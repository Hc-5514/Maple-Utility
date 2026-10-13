package com.maple.utility.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.maple.utility.entity.UserApiKey;

public interface UserApiKeyRepository extends JpaRepository<UserApiKey, Long> {

	Optional<UserApiKey> findFirstByUserIdOrderByIdAsc(Long userId);

	default Optional<UserApiKey> findByUserId(Long userId) {
		return findFirstByUserIdOrderByIdAsc(userId);
	}

	Optional<UserApiKey> findByKeyFingerprint(String fingerprint);
	List<UserApiKey> findByKeyFingerprintIsNullOrderByIdAsc();
	List<UserApiKey> findByContextIdOrderByIdAsc(Long contextId);
	List<UserApiKey> findByUserIdOrderByIdAsc(Long userId);

    @Query(value = "SELECT k.* FROM user_api_keys k JOIN data_contexts c ON c.id=k.context_id WHERE c.owner_user_id=:ownerId AND c.canonical_context_id IS NULL AND k.key_status='ACTIVE' ORDER BY k.id LIMIT 1", nativeQuery = true)
    Optional<UserApiKey> findActiveByDataOwnerId(Long ownerId);

    void deleteByUserId(Long userId);
}
