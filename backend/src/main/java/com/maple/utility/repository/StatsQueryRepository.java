package com.maple.utility.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.maple.utility.dto.response.StatsBossItemResponse;
import com.maple.utility.dto.response.StatsCompletionDetailResponse;
import com.maple.utility.dto.response.StatsHuntingDailyResponse;
import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.QBossItemAcquisition;
import com.maple.utility.entity.QHuntingRecord;
import com.maple.utility.entity.QSchedulerBossRecord;
import com.maple.utility.entity.QSchedulerDailyRecord;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

@Repository
public class StatsQueryRepository {

	private final JPAQueryFactory queryFactory;

	public StatsQueryRepository(JPAQueryFactory queryFactory) {
		this.queryFactory = queryFactory;
	}

	public List<StatsHuntingDailyResponse> findHuntingDailyStats(
			List<Long> characterIds,
			LocalDate dateFrom,
			LocalDate dateTo
	) {
		if (characterIds.isEmpty()) {
			return List.of();
		}

		QHuntingRecord record = QHuntingRecord.huntingRecord;
		NumberExpression<Long> mesoSum = record.mesoEarned.sum();
		NumberExpression<Integer> solErdaSum = record.solErdaEarned.sum();
		NumberExpression<Integer> playDurationSum = record.playDurationMin.sum();

		return queryFactory
				.select(record.recordDate, mesoSum, solErdaSum, playDurationSum)
				.from(record)
				.where(huntingCondition(record, characterIds, dateFrom, dateTo))
				.groupBy(record.recordDate)
				.orderBy(record.recordDate.asc())
				.fetch()
				.stream()
				.map(tuple -> new StatsHuntingDailyResponse(
						tuple.get(record.recordDate),
						valueOrZero(tuple.get(mesoSum)),
						valueOrZero(tuple.get(solErdaSum)),
						tuple.get(playDurationSum)
				))
				.toList();
	}

	public List<CrystalIncomeRow> findCompletedBossCrystalRows(
			List<Long> characterIds,
			LocalDate dateFrom,
			LocalDate dateTo
	) {
		if (characterIds.isEmpty()) {
			return List.of();
		}

		QSchedulerBossRecord record = QSchedulerBossRecord.schedulerBossRecord;
		return queryFactory
				.select(record.recordDate, record.boss.bossName, record.boss.difficulty, record.boss.crystalPrice)
				.from(record)
				.where(bossCondition(record, characterIds, dateFrom, dateTo)
						.and(record.completed.isTrue()))
				.orderBy(record.recordDate.asc(), record.boss.sortOrder.asc(), record.id.asc())
				.fetch()
				.stream()
				.map(tuple -> new CrystalIncomeRow(
						tuple.get(record.recordDate),
						tuple.get(record.boss.bossName),
						tuple.get(record.boss.difficulty),
						valueOrZero(tuple.get(record.boss.crystalPrice))
				))
				.toList();
	}

	public List<StatsBossItemResponse> findBossItemStats(
			List<Long> characterIds,
			LocalDate dateFrom,
			LocalDate dateTo
	) {
		if (characterIds.isEmpty()) {
			return List.of();
		}

		QBossItemAcquisition acquisition = QBossItemAcquisition.bossItemAcquisition;
		return queryFactory
				.select(
						acquisition.acquiredDate,
						acquisition.character.characterName,
						acquisition.bossDropItem.boss.bossName,
						acquisition.bossDropItem.boss.difficulty,
						acquisition.bossDropItem.itemName
				)
				.from(acquisition)
				.where(bossItemCondition(acquisition, characterIds, dateFrom, dateTo))
				.orderBy(acquisition.acquiredDate.desc(), acquisition.id.desc())
				.fetch()
				.stream()
				.map(tuple -> new StatsBossItemResponse(
						tuple.get(acquisition.acquiredDate),
						tuple.get(acquisition.character.characterName),
						tuple.get(acquisition.bossDropItem.boss.bossName),
						tuple.get(acquisition.bossDropItem.boss.difficulty),
						tuple.get(acquisition.bossDropItem.itemName)
				))
				.toList();
	}

	public StatsCompletionDetailResponse findDailyCompletion(
			List<Long> characterIds,
			LocalDate dateFrom,
			LocalDate dateTo
	) {
		if (characterIds.isEmpty()) {
			return new StatsCompletionDetailResponse(0, 0, 0);
		}

		QSchedulerDailyRecord record = QSchedulerDailyRecord.schedulerDailyRecord;
		NumberExpression<Integer> completedSum = record.completedCount.sum();
		NumberExpression<Integer> totalSum = record.totalCount.sum();
		Tuple tuple = queryFactory
				.select(completedSum, totalSum)
				.from(record)
				.where(dailyCondition(record, characterIds, dateFrom, dateTo))
				.fetchOne();

		int completed = tuple == null ? 0 : valueOrZero(tuple.get(completedSum));
		int total = tuple == null ? 0 : valueOrZero(tuple.get(totalSum));
		return new StatsCompletionDetailResponse(completed, total, completionRate(completed, total));
	}

	public StatsCompletionDetailResponse findBossCompletion(
			List<Long> characterIds,
			LocalDate dateFrom,
			LocalDate dateTo
	) {
		if (characterIds.isEmpty()) {
			return new StatsCompletionDetailResponse(0, 0, 0);
		}

		QSchedulerBossRecord record = QSchedulerBossRecord.schedulerBossRecord;
		NumberExpression<Integer> completedSum = new CaseBuilder()
				.when(record.completed.isTrue())
				.then(1)
				.otherwise(0)
				.sum();
		NumberExpression<Long> totalCount = record.id.count();
		Tuple tuple = queryFactory
				.select(completedSum, totalCount)
				.from(record)
				.where(bossCondition(record, characterIds, dateFrom, dateTo))
				.fetchOne();

		int completed = tuple == null ? 0 : valueOrZero(tuple.get(completedSum));
		int total = tuple == null ? 0 : Math.toIntExact(valueOrZero(tuple.get(totalCount)));
		return new StatsCompletionDetailResponse(completed, total, completionRate(completed, total));
	}

	private BooleanBuilder huntingCondition(
			QHuntingRecord record,
			List<Long> characterIds,
			LocalDate dateFrom,
			LocalDate dateTo
	) {
		BooleanBuilder builder = new BooleanBuilder(record.character.id.in(characterIds));
		if (dateFrom != null) {
			builder.and(record.recordDate.goe(dateFrom));
		}
		if (dateTo != null) {
			builder.and(record.recordDate.loe(dateTo));
		}
		return builder;
	}

	private BooleanBuilder bossCondition(
			QSchedulerBossRecord record,
			List<Long> characterIds,
			LocalDate dateFrom,
			LocalDate dateTo
	) {
		BooleanBuilder builder = new BooleanBuilder(record.character.id.in(characterIds));
		if (dateFrom != null) {
			builder.and(record.recordDate.goe(dateFrom));
		}
		if (dateTo != null) {
			builder.and(record.recordDate.loe(dateTo));
		}
		return builder;
	}

	private BooleanBuilder dailyCondition(
			QSchedulerDailyRecord record,
			List<Long> characterIds,
			LocalDate dateFrom,
			LocalDate dateTo
	) {
		BooleanBuilder builder = new BooleanBuilder(record.character.id.in(characterIds));
		if (dateFrom != null) {
			builder.and(record.recordDate.goe(dateFrom));
		}
		if (dateTo != null) {
			builder.and(record.recordDate.loe(dateTo));
		}
		return builder;
	}

	private BooleanBuilder bossItemCondition(
			QBossItemAcquisition acquisition,
			List<Long> characterIds,
			LocalDate dateFrom,
			LocalDate dateTo
	) {
		BooleanBuilder builder = new BooleanBuilder(acquisition.character.id.in(characterIds));
		if (dateFrom != null) {
			builder.and(acquisition.acquiredDate.goe(dateFrom));
		}
		if (dateTo != null) {
			builder.and(acquisition.acquiredDate.loe(dateTo));
		}
		return builder;
	}

	private int completionRate(int completed, int total) {
		if (total == 0) {
			return 0;
		}
		return Math.toIntExact(Math.round((double) completed * 100 / total));
	}

	private long valueOrZero(Long value) {
		return value == null ? 0 : value;
	}

	private int valueOrZero(Integer value) {
		return value == null ? 0 : value;
	}

	public record CrystalIncomeRow(
			LocalDate recordDate,
			String bossName,
			Difficulty difficulty,
			long income
	) {
	}
}
