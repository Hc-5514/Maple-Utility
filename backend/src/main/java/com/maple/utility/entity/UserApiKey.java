package com.maple.utility.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "user_api_keys")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserApiKey extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "encrypted_key", nullable = false)
	private String encryptedKey;

	@Enumerated(EnumType.STRING)
	@Column(name = "key_status", nullable = false, length = 20)
	private ApiKeyStatus keyStatus;

	@Column(name = "last_verified_at")
	private LocalDateTime lastVerifiedAt;
}
