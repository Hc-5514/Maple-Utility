package com.maple.utility.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.maple.utility.dto.request.BossItemAcquisitionCreateRequest;
import com.maple.utility.dto.response.BossDropItemAcquisitionStatusResponse;
import com.maple.utility.dto.response.BossDropItemResponse;
import com.maple.utility.dto.response.BossItemAcquisitionResponse;
import com.maple.utility.entity.BossDropItem;
import com.maple.utility.entity.BossItemAcquisition;
import com.maple.utility.entity.BossMaster;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.BossDropItemRepository;
import com.maple.utility.repository.BossItemAcquisitionRepository;
import com.maple.utility.repository.BossMasterRepository;
import com.maple.utility.repository.CharacterRepository;

@Service
public class BossDropService {

	private final BossMasterRepository bossMasterRepository;
	private final BossDropItemRepository bossDropItemRepository;
	private final BossItemAcquisitionRepository bossItemAcquisitionRepository;
	private final CharacterRepository characterRepository;

	public BossDropService(
			BossMasterRepository bossMasterRepository,
			BossDropItemRepository bossDropItemRepository,
			BossItemAcquisitionRepository bossItemAcquisitionRepository,
			CharacterRepository characterRepository
	) {
		this.bossMasterRepository = bossMasterRepository;
		this.bossDropItemRepository = bossDropItemRepository;
		this.bossItemAcquisitionRepository = bossItemAcquisitionRepository;
		this.characterRepository = characterRepository;
	}

	@Transactional(readOnly = true)
	public List<BossDropItemResponse> getDropItems(Long bossId) {
		findBoss(bossId);
		return bossDropItemRepository.findByBossIdOrderByIdAsc(bossId).stream()
				.map(BossDropItemResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<BossDropItemAcquisitionStatusResponse> getAcquisitionStatus(
			Long userId,
			Long bossId,
			Long characterId
	) {
		findCharacter(userId, characterId);
		findBoss(bossId);
		List<BossDropItem> dropItems = bossDropItemRepository.findByBossIdOrderByIdAsc(bossId);
		if (dropItems.isEmpty()) {
			return List.of();
		}

		List<Long> dropItemIds = dropItems.stream()
				.map(BossDropItem::getId)
				.toList();
		Map<Long, List<BossItemAcquisitionResponse>> acquisitionsByDropItemId = bossItemAcquisitionRepository
				.findByCharacter_IdAndBossDropItem_IdInOrderByAcquiredDateDescIdDesc(characterId, dropItemIds)
				.stream()
				.map(BossItemAcquisitionResponse::from)
				.collect(Collectors.groupingBy(BossItemAcquisitionResponse::bossDropItemId));

		return dropItems.stream()
				.map(dropItem -> BossDropItemAcquisitionStatusResponse.of(
						BossDropItemResponse.from(dropItem),
						acquisitionsByDropItemId.getOrDefault(dropItem.getId(), List.of())
				))
				.toList();
	}

	@Transactional
	public BossItemAcquisitionResponse createAcquisition(Long userId, BossItemAcquisitionCreateRequest request) {
		MapleCharacter character = findCharacter(userId, request.characterId());
		BossDropItem bossDropItem = bossDropItemRepository.findById(request.bossDropItemId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOSS_DROP_ITEM_NOT_FOUND", "보스 드랍 아이템 없음"));
		BossItemAcquisition acquisition = BossItemAcquisition.create(
				character,
				bossDropItem,
				request.acquiredDate(),
				request.memo()
		);
		return BossItemAcquisitionResponse.from(bossItemAcquisitionRepository.save(acquisition));
	}

	@Transactional
	public void deleteAcquisition(Long userId, Long id) {
		BossItemAcquisition acquisition = bossItemAcquisitionRepository.findByIdAndCharacter_User_Id(id, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOSS_ITEM_ACQUISITION_NOT_FOUND", "보스 드랍 획득 기록 없음"));
		bossItemAcquisitionRepository.delete(acquisition);
	}

	private BossMaster findBoss(Long bossId) {
		return bossMasterRepository.findById(bossId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOSS_NOT_FOUND", "보스 없음"));
	}

	private MapleCharacter findCharacter(Long userId, Long characterId) {
		return characterRepository.findByIdAndUserId(characterId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHARACTER_NOT_FOUND", "캐릭터 없음"));
	}
}
