package com.maple.utility.entity;

import java.time.LocalDate;

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
@Table(name = "boss_period_selections", uniqueConstraints = {
		@UniqueConstraint(name = "uk_boss_period_selections_character_boss_period", columnNames = {"character_id", "boss_id", "period_start"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BossPeriodSelection extends BaseTimeEntity {

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
	@Column(name = "reset_period", nullable = false, length = 10)
	private ResetPeriod resetPeriod;

	@Column(name = "period_start", nullable = false)
	private LocalDate periodStart;

	@Column(name = "is_visible", nullable = false)
	private boolean visible;

	@Column(name = "is_completed", nullable = false)
	private boolean completed;

	public static BossPeriodSelection create(MapleCharacter character, BossMaster boss, LocalDate periodStart, boolean visible) {
		BossPeriodSelection selection = new BossPeriodSelection();
		selection.character = character;
		selection.boss = boss;
		selection.resetPeriod = boss.getResetPeriod();
		selection.periodStart = periodStart;
		selection.visible = visible;
		selection.completed = true;
		return selection;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
		if (visible) this.completed = true;
	}
}
