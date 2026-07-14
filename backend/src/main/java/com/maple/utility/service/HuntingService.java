package com.maple.utility.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.maple.utility.dto.request.HuntingRecordRequest;
import com.maple.utility.dto.response.HuntingRecordResponse;
import com.maple.utility.entity.HuntingRecord;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.HuntingRecordRepository;

@Service
public class HuntingService {

	private final HuntingRecordRepository huntingRecordRepository;
	private final CharacterRepository characterRepository;

	public HuntingService(
			HuntingRecordRepository huntingRecordRepository,
			CharacterRepository characterRepository
	) {
		this.huntingRecordRepository = huntingRecordRepository;
		this.characterRepository = characterRepository;
	}

	@Transactional(readOnly = true)
	public List<HuntingRecordResponse> getRecords(Long userId, Long characterId, LocalDate from, LocalDate to) {
		MapleCharacter character = findCharacter(userId, characterId);
		validateDateRange(from, to);
		return huntingRecordRepository.findByCharacterIdAndRecordDateRange(character.getId(), from, to).stream()
				.map(HuntingRecordResponse::from)
				.toList();
	}

	@Transactional
	public HuntingRecordResponse createRecord(Long userId, HuntingRecordRequest request) {
		MapleCharacter character = findCharacter(userId, request.characterId());
		long mesoEarned = mesoEarned(request);
		int solErdaEarned = solErdaEarned(request);
		validateReward(mesoEarned, solErdaEarned);
		validateDuplicate(character.getId(), request.recordDate());

		HuntingRecord record = HuntingRecord.create(
				character,
				request.recordDate(),
				mesoEarned,
				solErdaEarned,
				request.playDurationMin(),
				request.huntingGround(),
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

		record.update(
				request.recordDate(),
				mesoEarned,
				solErdaEarned,
				request.playDurationMin(),
				request.huntingGround(),
				request.memo()
		);
		return HuntingRecordResponse.from(record);
	}

	@Transactional
	public void deleteRecord(Long userId, Long id) {
		HuntingRecord record = findRecord(userId, id);
		huntingRecordRepository.delete(record);
	}

	private MapleCharacter findCharacter(Long userId, Long characterId) {
		return characterRepository.findByIdAndUserId(characterId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHARACTER_NOT_FOUND", "캐릭터 없음"));
	}

	private HuntingRecord findRecord(Long userId, Long id) {
		return huntingRecordRepository.findByIdAndCharacter_User_Id(id, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HUNTING_RECORD_NOT_FOUND", "사냥 기록 없음"));
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
