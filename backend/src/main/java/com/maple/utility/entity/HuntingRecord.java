package com.maple.utility.entity;

import java.time.LocalDate;

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
@Table(name = "hunting_records", uniqueConstraints = {
		@UniqueConstraint(name = "uk_hunting_records_character_id_record_date", columnNames = {"character_id", "record_date"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HuntingRecord extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "character_id", nullable = false)
	private MapleCharacter character;

	@Column(name = "record_date", nullable = false)
	private LocalDate recordDate;

	@Column(name = "meso_earned", nullable = false)
	private long mesoEarned;

	@Column(name = "sol_erda_earned", nullable = false)
	private int solErdaEarned;

	@Column(name = "play_duration_min")
	private Integer playDurationMin;

	@Column(name = "hunting_ground", length = 100)
	private String huntingGround;

	@Column(name = "memo")
	private String memo;
}
