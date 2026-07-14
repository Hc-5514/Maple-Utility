package com.maple.utility.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "boss_drop_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BossDropItem extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "boss_id", nullable = false)
	private BossMaster boss;

	@Column(name = "item_name", nullable = false, length = 100)
	private String itemName;

	@Column(name = "item_image")
	private String itemImage;

	@Column(name = "item_description")
	private String itemDescription;

	@Enumerated(EnumType.STRING)
	@Column(name = "drop_rate_tier", length = 20)
	private DropRateTier dropRateTier;
}
