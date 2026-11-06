package com.maple.utility.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class BossItemAcquisitionPeriodTest {

	@Test
	void weeklyAcquisitionPreservesActualDateAndSpansYearBoundary() {
		BossDropItem item = item(ResetPeriod.WEEKLY);
		var acquisition = BossItemAcquisition.create(null, item, LocalDate.parse("2027-01-01"), "메모");

		assertThat(acquisition.getAcquiredDate()).isEqualTo(LocalDate.parse("2027-01-01"));
		assertThat(acquisition.getPeriodStart()).isEqualTo(LocalDate.parse("2026-12-31"));
		assertThat(acquisition.getPeriodEnd()).isEqualTo(LocalDate.parse("2027-01-06"));
	}

	@Test
	void monthlyAcquisitionUsesLeapMonthEnd() {
		var acquisition = BossItemAcquisition.create(null, item(ResetPeriod.MONTHLY), LocalDate.parse("2028-02-29"), null);

		assertThat(acquisition.getPeriodStart()).isEqualTo(LocalDate.parse("2028-02-01"));
		assertThat(acquisition.getPeriodEnd()).isEqualTo(LocalDate.parse("2028-02-29"));
	}

	@Test
	void linkedPeriodUsesSavedResetPeriodWhenCatalogChanges() {
		BossDropItem item = item(ResetPeriod.MONTHLY);
		BossPeriodEntry entry = BossPeriodEntry.create(null, item.getBoss(), LocalDate.parse("2026-11-01"), 1);
		ReflectionTestUtils.setField(item.getBoss(), "resetPeriod", ResetPeriod.WEEKLY);
		var acquisition = BossItemAcquisition.createForPeriod(entry, item, 1, null, "상세");

		assertThat(acquisition.getPeriodStart()).isEqualTo(LocalDate.parse("2026-11-01"));
		assertThat(acquisition.getPeriodEnd()).isEqualTo(LocalDate.parse("2026-11-30"));
		acquisition.updateForPeriod(2, null, "수정 상세");
		assertThat(acquisition.getPeriodEnd()).isEqualTo(LocalDate.parse("2026-11-30"));
		assertThat(acquisition.getQuantity()).isEqualTo(2);
	}

	private BossDropItem item(ResetPeriod resetPeriod) {
		BossMaster boss = BeanUtils.instantiateClass(BossMaster.class);
		ReflectionTestUtils.setField(boss, "resetPeriod", resetPeriod);
		BossDropItem item = BeanUtils.instantiateClass(BossDropItem.class);
		ReflectionTestUtils.setField(item, "boss", boss);
		return item;
	}
}
