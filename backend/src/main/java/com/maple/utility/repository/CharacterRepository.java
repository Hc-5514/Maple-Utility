package com.maple.utility.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.maple.utility.entity.MapleCharacter;

public interface CharacterRepository extends JpaRepository<MapleCharacter, Long> {

	List<MapleCharacter> findByUserIdOrderBySortOrderAscIdAsc(Long userId);

	List<MapleCharacter> findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(Long userId);

	Optional<MapleCharacter> findByUserIdAndOcid(Long userId, String ocid);
}
