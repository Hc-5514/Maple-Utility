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

	public static HuntingRecord create(
			MapleCharacter character,
			LocalDate recordDate,
			long mesoEarned,
			int solErdaEarned,
			Integer playDurationMin,
			String huntingGround,
			String memo
	) {
		HuntingRecord record = new HuntingRecord();
		record.character = character;
		record.recordDate = recordDate;
		record.mesoEarned = mesoEarned;
		record.solErdaEarned = solErdaEarned;
		record.playDurationMin = playDurationMin;
		record.huntingGround = huntingGround;
		record.memo = memo;
		return record;
	}

	public void update(
			LocalDate recordDate,
			long mesoEarned,
			int solErdaEarned,
			Integer playDurationMin,
			String huntingGround,
			String memo
	) {
		this.recordDate = recordDate;
		this.mesoEarned = mesoEarned;
		this.solErdaEarned = solErdaEarned;
		this.playDurationMin = playDurationMin;
		this.huntingGround = huntingGround;
		this.memo = memo;
	}
}
