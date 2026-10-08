package com.maple.utility.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "hunting_grounds", uniqueConstraints = @UniqueConstraint(name = "uk_hunting_grounds_region_map", columnNames = {"region_name", "map_name"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HuntingGround extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "region_name", nullable = false)
	private String regionName;

	@Column(name = "map_name", nullable = false)
	private String mapName;

	@Column(name = "max_monster_level", nullable = false)
	private int maxMonsterLevel;
}
