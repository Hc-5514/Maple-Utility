package com.maple.utility.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.maple.utility.dto.request.BossItemAcquisitionCreateRequest;
import com.maple.utility.dto.request.BossPeriodSaveRequest;
import com.maple.utility.dto.response.BossDropItemAcquisitionStatusResponse;
import com.maple.utility.dto.response.BossDropItemResponse;
import com.maple.utility.dto.response.BossItemAcquisitionResponse;
import com.maple.utility.dto.response.BossPeriodResponse;
import com.maple.utility.entity.BossDropItem;
import com.maple.utility.entity.BossItemAcquisition;
import com.maple.utility.entity.BossItemKind;
import com.maple.utility.entity.BossMaster;
import com.maple.utility.entity.BossPeriodEntry;
import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.BossDropItemRepository;
import com.maple.utility.repository.BossItemAcquisitionRepository;
import com.maple.utility.repository.BossMasterRepository;
import com.maple.utility.repository.BossPeriodEntryRepository;
import com.maple.utility.repository.CharacterRepository;

@Service
public class BossDropService {

	private final BossMasterRepository bossMasterRepository;
	private final BossDropItemRepository bossDropItemRepository;
	private final BossItemAcquisitionRepository bossItemAcquisitionRepository;
	private final CharacterRepository characterRepository;
	private final BossPeriodEntryRepository bossPeriodEntryRepository;

	public BossDropService(
			BossMasterRepository bossMasterRepository,
			BossDropItemRepository bossDropItemRepository,
			BossItemAcquisitionRepository bossItemAcquisitionRepository,
			CharacterRepository characterRepository,
			BossPeriodEntryRepository bossPeriodEntryRepository
	) {
		this.bossMasterRepository = bossMasterRepository;
		this.bossDropItemRepository = bossDropItemRepository;
		this.bossItemAcquisitionRepository = bossItemAcquisitionRepository;
		this.characterRepository = characterRepository;
		this.bossPeriodEntryRepository = bossPeriodEntryRepository;
	}

	@Transactional(readOnly = true)
	public List<BossDropItemResponse> getDropItems(Long bossId) {
		findBoss(bossId);
		return bossDropItemRepository.findByBossIdAndActiveTrueOrderByIdAsc(bossId).stream()
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
		List<BossDropItem> dropItems = bossDropItemRepository.findByBossIdAndActiveTrueOrderByIdAsc(bossId);
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

	@Transactional(readOnly = true)
	public BossPeriodResponse getPeriod(Long userId, Long bossId, Long characterId, LocalDate periodStart) {
		findCharacter(userId, characterId);
		BossMaster boss = findBoss(bossId);
		validatePeriodStart(boss, periodStart);
		BossPeriodEntry entry = bossPeriodEntryRepository
				.findByCharacter_IdAndBoss_IdAndPeriodStart(characterId, bossId, periodStart).orElse(null);
		return periodResponse(boss, characterId, periodStart, entry);
	}

	@Transactional
	public BossPeriodResponse savePeriod(Long userId, Long bossId, BossPeriodSaveRequest request) {
		MapleCharacter character = findCharacter(userId, request.characterId());
		BossMaster boss = findBoss(bossId);
		validatePeriodStart(boss, request.periodStart());
		if (request.partySize() < 1 || request.partySize() > 6) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARTY_SIZE", "파티 인원 오류");
		}
		List<BossDropItem> dropItems = bossDropItemRepository.findByBossIdAndActiveTrueOrderByIdAsc(bossId);
		Map<Long, BossDropItem> dropItemsById = dropItems.stream()
				.collect(Collectors.toMap(BossDropItem::getId, item -> item));
		Set<Long> submittedIds = new HashSet<>();
		for (BossPeriodSaveRequest.Item item : request.items()) {
			if (!submittedIds.add(item.bossDropItemId()) || !dropItemsById.containsKey(item.bossDropItemId())) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOSS_DROP_ITEM", "보스 드랍 아이템 오류");
			}
			if (item.acquired() && dropItemsById.get(item.bossDropItemId()).getItemKind() != BossItemKind.CRYSTAL
					&& item.mesoAmount() != null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MESO_AMOUNT", "결정석 외 메소 금액 오류");
			}
		}
		BossPeriodEntry entry = bossPeriodEntryRepository
				.findByCharacter_IdAndBoss_IdAndPeriodStart(character.getId(), bossId, request.periodStart())
				.orElseGet(() -> bossPeriodEntryRepository.save(BossPeriodEntry.create(
						character, boss, request.periodStart(), request.partySize())));
		entry.save(request.partySize());
		Map<Long, BossItemAcquisition> existing = new HashMap<>();
		for (BossItemAcquisition acquisition : bossItemAcquisitionRepository.findByPeriodEntry_IdOrderByIdAsc(entry.getId())) {
			BossItemAcquisition duplicate = existing.putIfAbsent(acquisition.getBossDropItem().getId(), acquisition);
			if (duplicate != null) {
				bossItemAcquisitionRepository.delete(acquisition);
			}
		}
		for (BossPeriodSaveRequest.Item item : request.items()) {
			BossItemAcquisition acquisition = existing.remove(item.bossDropItemId());
			if (!item.acquired()) {
				if (acquisition != null) {
					bossItemAcquisitionRepository.delete(acquisition);
				}
				continue;
			}
			BossDropItem dropItem = dropItemsById.get(item.bossDropItemId());
			Long mesoAmount = dropItem.getItemKind() == BossItemKind.CRYSTAL
					? item.mesoAmount() == null
						? boss.getCrystalPrice() == 0 ? null : boss.getCrystalPrice() / request.partySize()
						: item.mesoAmount()
					: null;
			if (acquisition == null) {
				bossItemAcquisitionRepository.save(BossItemAcquisition.createForPeriod(
						entry, dropItem, item.quantity(), mesoAmount));
			} else {
				acquisition.updateForPeriod(item.quantity(), mesoAmount);
			}
		}
		bossItemAcquisitionRepository.deleteAll(existing.values());
		bossItemAcquisitionRepository.flush();
		return periodResponse(boss, character.getId(), request.periodStart(), entry);
	}

	private BossPeriodResponse periodResponse(BossMaster boss, Long characterId, LocalDate periodStart, BossPeriodEntry entry) {
		Map<Long, BossItemAcquisition> current = new HashMap<>();
		if (entry != null) {
			for (BossItemAcquisition acquisition : bossItemAcquisitionRepository.findByPeriodEntry_IdOrderByIdAsc(entry.getId())) {
				current.putIfAbsent(acquisition.getBossDropItem().getId(), acquisition);
			}
		}
		Map<Long, BossItemAcquisition> latest = new HashMap<>();
		for (BossItemAcquisition acquisition : bossItemAcquisitionRepository
				.findByCharacter_IdAndBossDropItem_Boss_IdOrderByAcquiredDateDescIdDesc(characterId, boss.getId())) {
			latest.putIfAbsent(acquisition.getBossDropItem().getId(), acquisition);
		}
		int partySize = entry == null ? 1 : entry.getPartySize();
		List<BossPeriodResponse.Item> items = bossDropItemRepository.findByBossIdAndActiveTrueOrderByIdAsc(boss.getId())
				.stream().map(dropItem -> {
					BossItemAcquisition saved = current.get(dropItem.getId());
					BossItemAcquisition previous = latest.get(dropItem.getId());
					int quantity = saved != null ? saved.getQuantity()
							: previous != null ? previous.getQuantity() : dropItem.getDefaultQuantity();
					Long mesoAmount = null;
					if (dropItem.getItemKind() == BossItemKind.CRYSTAL) {
						mesoAmount = saved != null && saved.getMesoAmount() != null ? saved.getMesoAmount()
								: previous != null && previous.getMesoAmount() != null ? previous.getMesoAmount()
								: boss.getCrystalPrice() == 0 ? null : boss.getCrystalPrice() / partySize;
					}
					return new BossPeriodResponse.Item(BossDropItemResponse.from(dropItem), saved != null, quantity, mesoAmount);
				}).toList();
		return new BossPeriodResponse(boss.getId(), characterId, periodStart, partySize, boss.getCrystalPrice(),
				entry == null ? null : entry.getSavedAt(), items);
	}

	private void validatePeriodStart(BossMaster boss, LocalDate periodStart) {
		if (periodStart == null || (boss.getResetPeriod() == ResetPeriod.WEEKLY
				? periodStart.getDayOfWeek() != DayOfWeek.THURSDAY
				: periodStart.getDayOfMonth() != 1)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOSS_PERIOD", "보스 기간 시작일 오류");
		}
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
		if (!acquisition.getCharacter().isFavorite()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CHARACTER_NOT_FAVORITE", "즐겨찾기 캐릭터 아님");
		}
		bossItemAcquisitionRepository.delete(acquisition);
	}

	private BossMaster findBoss(Long bossId) {
		return bossMasterRepository.findById(bossId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOSS_NOT_FOUND", "보스 없음"));
	}

	private MapleCharacter findCharacter(Long userId, Long characterId) {
		MapleCharacter character = characterRepository.findByIdAndUserId(characterId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHARACTER_NOT_FOUND", "캐릭터 없음"));
		if (!character.isFavorite()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CHARACTER_NOT_FAVORITE", "즐겨찾기 캐릭터 아님");
		}
		return character;
	}
}
