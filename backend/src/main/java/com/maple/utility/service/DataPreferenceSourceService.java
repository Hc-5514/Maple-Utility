package com.maple.utility.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** User intent is independent of data ownership and automatic Nexon synchronization. */
@Service
public class DataPreferenceSourceService {
	public static final String FAVORITE = "FAVORITE";
	public static final String SORT_ORDER = "SORT_ORDER";
	public static final String WORLD_SELECTION = "WORLD_SELECTION";
	private final JdbcTemplate jdbc;

	public DataPreferenceSourceService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void saveExplicit(String type, long recordId, long identityUserId) {
		jdbc.update("""
				INSERT INTO data_preference_sources (setting_type, record_id, source_user_id, state, saved_at)
				VALUES (?, ?, ?, 'EXPLICIT', CURRENT_TIMESTAMP)
				ON CONFLICT (setting_type, record_id) DO UPDATE
				SET source_user_id = EXCLUDED.source_user_id, state = 'EXPLICIT', saved_at = EXCLUDED.saved_at
				""", type, recordId, identityUserId);
	}

	public PreferenceSource read(String type, long recordId, long owner, boolean legacyValue) {
		var rows = jdbc.query("""
				SELECT source_user_id, state, saved_at FROM data_preference_sources
				WHERE setting_type = ? AND record_id = ?
				""", (rs, n) -> new PreferenceSource(rs.getLong("source_user_id"), rs.getString("state"),
				rs.getTimestamp("saved_at") == null ? null : rs.getTimestamp("saved_at").toLocalDateTime()), type, recordId);
		if (!rows.isEmpty()) {
			return rows.getFirst();
		}
		// No legacy dataset-wide sort provenance exists; keep its owner as an unknown source.
		if (SORT_ORDER.equals(type)) {
			return new PreferenceSource(owner, "UNKNOWN", null);
		}
		// Preserve provenance after an earlier merge; never infer intent from updated_at.
		String table = FAVORITE.equals(type) ? "characters" : "user_selected_world_sets";
		var origins = jdbc.queryForList("""
				SELECT source_user_id FROM data_merge_provenance WHERE table_name = ? AND record_id = ?
				""", Long.class, table, recordId);
		return new PreferenceSource(origins.isEmpty() ? owner : origins.getFirst(),
				legacyValue ? "LEGACY" : "UNKNOWN", null);
	}

	public void put(String type, long recordId, PreferenceSource source) {
		jdbc.update("""
				INSERT INTO data_preference_sources (setting_type, record_id, source_user_id, state, saved_at)
				VALUES (?, ?, ?, ?, ?)
				ON CONFLICT (setting_type, record_id) DO UPDATE SET
				source_user_id = EXCLUDED.source_user_id, state = EXCLUDED.state, saved_at = EXCLUDED.saved_at
				""", type, recordId, source.userId(), source.state(), source.savedAt() == null ? null : Timestamp.valueOf(source.savedAt()));
	}

	public void remove(String type, long recordId) {
		jdbc.update("DELETE FROM data_preference_sources WHERE setting_type = ? AND record_id = ?", type, recordId);
	}

	public boolean prefer(PreferenceSource candidate, PreferenceSource existing) {
		int stateComparison = Integer.compare(rank(candidate.state()), rank(existing.state()));
		if (stateComparison != 0) {
			return stateComparison > 0;
		}
		Map<Long, Origin> cache = new HashMap<>();
		return Comparator.comparingInt(Origin::providerPriority).thenComparing(Origin::createdAt)
				.thenComparingLong(Origin::userId)
				.compare(origin(candidate.userId(), cache), origin(existing.userId(), cache)) < 0;
	}

	private Origin origin(long userId, Map<Long, Origin> cache) {
		return cache.computeIfAbsent(userId, id -> jdbc.queryForObject(
				"SELECT id, oauth_provider, created_at FROM users WHERE id = ?",
				(rs, n) -> new Origin(rs.getLong("id"),
						"NEXON_APIKEY".equals(rs.getString("oauth_provider")) ? 0 : 1,
						rs.getTimestamp("created_at").toLocalDateTime()), id));
	}

	private int rank(String state) {
		return switch (state) {
			case "EXPLICIT" -> 2;
			case "LEGACY" -> 1;
			default -> 0;
		};
	}

	public record PreferenceSource(long userId, String state, LocalDateTime savedAt) { }
	private record Origin(long userId, int providerPriority, LocalDateTime createdAt) { }
}
