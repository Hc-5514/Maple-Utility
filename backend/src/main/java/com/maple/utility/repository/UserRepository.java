package com.maple.utility.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByOauthProviderAndOauthId(OAuthProvider oauthProvider, String oauthId);
}
