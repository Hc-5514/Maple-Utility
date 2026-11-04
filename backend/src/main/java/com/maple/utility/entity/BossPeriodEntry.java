package com.maple.utility.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "boss_period_entries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BossPeriodEntry extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "character_id", nullable = false)
	private MapleCharacter character;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "boss_id", nullable = false)
	private BossMaster boss;

	@Enumerated(EnumType.STRING)
	@Column(name = "reset_period", nullable = false)
	private ResetPeriod resetPeriod;

	@Column(name = "period_start", nullable = false)
	private LocalDate periodStart;

	@Column(name = "party_size", nullable = false)
	private int partySize;

	@Column(name = "crystal_price")
	private Long crystalPrice;

	@Column(name = "saved_at", nullable = false)
	private LocalDateTime savedAt;

	public static BossPeriodEntry create(MapleCharacter character, BossMaster boss, LocalDate periodStart, int partySize) {
		BossPeriodEntry entry = new BossPeriodEntry();
		entry.character = character;
		entry.boss = boss;
		entry.resetPeriod = boss.getResetPeriod();
		entry.periodStart = periodStart;
		entry.partySize = partySize;
		entry.savedAt = LocalDateTime.now();
		return entry;
	}

	public void save(int partySize, Long crystalPrice) {
		this.partySize = partySize;
		this.crystalPrice = crystalPrice;
		this.savedAt = LocalDateTime.now();
	}

	/** 기록에 수정한 결정 가격이 있으면 그 값, 없으면 보스 기본 가격 */
	public long effectiveCrystalPrice(long basePrice) {
		return crystalPrice != null ? crystalPrice : basePrice;
	}
}
