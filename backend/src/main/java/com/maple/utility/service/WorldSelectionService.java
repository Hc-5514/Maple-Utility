package com.maple.utility.service;

import java.util.HashSet;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.maple.utility.dto.response.WorldSelectionResponse;
import com.maple.utility.entity.User;
import com.maple.utility.entity.UserSelectedWorld;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.repository.UserSelectedWorldRepository;

@Service
public class WorldSelectionService {
	private static final List<String> WORLDS = List.of(
			"스카니아", "베라", "루나", "제니스", "크로아", "유니온", "엘리시움",
			"이노시스", "레드", "오로라", "아케인", "노바", "에오스", "헬리오스");
	private final UserRepository users;
	private final UserSelectedWorldRepository selections;
	private final DataPreferenceSourceService preferenceSources;

	public WorldSelectionService(UserRepository users, UserSelectedWorldRepository selections) {
		this(users, selections, null);
	}

	@org.springframework.beans.factory.annotation.Autowired
	public WorldSelectionService(UserRepository users, UserSelectedWorldRepository selections,
			DataPreferenceSourceService preferenceSources) {
		this.preferenceSources = preferenceSources;
		this.users = users;
		this.selections = selections;
	}

	@Transactional(readOnly = true)
	public WorldSelectionResponse getSelection(Long userId) {
		users.findById(userId).orElseThrow(this::userNotFound);
		var selected = selections.findByUser_Id(userId).stream()
				.map(UserSelectedWorld::getWorldName).collect(java.util.stream.Collectors.toSet());
		return new WorldSelectionResponse(WORLDS.stream().filter(selected::contains).toList());
	}

	@Transactional
	public WorldSelectionResponse saveSelection(Long userId, List<String> worldNames) {
		return saveSelection(userId, userId, worldNames);
	}

	@Transactional
	public WorldSelectionResponse saveSelection(Long userId, Long identityUserId, List<String> worldNames) {
		if (worldNames == null || worldNames.stream().anyMatch(name -> name == null || !WORLDS.contains(name))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WORLD_SELECTION", "월드 선택 값 오류");
		}
		if (new HashSet<>(worldNames).size() != worldNames.size()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_WORLD_SELECTION", "중복 월드 선택");
		}
		// Serialize replacements even for users with no existing selection rows.
		User user = users.findByIdForUpdate(userId).orElseThrow(this::userNotFound);
		List<String> ordered = WORLDS.stream().filter(worldNames::contains).toList();
		selections.deleteAllByUserId(userId);
		selections.saveAll(ordered.stream().map(name -> UserSelectedWorld.create(user, name)).toList());
		if (preferenceSources != null) {
			preferenceSources.saveExplicit(DataPreferenceSourceService.WORLD_SELECTION, userId, identityUserId);
		}
		return new WorldSelectionResponse(ordered);
	}

	private ApiException userNotFound() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "사용자 없음");
	}
}
