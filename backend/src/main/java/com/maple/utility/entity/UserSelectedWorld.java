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
@Table(name = "user_selected_worlds", uniqueConstraints = {
		@UniqueConstraint(name = "uk_user_selected_worlds_user_world", columnNames = {"user_id", "world_name"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSelectedWorld extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "world_name", nullable = false, length = 30)
	private String worldName;

	public static UserSelectedWorld create(User user, String worldName) {
		UserSelectedWorld selection = new UserSelectedWorld();
		selection.user = user;
		selection.worldName = worldName;
		return selection;
	}
}
