package com.maple.utility.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "user_hunting_ground_favorites")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserHuntingGroundFavorite {
	@EmbeddedId
	private Id id;

	@MapsId("userId")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@MapsId("huntingGroundId")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "hunting_ground_id")
	private HuntingGround huntingGround;

	public static UserHuntingGroundFavorite create(User user, HuntingGround huntingGround) {
		UserHuntingGroundFavorite favorite = new UserHuntingGroundFavorite();
		favorite.id = new Id(user.getId(), huntingGround.getId());
		favorite.user = user;
		favorite.huntingGround = huntingGround;
		return favorite;
	}

	@Embeddable
	public record Id(@Column(name = "user_id") Long userId, @Column(name = "hunting_ground_id") Long huntingGroundId) implements Serializable { }
}
