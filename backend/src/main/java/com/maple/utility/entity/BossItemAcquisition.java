package com.maple.utility.entity;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "boss_item_acquisitions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BossItemAcquisition extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "character_id", nullable = false)
	private MapleCharacter character;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "boss_drop_item_id", nullable = false)
	private BossDropItem bossDropItem;

	@Column(name = "acquired_date", nullable = false)
	private LocalDate acquiredDate;

	@Column(name = "period_start", nullable = false)
	private LocalDate periodStart;

	@Column(name = "period_end", nullable = false)
	private LocalDate periodEnd;

	@Column(name = "memo", length = 255)
	private String memo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "boss_period_entry_id")
	private BossPeriodEntry periodEntry;

	@Column(name = "quantity", nullable = false)
	private int quantity = 1;

	@Column(name = "meso_amount")
	private Long mesoAmount;

	@Column(name = "detail_item", length = 100)
	private String detailItem;

	public static BossItemAcquisition create(
			MapleCharacter character,
			BossDropItem bossDropItem,
			LocalDate acquiredDate,
			String memo
	) {
		BossItemAcquisition acquisition = new BossItemAcquisition();
		acquisition.character = character;
		acquisition.bossDropItem = bossDropItem;
		acquisition.acquiredDate = acquiredDate;
		acquisition.assignPeriod(acquiredDate, bossDropItem.getBoss().getResetPeriod());
		acquisition.memo = memo;
		return acquisition;
	}

	public static BossItemAcquisition createForPeriod(
			BossPeriodEntry periodEntry, BossDropItem dropItem, int quantity, Long mesoAmount, String detailItem
	) {
		BossItemAcquisition acquisition = create(periodEntry.getCharacter(), dropItem, periodEntry.getPeriodStart(), null);
		acquisition.periodEntry = periodEntry;
		acquisition.assignPeriod(periodEntry.getPeriodStart(), periodEntry.getResetPeriod());
		acquisition.quantity = quantity;
		acquisition.mesoAmount = mesoAmount;
		acquisition.detailItem = detailItem;
		return acquisition;
	}

	private void assignPeriod(LocalDate date, ResetPeriod resetPeriod) {
		if (resetPeriod == ResetPeriod.MONTHLY) {
			this.periodStart = date.withDayOfMonth(1);
			this.periodEnd = date.with(TemporalAdjusters.lastDayOfMonth());
		} else {
			this.periodStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.THURSDAY));
			this.periodEnd = this.periodStart.plusDays(6);
		}
	}

	public void updateForPeriod(int quantity, Long mesoAmount, String detailItem) {
		this.quantity = quantity;
		this.mesoAmount = mesoAmount;
		this.detailItem = detailItem;
	}
}
