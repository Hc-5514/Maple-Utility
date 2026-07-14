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

	@Column(name = "memo", length = 255)
	private String memo;

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
		acquisition.memo = memo;
		return acquisition;
	}
}
