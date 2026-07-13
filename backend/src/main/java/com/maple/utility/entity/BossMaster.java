package com.maple.utility.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "boss_master", uniqueConstraints = {
		@UniqueConstraint(name = "uk_boss_master_boss_name_difficulty", columnNames = {"boss_name", "difficulty"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BossMaster extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "boss_name", nullable = false, length = 50)
	private String bossName;

	@Enumerated(EnumType.STRING)
	@Column(name = "difficulty", nullable = false, length = 20)
	private Difficulty difficulty;

	@Enumerated(EnumType.STRING)
	@Column(name = "reset_period", nullable = false, length = 10)
	private ResetPeriod resetPeriod;

	@Column(name = "crystal_price", nullable = false)
	private long crystalPrice;

	@Column(name = "boss_image")
	private String bossImage;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "is_active", nullable = false)
	private boolean active;
}
