package com.maple.utility.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Comparator;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.maple.utility.dto.request.HuntingRecordRequest;
import com.maple.utility.dto.response.HuntingRecordResponse;
import com.maple.utility.entity.HuntingRecord;

import com.maple.utility.entity.HuntingGround;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.User;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.HuntingRecordRepository;
import com.maple.utility.repository.HuntingRecordSpecifications;
import com.maple.utility.repository.HuntingGroundRepository;
import com.maple.utility.repository.UserHuntingGroundFavoriteRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.entity.UserHuntingGroundFavorite;
import com.maple.utility.dto.response.HuntingGroundResponse;

@Service
public class HuntingService {

	private final HuntingRecordRepository huntingRecordRepository;
	private final CharacterRepository characterRepository;
	private final HuntingGroundRepository huntingGroundRepository;
	private final UserHuntingGroundFavoriteRepository huntingGroundFavoriteRepository;
	private final UserRepository userRepository;

	public HuntingService(
			HuntingRecordRepository huntingRecordRepository,
			CharacterRepository characterRepository,
			HuntingGroundRepository huntingGroundRepository,
			UserHuntingGroundFavoriteRepository huntingGroundFavoriteRepository,
			UserRepository userRepository
	) {
		this.huntingRecordRepository = huntingRecordRepository;
		this.characterRepository = characterRepository;
		this.huntingGroundRepository = huntingGroundRepository;
		this.huntingGroundFavoriteRepository = huntingGroundFavoriteRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public List<HuntingRecordResponse> getRecords(Long userId, Long characterId, LocalDate from, LocalDate to) {
		validateDateRange(from, to);
		Sort sort = Sort.by(Sort.Order.desc("recordDate"), Sort.Order.desc("id"));
		List<HuntingRecord> records = characterId == null
				? huntingRecordRepository.findAll(HuntingRecordSpecifications.favoriteRecords(userId, from, to), sort)
				: huntingRecordRepository.findAll(
						HuntingRecordSpecifications.characterRecords(findCharacter(userId, characterId).getId(), from, to), sort);
		return records.stream()
				.map(HuntingRecordResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public HuntingRecordResponse getRecord(Long userId, Long id) {
		return HuntingRecordResponse.from(findRecord(userId, id));
	}

	@Transactional
	public HuntingRecordResponse createRecord(Long userId, HuntingRecordRequest request) {
		MapleCharacter character = findCharacter(userId, request.characterId());
		long mesoEarned = mesoEarned(request);
		int solErdaEarned = solErdaEarned(request);
		validateReward(mesoEarned, solErdaEarned);
		validateDuplicate(character.getId(), request.recordDate());
		HuntingGround ground = findGround(request.huntingGroundId());

		HuntingRecord record = HuntingRecord.create(
				character,
				request.recordDate(),
				mesoEarned,
				solErdaEarned,
				request.playDurationMin(),
				ground == null ? request.huntingGround() : ground.getMapName(),
				ground,
				request.memo()
		);
		return HuntingRecordResponse.from(huntingRecordRepository.save(record));
	}

	@Transactional
	public HuntingRecordResponse updateRecord(Long userId, Long id, HuntingRecordRequest request) {
		HuntingRecord record = findRecord(userId, id);
		MapleCharacter character = findCharacter(userId, request.characterId());
		if (!record.getCharacter().getId().equals(character.getId())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "HUNTING_RECORD_CHARACTER_MISMATCH", "사냥 기록 캐릭터 불일치");
		}

		long mesoEarned = mesoEarned(request);
		int solErdaEarned = solErdaEarned(request);
		validateReward(mesoEarned, solErdaEarned);
		validateDuplicateForUpdate(character.getId(), request.recordDate(), record.getId());
		HuntingGround ground = findGround(request.huntingGroundId());

		record.update(
				request.recordDate(),
				mesoEarned,
				solErdaEarned,
				request.playDurationMin(),
				ground == null ? request.huntingGround() : ground.getMapName(),
				ground,
				request.memo()
		);
		return HuntingRecordResponse.from(record);
	}

	@Transactional(readOnly = true)
	public List<HuntingGroundResponse> getGrounds(Long userId) {
		var favoriteIds = huntingGroundFavoriteRepository.findByIdUserId(userId).stream()
				.map(favorite -> favorite.getId().huntingGroundId()).collect(Collectors.toSet());
		List<HuntingGround> grounds = huntingGroundRepository.findAll();
		var regionalMaxLevels = grounds.stream().collect(Collectors.groupingBy(HuntingGround::getRegionName,
				Collectors.collectingAndThen(Collectors.maxBy(Comparator.comparingInt(HuntingGround::getMaxMonsterLevel)),
						max -> max.orElseThrow().getMaxMonsterLevel())));
		return grounds.stream()
				.sorted(Comparator.comparingInt((HuntingGround ground) -> regionalMaxLevels.get(ground.getRegionName())).reversed()
						.thenComparing((HuntingGround ground) -> favoriteIds.contains(ground.getId()), Comparator.reverseOrder())
						.thenComparing(HuntingGround::getMaxMonsterLevel, Comparator.reverseOrder())
						.thenComparing(HuntingGround::getMapName, Comparator.reverseOrder()))
				.map(ground -> HuntingGroundResponse.from(ground, favoriteIds.contains(ground.getId())))
				.toList();
	}

	@Transactional(readOnly = true)
	public HuntingGroundResponse getLatestGround(Long userId, Long characterId) {
		MapleCharacter character = findCharacter(userId, characterId);
		return huntingRecordRepository.findFirstByCharacter_IdAndHuntingGroundCatalogIsNotNullOrderByRecordDateDescIdDesc(character.getId())
				.map(record -> HuntingGroundResponse.from(record.getHuntingGroundCatalog(), true)).orElse(null);
	}

	@Transactional
	public void favoriteGround(Long userId, Long groundId) {
		HuntingGround ground = huntingGroundRepository.findById(groundId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HUNTING_GROUND_NOT_FOUND", "사냥터 없음"));
		User user = userRepository.findById(userId).orElseThrow();
		UserHuntingGroundFavorite.Id id = new UserHuntingGroundFavorite.Id(userId, groundId);
		if (!huntingGroundFavoriteRepository.existsById(id)) huntingGroundFavoriteRepository.save(UserHuntingGroundFavorite.create(user, ground));
	}

	@Transactional
	public void unfavoriteGround(Long userId, Long groundId) {
		huntingGroundFavoriteRepository.deleteById(new UserHuntingGroundFavorite.Id(userId, groundId));
	}

	private HuntingGround findGround(Long groundId) {
		if (groundId == null) return null;
		return huntingGroundRepository.findById(groundId)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "HUNTING_GROUND_NOT_FOUND", "사냥터 없음"));
	}

	@Transactional
	public void deleteRecord(Long userId, Long id) {
		HuntingRecord record = findRecord(userId, id);
		huntingRecordRepository.delete(record);
	}

	private MapleCharacter findCharacter(Long userId, Long characterId) {
		MapleCharacter character = characterRepository.findByIdAndUserId(characterId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHARACTER_NOT_FOUND", "캐릭터 없음"));
		if (!character.isFavorite()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CHARACTER_NOT_FAVORITE", "즐겨찾기 캐릭터 아님");
		}
		return character;
	}

	private HuntingRecord findRecord(Long userId, Long id) {
		HuntingRecord record = huntingRecordRepository.findByIdAndCharacter_User_Id(id, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HUNTING_RECORD_NOT_FOUND", "사냥 기록 없음"));
		if (!record.getCharacter().isFavorite()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CHARACTER_NOT_FAVORITE", "즐겨찾기 캐릭터 아님");
		}
		return record;
	}

	private void validateDateRange(LocalDate from, LocalDate to) {
		if (from != null && to != null && from.isAfter(to)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "조회 기간 오류");
		}
	}

	private void validateReward(long mesoEarned, int solErdaEarned) {
		if (mesoEarned == 0 && solErdaEarned == 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "HUNTING_REWARD_REQUIRED", "사냥 보상 필요");
		}
	}

	private void validateDuplicate(Long characterId, LocalDate recordDate) {
		if (huntingRecordRepository.existsByCharacter_IdAndRecordDate(characterId, recordDate)) {
			throw new ApiException(HttpStatus.CONFLICT, "HUNTING_RECORD_ALREADY_EXISTS", "사냥 기록 중복");
		}
	}

	private void validateDuplicateForUpdate(Long characterId, LocalDate recordDate, Long recordId) {
		if (huntingRecordRepository.existsByCharacter_IdAndRecordDateAndIdNot(characterId, recordDate, recordId)) {
			throw new ApiException(HttpStatus.CONFLICT, "HUNTING_RECORD_ALREADY_EXISTS", "사냥 기록 중복");
		}
	}

	private long mesoEarned(HuntingRecordRequest request) {
		return request.mesoEarned() == null ? 0 : request.mesoEarned();
	}

	private int solErdaEarned(HuntingRecordRequest request) {
		return request.solErdaEarned() == null ? 0 : request.solErdaEarned();
	}
}
