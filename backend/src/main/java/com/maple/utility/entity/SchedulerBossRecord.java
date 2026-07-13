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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "scheduler_boss_records", uniqueConstraints = {
		@UniqueConstraint(name = "uk_scheduler_boss_records_character_boss_date", columnNames = {"character_id", "boss_id", "record_date"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchedulerBossRecord extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "character_id", nullable = false)
	private MapleCharacter character;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "boss_id", nullable = false)
	private BossMaster boss;

	@Column(name = "record_date", nullable = false)
	private LocalDate recordDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "reset_period", nullable = false, length = 10)
	private ResetPeriod resetPeriod;

	@Column(name = "is_completed", nullable = false)
	private boolean completed;

	@Column(name = "synced_at")
	private LocalDateTime syncedAt;
}
