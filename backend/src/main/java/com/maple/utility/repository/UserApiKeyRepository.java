package com.maple.utility.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.maple.utility.entity.UserApiKey;

public interface UserApiKeyRepository extends JpaRepository<UserApiKey, Long> {

	Optional<UserApiKey> findByUserId(Long userId);
}
