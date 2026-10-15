package com.maple.utility.service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.maple.utility.exception.ApiException;
import com.maple.utility.service.DataPreferenceSourceService.PreferenceSource;

/** Moves verified legacy data without changing the identity of the surviving owner. */
@Service
public class DataContextMergeService {

	private static final List<String> CHARACTER_TABLES = List.of(
			"scheduler_daily_records", "scheduler_weekly_records", "scheduler_boss_records",
			"boss_period_entries", "boss_period_selections", "boss_item_acquisitions",
			"hunting_records", "scheduler_week_finalizations", "scheduler_monthly_finalizations");
	private static final String WORLD_SET = "user_selected_world_sets";
	private final JdbcTemplate jdbc;
	private final SchedulerSyncLock syncLock;
	private final DataPreferenceSourceService preferences;

	public DataContextMergeService(JdbcTemplate jdbc, SchedulerSyncLock syncLock) {
		this.jdbc = jdbc;
		this.syncLock = syncLock;
		this.preferences = new DataPreferenceSourceService(jdbc);
	}

	/** Caller must hold the account linkage lock and flush pending JPA writes first. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void merge(long targetOwnerUserId, long sourceOwnerUserId) {
		if (targetOwnerUserId == sourceOwnerUserId) {
			return;
		}
		long first = Math.min(targetOwnerUserId, sourceOwnerUserId);
		long second = Math.max(targetOwnerUserId, sourceOwnerUserId);
		if (!syncLock.tryAcquire(first) || !syncLock.tryAcquire(second)) {
			throw syncInProgress();
		}
		List<Long> lockedUsers = jdbc.queryForList(
				"SELECT id FROM users WHERE id IN (?, ?) ORDER BY id FOR UPDATE",
				Long.class, first, second);
		if (lockedUsers.size() != 2) {
			throw new ApiException(HttpStatus.NOT_FOUND, "DATA_CONTEXT_NOT_FOUND", "데이터 소유자 없음");
		}
		assertNoActiveSynchronization(first, second);
		seedProvenance(targetOwnerUserId, sourceOwnerUserId);
		Map<Long, Origin> origins = new HashMap<>();
		List<Map<String, Object>> targetCharacters = characters(targetOwnerUserId);
		List<Map<String, Object>> sourceCharacters = characters(sourceOwnerUserId);
		Map<String, Map<String, Object>> targetByOcid = byOcid(targetCharacters);
		Map<String, FavoriteDecision> favorites = new LinkedHashMap<>();
		for (Map<String, Object> target : targetCharacters) {
			favorites.put((String) target.get("ocid"), favorite(target, targetOwnerUserId));
		}
		for (Map<String, Object> source : sourceCharacters) {
			String ocid = (String) source.get("ocid");
			FavoriteDecision candidate = favorite(source, sourceOwnerUserId);
			FavoriteDecision existing = favorites.get(ocid);
			if (existing == null || preferFavorite(candidate, existing)) {
				favorites.put(ocid, candidate);
			}
		}
		if (favorites.values().stream().filter(FavoriteDecision::value).count() > 18) {
			throw new ApiException(HttpStatus.CONFLICT, "DATA_CONTEXT_FAVORITE_LIMIT_EXCEEDED",
					"병합 후 즐겨찾기 18명 초과, 기존 계정 설정 정리 필요");
		}
		PreferenceSource targetOrder = preferences.read(DataPreferenceSourceService.SORT_ORDER,
				targetOwnerUserId, targetOwnerUserId, false);
		PreferenceSource sourceOrder = preferences.read(DataPreferenceSourceService.SORT_ORDER,
				sourceOwnerUserId, sourceOwnerUserId, false);
		boolean sourceOrderPreferred = preferences.prefer(sourceOrder, targetOrder);
		LinkedHashSet<String> orderedOcids = new LinkedHashSet<>();
		for (Map<String, Object> row : sourceOrderPreferred ? sourceCharacters : targetCharacters) {
			orderedOcids.add((String) row.get("ocid"));
		}
		for (Map<String, Object> row : sourceOrderPreferred ? targetCharacters : sourceCharacters) {
			orderedOcids.add((String) row.get("ocid"));
		}
		seedPreferenceSources(targetOwnerUserId, targetCharacters);
		seedPreferenceSources(sourceOwnerUserId, sourceCharacters);
		String beforeState = auditState(targetOwnerUserId, sourceOwnerUserId);
		mergeWorldSelection(targetOwnerUserId, sourceOwnerUserId);
		for (Map<String, Object> source : sourceCharacters) {
			long sourceId = id(source);
			Map<String, Object> target = targetByOcid.get((String) source.get("ocid"));
			if (target == null) {
				jdbc.update("UPDATE characters SET user_id = ? WHERE id = ?", targetOwnerUserId, sourceId);
				continue;
			}
			long targetId = id(target);
			mergeCharacterChildren(targetId, sourceId, origins);
			// Profile provenance is independent of explicit user preference provenance.
			if (preferred("characters", sourceId, targetId, origins)) {
				copyCharacterProfile(targetId, source);
				setOrigin("characters", targetId, originUser("characters", sourceId));
			}
			preferences.remove(DataPreferenceSourceService.FAVORITE, sourceId);
			deleteRecord("characters", sourceId);
		}
		int sortOrder = 1;
		Map<String, Map<String, Object>> merged = byOcid(characters(targetOwnerUserId));
		for (String ocid : orderedOcids) {
			long characterId = id(merged.get(ocid));
			FavoriteDecision decision = favorites.get(ocid);
			jdbc.update("UPDATE characters SET is_favorite = ?, sort_order = ? WHERE id = ?",
					decision.value(), sortOrder++, characterId);
			preferences.put(DataPreferenceSourceService.FAVORITE, characterId, decision.source());
		}
		preferences.put(DataPreferenceSourceService.SORT_ORDER, targetOwnerUserId,
				sourceOrderPreferred ? sourceOrder : targetOrder);
		preferences.remove(DataPreferenceSourceService.SORT_ORDER, sourceOwnerUserId);
		for (String table : List.of("scheduler_week_finalizations", "scheduler_monthly_finalizations")) {
			jdbc.update("UPDATE " + table + " SET user_id = ? WHERE user_id = ?", targetOwnerUserId, sourceOwnerUserId);
		}
		jdbc.update("""
				INSERT INTO user_hunting_ground_favorites (user_id, hunting_ground_id, created_at)
				SELECT ?, hunting_ground_id, created_at FROM user_hunting_ground_favorites WHERE user_id = ?
				ON CONFLICT (user_id, hunting_ground_id) DO NOTHING
				""", targetOwnerUserId, sourceOwnerUserId);
		jdbc.update("DELETE FROM user_hunting_ground_favorites WHERE user_id = ?", sourceOwnerUserId);
		jdbc.update("UPDATE data_sync_logs SET user_id = ? WHERE user_id = ?", targetOwnerUserId, sourceOwnerUserId);
		jdbc.update("UPDATE sync_jobs SET user_id = ? WHERE user_id = ?", targetOwnerUserId, sourceOwnerUserId);
		jdbc.update("""
				INSERT INTO data_context_merge_audits (target_user_id, source_user_id, before_state, after_state)
				VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
				""", targetOwnerUserId, sourceOwnerUserId, beforeState, auditState(targetOwnerUserId, sourceOwnerUserId));
	}

	private List<Map<String, Object>> characters(long owner) {
		return jdbc.queryForList("SELECT * FROM characters WHERE user_id = ? ORDER BY sort_order, id FOR UPDATE", owner);
	}

	private Map<String, Map<String, Object>> byOcid(List<Map<String, Object>> rows) {
		return rows.stream().collect(Collectors.toMap(row -> (String) row.get("ocid"), row -> row));
	}

	private FavoriteDecision favorite(Map<String, Object> row, long owner) {
		boolean value = Boolean.TRUE.equals(row.get("is_favorite"));
		return new FavoriteDecision(value, preferences.read(DataPreferenceSourceService.FAVORITE, id(row), owner, value));
	}

	private void seedPreferenceSources(long owner, List<Map<String, Object>> characters) {
		for (Map<String, Object> row : characters) {
			FavoriteDecision decision = favorite(row, owner);
			preferences.put(DataPreferenceSourceService.FAVORITE, id(row), decision.source());
		}
		for (String type : List.of(DataPreferenceSourceService.SORT_ORDER, DataPreferenceSourceService.WORLD_SELECTION)) {
			preferences.put(type, owner, preferences.read(type, owner, owner,
					type.equals(DataPreferenceSourceService.WORLD_SELECTION) && hasWorlds(owner)));
		}
	}

	private boolean preferFavorite(FavoriteDecision candidate, FavoriteDecision existing) {
		if (candidate.source().state().equals("UNKNOWN") && existing.source().state().equals("UNKNOWN")
				&& candidate.value() != existing.value()) {
			return candidate.value();
		}
		return preferences.prefer(candidate.source(), existing.source());
	}

	private boolean hasWorlds(long owner) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM user_selected_worlds WHERE user_id = ?)", Boolean.class, owner));
	}

	/** Allowlisted record tables exclude credentials, tokens and external response errors. */
	private String auditState(long target, long source) {
		List<String> parts = new ArrayList<>();
		parts.add(auditPart("characters", "SELECT * FROM characters WHERE user_id IN (?, ?)"));
		parts.add(auditPart("user_selected_worlds", "SELECT * FROM user_selected_worlds WHERE user_id IN (?, ?)"));
		parts.add(auditPart("user_hunting_ground_favorites",
				"SELECT * FROM user_hunting_ground_favorites WHERE user_id IN (?, ?)"));
		for (String table : CHARACTER_TABLES) {
			parts.add(auditPart(table, "SELECT to_jsonb(r) - 'last_error' AS data FROM " + table
					+ " r JOIN characters c ON c.id = r.character_id WHERE c.user_id IN (?, ?)", true));
		}
		parts.add(auditPart("scheduler_monthly_boss_snapshots", """
				SELECT s.* FROM scheduler_monthly_boss_snapshots s
				JOIN scheduler_monthly_finalizations f ON f.id = s.finalization_id
				JOIN characters c ON c.id = f.character_id WHERE c.user_id IN (?, ?)
				"""));
		parts.add(auditPart("data_preference_sources", """
				SELECT * FROM data_preference_sources WHERE
				(setting_type = 'FAVORITE' AND record_id IN (SELECT id FROM characters WHERE user_id IN (?, ?)))
				OR (setting_type IN ('SORT_ORDER', 'WORLD_SELECTION') AND record_id IN (?, ?))
				"""));
		List<String> provenancePredicates = new ArrayList<>();
		provenancePredicates.add("(table_name = 'characters' AND record_id IN (SELECT id FROM characters WHERE user_id IN (?, ?)))");
		provenancePredicates.add("(table_name = 'user_selected_worlds' AND record_id IN (SELECT id FROM user_selected_worlds WHERE user_id IN (?, ?)))");
		provenancePredicates.add("(table_name = 'user_selected_world_sets' AND record_id IN (?, ?))");
		for (String table : CHARACTER_TABLES) {
			provenancePredicates.add("(table_name = '" + table + "' AND record_id IN (SELECT r.id FROM " + table
					+ " r JOIN characters c ON c.id = r.character_id WHERE c.user_id IN (?, ?)))");
		}
		parts.add(auditPart("data_merge_provenance", "SELECT * FROM data_merge_provenance WHERE "
				+ String.join(" OR ", provenancePredicates)));
		parts.add(auditPart("data_sync_logs", """
				SELECT id, user_id, sync_type, status, api_calls_used, started_at, completed_at, created_at, updated_at
				FROM data_sync_logs WHERE user_id IN (?, ?)
				"""));
		parts.add(auditPart("sync_jobs", """
				SELECT id, user_id, job_type, status, total_count, completed_count, skipped_count, started_at, completed_at
				FROM sync_jobs WHERE user_id IN (?, ?)
				"""));
		String sql = "SELECT jsonb_build_object(" + String.join(",", parts) + ")::text";
		List<Object> args = new ArrayList<>();
		for (int i = 0; i < sql.length(); i++) {
			if (sql.charAt(i) == '?') {
				args.add(args.size() % 2 == 0 ? target : source);
			}
		}
		return jdbc.queryForObject(sql, String.class, args.toArray());
	}

	private String auditPart(String name, String select) {
		return auditPart(name, select, false);
	}

	private String auditPart(String name, String select, boolean jsonData) {
		String aggregate = "COALESCE(jsonb_agg(" + (jsonData ? "q.data" : "to_jsonb(q)") + "), '[]'::jsonb)";
		return "'" + name + "', (SELECT jsonb_build_object('count', count(*), 'rows', "
				+ aggregate + ") FROM (" + select + ") q)";
	}

	private record FavoriteDecision(boolean value, PreferenceSource source) { }

	private void assertNoActiveSynchronization(long first, long second) {
		Boolean active = jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM sync_jobs WHERE user_id IN (?, ?) AND status = 'STARTED')
				    OR EXISTS (SELECT 1 FROM data_sync_logs WHERE user_id IN (?, ?) AND status = 'STARTED')
				""", Boolean.class, first, second, first, second);
		if (Boolean.TRUE.equals(active)) {
			throw syncInProgress();
		}
	}

	private ApiException syncInProgress() {
		return new ApiException(HttpStatus.CONFLICT, "DATA_CONTEXT_SYNC_IN_PROGRESS", "동기화 완료 후 계정 연결 재시도 필요");
	}

	private void seedProvenance(long targetOwner, long sourceOwner) {
		jdbc.update("""
				INSERT INTO data_merge_provenance (table_name, record_id, source_user_id)
				SELECT 'characters', id, user_id FROM characters WHERE user_id IN (?, ?)
				ON CONFLICT (table_name, record_id) DO NOTHING
				""", targetOwner, sourceOwner);
		for (String table : CHARACTER_TABLES) {
			jdbc.update("INSERT INTO data_merge_provenance (table_name, record_id, source_user_id) "
					+ "SELECT ?, r.id, c.user_id FROM " + table + " r JOIN characters c ON c.id = r.character_id "
					+ "WHERE c.user_id IN (?, ?) ON CONFLICT (table_name, record_id) DO NOTHING",
					table, targetOwner, sourceOwner);
		}
		jdbc.update("""
				INSERT INTO data_merge_provenance (table_name, record_id, source_user_id)
				SELECT 'user_selected_worlds', id, user_id FROM user_selected_worlds WHERE user_id IN (?, ?)
				ON CONFLICT (table_name, record_id) DO NOTHING
				""", targetOwner, sourceOwner);
		seedWorldSetOrigin(targetOwner);
		seedWorldSetOrigin(sourceOwner);
	}

	private void seedWorldSetOrigin(long owner) {
		jdbc.update("""
				INSERT INTO data_merge_provenance (table_name, record_id, source_user_id)
				VALUES (?, ?, ?) ON CONFLICT (table_name, record_id) DO NOTHING
				""", WORLD_SET, owner, owner);
	}

	private void mergeWorldSelection(long targetOwner, long sourceOwner) {
		PreferenceSource target = preferences.read(DataPreferenceSourceService.WORLD_SELECTION,
				targetOwner, targetOwner, hasWorlds(targetOwner));
		PreferenceSource source = preferences.read(DataPreferenceSourceService.WORLD_SELECTION,
				sourceOwner, sourceOwner, hasWorlds(sourceOwner));
		boolean sourcePreferred = preferences.prefer(source, target);
		long losingOwner = sourcePreferred ? targetOwner : sourceOwner;
		jdbc.update("""
				DELETE FROM data_merge_provenance WHERE table_name = 'user_selected_worlds'
				AND record_id IN (SELECT id FROM user_selected_worlds WHERE user_id = ?)
				""", losingOwner);
		jdbc.update("DELETE FROM user_selected_worlds WHERE user_id = ?", losingOwner);
		if (sourcePreferred) {
			jdbc.update("UPDATE user_selected_worlds SET user_id = ? WHERE user_id = ?", targetOwner, sourceOwner);
			setOrigin(WORLD_SET, targetOwner, originUser(WORLD_SET, sourceOwner));
		}
		preferences.put(DataPreferenceSourceService.WORLD_SELECTION, targetOwner, sourcePreferred ? source : target);
		preferences.remove(DataPreferenceSourceService.WORLD_SELECTION, sourceOwner);
		jdbc.update("DELETE FROM data_merge_provenance WHERE table_name = ? AND record_id = ?", WORLD_SET, sourceOwner);
	}

	private void mergeCharacterChildren(long targetId, long sourceId, Map<Long, Origin> origins) {
		mergeRecords("scheduler_daily_records", targetId, sourceId, List.of("record_date", "content_name"), origins);
		mergeRecords("scheduler_weekly_records", targetId, sourceId, List.of("week_start_date", "content_name"), origins);
		mergeRecords("scheduler_boss_records", targetId, sourceId, List.of("boss_id", "record_date"), origins);
		mergeRecords("boss_period_entries", targetId, sourceId, List.of("boss_id", "period_start"), origins);
		mergeRecords("boss_period_selections", targetId, sourceId, List.of("boss_id", "period_start"), origins);
		// Acquisitions are events, not unique item/date settings. Only exact copies of the same event collapse.
		mergeRecords("boss_item_acquisitions", targetId, sourceId,
				List.of("boss_drop_item_id", "acquired_date", "memo", "boss_period_entry_id", "quantity", "meso_amount", "created_at"),
				origins);
		mergeRecords("hunting_records", targetId, sourceId, List.of("record_date"), origins);
		mergeRecords("scheduler_week_finalizations", targetId, sourceId, List.of("week_start_date"), origins);
		// Cascading the losing finalization removes only its own snapshot set.
		mergeRecords("scheduler_monthly_finalizations", targetId, sourceId, List.of("month_start_date"), origins);
	}

	private void mergeRecords(String table, long targetCharacter, long sourceCharacter,
			List<String> keyColumns, Map<Long, Origin> origins) {
		List<Map<String, Object>> sourceRecords = jdbc.queryForList(
				"SELECT * FROM " + table + " WHERE character_id = ? ORDER BY id FOR UPDATE", sourceCharacter);
		String predicate = keyColumns.stream().map(column -> column + " IS NOT DISTINCT FROM ?")
				.collect(Collectors.joining(" AND "));
		for (Map<String, Object> source : sourceRecords) {
			List<Object> arguments = new ArrayList<>();
			arguments.add(targetCharacter);
			keyColumns.forEach(column -> arguments.add(source.get(column)));
			List<Long> matches = jdbc.queryForList("SELECT id FROM " + table
					+ " WHERE character_id = ? AND " + predicate + " ORDER BY id FOR UPDATE", Long.class,
					arguments.toArray());
			long sourceId = id(source);
			if (!matches.isEmpty()) {
				long targetId = matches.getFirst();
				boolean sourcePreferred = preferred(table, sourceId, targetId, origins);
				long survivor = sourcePreferred ? sourceId : targetId;
				long removed = sourcePreferred ? targetId : sourceId;
				if (table.equals("boss_period_entries")) {
					jdbc.update("UPDATE boss_item_acquisitions SET boss_period_entry_id = ? WHERE boss_period_entry_id = ?",
							survivor, removed);
				}
				deleteRecord(table, removed);
				if (!sourcePreferred) {
					continue;
				}
			}
			jdbc.update("UPDATE " + table + " SET character_id = ? WHERE id = ?", targetCharacter, sourceId);
		}
	}

	private void copyCharacterProfile(long targetId, Map<String, Object> source) {
		jdbc.update("""
				UPDATE characters SET character_name = ?, world_name = ?, character_class = ?,
				character_level = ?, character_image = ?, guild_name = ?
				WHERE id = ?
				""", source.get("character_name"), source.get("world_name"), source.get("character_class"),
				source.get("character_level"), source.get("character_image"), source.get("guild_name"), targetId);
	}

	private boolean preferred(String table, long candidate, long existing, Map<Long, Origin> origins) {
		Origin candidateOrigin = origin(table, candidate, origins);
		Origin existingOrigin = origin(table, existing, origins);
		int comparison = Comparator.comparingInt(Origin::providerPriority)
				.thenComparing(Origin::createdAt).thenComparingLong(Origin::userId)
				.compare(candidateOrigin, existingOrigin);
		return comparison < 0 || comparison == 0 && candidate < existing;
	}

	private Origin origin(String table, long recordId, Map<Long, Origin> cache) {
		long sourceUser = originUser(table, recordId);
		return cache.computeIfAbsent(sourceUser, userId -> jdbc.queryForObject(
				"SELECT id, oauth_provider, created_at FROM users WHERE id = ?",
				(rs, rowNum) -> new Origin(rs.getLong("id"),
						"NEXON_APIKEY".equals(rs.getString("oauth_provider")) ? 0 : 1,
						rs.getTimestamp("created_at")), userId));
	}

	private long originUser(String table, long recordId) {
		return jdbc.queryForObject(
				"SELECT source_user_id FROM data_merge_provenance WHERE table_name = ? AND record_id = ?",
				Long.class, table, recordId);
	}

	private void setOrigin(String table, long recordId, long sourceUser) {
		jdbc.update("""
				INSERT INTO data_merge_provenance (table_name, record_id, source_user_id) VALUES (?, ?, ?)
				ON CONFLICT (table_name, record_id) DO UPDATE SET source_user_id = EXCLUDED.source_user_id
				""", table, recordId, sourceUser);
	}

	private void deleteRecord(String table, long recordId) {
		jdbc.update("DELETE FROM " + table + " WHERE id = ?", recordId);
		jdbc.update("DELETE FROM data_merge_provenance WHERE table_name = ? AND record_id = ?", table, recordId);
	}

	private static long id(Map<String, Object> row) {
		return ((Number) row.get("id")).longValue();
	}

	private record Origin(long userId, int providerPriority, Timestamp createdAt) {
	}
}
