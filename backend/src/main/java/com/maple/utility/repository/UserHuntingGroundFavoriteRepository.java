package com.maple.utility.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.maple.utility.entity.UserHuntingGroundFavorite;

public interface UserHuntingGroundFavoriteRepository extends JpaRepository<UserHuntingGroundFavorite, UserHuntingGroundFavorite.Id> {
	List<UserHuntingGroundFavorite> findByIdUserId(Long userId);
}
