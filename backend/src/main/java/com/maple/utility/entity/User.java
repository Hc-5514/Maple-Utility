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
@Table(name = "users", uniqueConstraints = {
		@UniqueConstraint(name = "uk_users_oauth_provider_oauth_id", columnNames = {"oauth_provider", "oauth_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "oauth_provider", nullable = false, length = 20)
	private OAuthProvider oauthProvider;

	@Column(name = "oauth_id", nullable = false, length = 100)
	private String oauthId;

	@Column(name = "email")
	private String email;

	@Column(name = "nickname", length = 50)
	private String nickname;
}
