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

    @Query(value = "SELECT DISTINCT u.* FROM users u JOIN data_contexts c ON c.owner_user_id=u.id JOIN user_api_keys k ON k.context_id=c.id WHERE c.canonical_context_id IS NULL AND k.key_status='ACTIVE'", nativeQuery = true)
    java.util.List<User> findDataOwnersWithActiveKey();

    Optional<User> findByOauthProviderAndOauthId(OAuthProvider oauthProvider, String oauthId);
}
