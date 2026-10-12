package com.maple.utility.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select user from User user where user.id = :userId")
	Optional<User> findByIdForUpdate(Long userId);

	Optional<User> findByOauthProviderAndOauthId(OAuthProvider oauthProvider, String oauthId);
}
