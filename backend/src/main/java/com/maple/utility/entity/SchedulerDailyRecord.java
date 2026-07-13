package com.maple.utility.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "scheduler_daily_records", uniqueConstraints = {
		@UniqueConstraint(name = "uk_scheduler_daily_records_character_date_content", columnNames = {"character_id", "record_date", "content_name"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchedulerDailyRecord extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "character_id", nullable = false)
	private MapleCharacter character;

	@Column(name = "record_date", nullable = false)
	private LocalDate recordDate;

	@Column(name = "content_name", nullable = false, length = 100)
	private String contentName;

	@Column(name = "completed_count", nullable = false)
	private int completedCount;

	@Column(name = "total_count", nullable = false)
	private int totalCount;

	@Column(name = "synced_at")
	private LocalDateTime syncedAt;
}
