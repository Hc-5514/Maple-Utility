package com.maple.utility.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.maple.utility.entity.UserSelectedWorld;

public interface UserSelectedWorldRepository extends JpaRepository<UserSelectedWorld, Long> {
	List<UserSelectedWorld> findByUser_Id(Long userId);

	@Modifying(flushAutomatically = true)
	@Query("delete from UserSelectedWorld selection where selection.user.id = :userId")
	void deleteAllByUserId(Long userId);
}
