package com.maple.utility.service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.maple.utility.exception.ApiException;

/** Moves verified legacy data without changing the identity of the surviving owner. */
@Service
public class DataContextMergeService {

	private static final List<String> CHARACTER_TABLES = List.of(
			"scheduler_daily_records", "scheduler_weekly_records", "scheduler_boss_records",
			"boss_period_entries", "boss_period_selections", "boss_item_acquisitions",
			"hunting_records", "scheduler_week_finalizations");
	private static final String WORLD_SET = "user_selected_world_sets";
	private final JdbcTemplate jdbc;
	private final SchedulerSyncLock syncLock;

	public DataContextMergeService(JdbcTemplate jdbc, SchedulerSyncLock syncLock) {
		this.jdbc = jdbc;
		this.syncLock = syncLock;
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
		mergeWorldSelection(targetOwnerUserId, sourceOwnerUserId, origins);
		List<Map<String, Object>> sourceCharacters = jdbc.queryForList(
				"SELECT * FROM characters WHERE user_id = ? ORDER BY sort_order, id FOR UPDATE",
				sourceOwnerUserId);
		int nextSortOrder = jdbc.queryForObject(
				"SELECT COALESCE(MAX(sort_order), -1) + 1 FROM characters WHERE user_id = ?",
				Integer.class, targetOwnerUserId);
		for (Map<String, Object> source : sourceCharacters) {
			long sourceId = id(source);
			List<Map<String, Object>> matches = jdbc.queryForList(
					"SELECT * FROM characters WHERE user_id = ? AND ocid = ? FOR UPDATE",
					targetOwnerUserId, source.get("ocid"));
			if (matches.isEmpty()) {
				jdbc.update("UPDATE characters SET user_id = ?, sort_order = ? WHERE id = ?",
						targetOwnerUserId, nextSortOrder++, sourceId);
				continue;
			}
			long targetId = id(matches.getFirst());
			mergeCharacterChildren(targetId, sourceId, origins);
			if (preferred("characters", sourceId, targetId, origins)) {
				copyCharacterSettings(targetId, source);
				setOrigin("characters", targetId, originUser("characters", sourceId));
			}
			deleteRecord("characters", sourceId);
		}
		jdbc.update("UPDATE scheduler_week_finalizations SET user_id = ? WHERE user_id = ?",
				targetOwnerUserId, sourceOwnerUserId);
		jdbc.update("""
				INSERT INTO user_hunting_ground_favorites (user_id, hunting_ground_id, created_at)
				SELECT ?, hunting_ground_id, created_at FROM user_hunting_ground_favorites WHERE user_id = ?
				ON CONFLICT (user_id, hunting_ground_id) DO NOTHING
				""", targetOwnerUserId, sourceOwnerUserId);
		jdbc.update("DELETE FROM user_hunting_ground_favorites WHERE user_id = ?", sourceOwnerUserId);
		jdbc.update("UPDATE data_sync_logs SET user_id = ? WHERE user_id = ?", targetOwnerUserId, sourceOwnerUserId);
		jdbc.update("UPDATE sync_jobs SET user_id = ? WHERE user_id = ?", targetOwnerUserId, sourceOwnerUserId);
	}

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

	private void mergeWorldSelection(long targetOwner, long sourceOwner, Map<Long, Origin> origins) {
		boolean sourcePreferred = preferred(WORLD_SET, sourceOwner, targetOwner, origins);
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

	private void copyCharacterSettings(long targetId, Map<String, Object> source) {
		jdbc.update("""
				UPDATE characters SET character_name = ?, world_name = ?, character_class = ?,
				character_level = ?, character_image = ?, guild_name = ?, is_favorite = ?, sort_order = ?
				WHERE id = ?
				""", source.get("character_name"), source.get("world_name"), source.get("character_class"),
				source.get("character_level"), source.get("character_image"), source.get("guild_name"),
				source.get("is_favorite"), source.get("sort_order"), targetId);
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
