package com.maple.utility.entity;

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
@Table(name = "characters", uniqueConstraints = {
		@UniqueConstraint(name = "uk_characters_user_id_ocid", columnNames = {"user_id", "ocid"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapleCharacter extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "ocid", nullable = false, length = 100)
	private String ocid;

	@Column(name = "character_name", nullable = false, length = 50)
	private String characterName;

	@Column(name = "world_name", length = 30)
	private String worldName;

	@Column(name = "character_class", length = 50)
	private String characterClass;

	@Column(name = "character_level")
	private Integer characterLevel;

	@Column(name = "character_image")
	private String characterImage;

	@Column(name = "guild_name", length = 50)
	private String guildName;

	@Column(name = "is_favorite", nullable = false)
	private boolean favorite;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	public static MapleCharacter create(
			User user,
			String ocid,
			String characterName,
			String worldName,
			String characterClass,
			Integer characterLevel,
			int sortOrder
	) {
		MapleCharacter character = new MapleCharacter();
		character.user = user;
		character.ocid = ocid;
		character.characterName = characterName;
		character.worldName = worldName;
		character.characterClass = characterClass;
		character.characterLevel = characterLevel;
		character.sortOrder = sortOrder;
		return character;
	}

	public void updateBasicInfo(
			String characterName,
			String worldName,
			String characterClass,
			Integer characterLevel,
			int sortOrder
	) {
		this.characterName = characterName;
		this.worldName = worldName;
		this.characterClass = characterClass;
		this.characterLevel = characterLevel;
		this.sortOrder = sortOrder;
	}
}
