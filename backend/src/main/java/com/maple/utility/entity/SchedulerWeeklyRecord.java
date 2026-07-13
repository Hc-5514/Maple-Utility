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
@Table(name = "scheduler_weekly_records", uniqueConstraints = {
		@UniqueConstraint(name = "uk_scheduler_weekly_records_character_week_content", columnNames = {"character_id", "week_start_date", "content_name"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchedulerWeeklyRecord extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "character_id", nullable = false)
	private MapleCharacter character;

	@Column(name = "week_start_date", nullable = false)
	private LocalDate weekStartDate;

	@Column(name = "content_name", nullable = false, length = 100)
	private String contentName;

	@Column(name = "is_completed", nullable = false)
	private boolean completed;

	@Column(name = "score")
	private Integer score;

	@Column(name = "synced_at")
	private LocalDateTime syncedAt;
}
