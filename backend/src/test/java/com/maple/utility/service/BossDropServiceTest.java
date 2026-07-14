package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.maple.utility.dto.request.BossItemAcquisitionCreateRequest;
import com.maple.utility.dto.response.BossDropItemAcquisitionStatusResponse;
import com.maple.utility.dto.response.BossDropItemResponse;
import com.maple.utility.dto.response.BossItemAcquisitionResponse;
import com.maple.utility.entity.BossDropItem;
import com.maple.utility.entity.BossItemAcquisition;
import com.maple.utility.entity.BossMaster;
import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.DropRateTier;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.entity.User;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.BossDropItemRepository;
import com.maple.utility.repository.BossItemAcquisitionRepository;
import com.maple.utility.repository.BossMasterRepository;
import com.maple.utility.repository.CharacterRepository;

@ExtendWith(MockitoExtension.class)
class BossDropServiceTest {

	@Mock
	private BossMasterRepository bossMasterRepository;

	@Mock
	private BossDropItemRepository bossDropItemRepository;

	@Mock
	private BossItemAcquisitionRepository bossItemAcquisitionRepository;

	@Mock
	private CharacterRepository characterRepository;

	private BossDropService bossDropService;

	@BeforeEach
	void setUp() {
		bossDropService = new BossDropService(
				bossMasterRepository,
				bossDropItemRepository,
				bossItemAcquisitionRepository,
				characterRepository
		);
	}

	@Test
	void getDropItemsReturnsBossDropItems() {
		BossMaster boss = boss(20L);
		BossDropItem dropItem = dropItem(100L, boss, "아케인셰이드 무기 상자");
		when(bossMasterRepository.findById(20L)).thenReturn(Optional.of(boss));
		when(bossDropItemRepository.findByBossIdOrderByIdAsc(20L)).thenReturn(List.of(dropItem));

		List<BossDropItemResponse> response = bossDropService.getDropItems(20L);

		assertThat(response).hasSize(1);
		assertThat(response.get(0).id()).isEqualTo(100L);
		assertThat(response.get(0).bossId()).isEqualTo(20L);
		assertThat(response.get(0).itemName()).isEqualTo("아케인셰이드 무기 상자");
	}

	@Test
	void getAcquisitionStatusReturnsDropItemsWithAcquisitions() {
		User user = user();
		MapleCharacter character = character(user);
		BossMaster boss = boss(20L);
		BossDropItem firstDropItem = dropItem(100L, boss, "아케인셰이드 무기 상자");
		BossDropItem secondDropItem = dropItem(101L, boss, "루즈 컨트롤 머신 마크");
		BossItemAcquisition acquisition = acquisition(1000L, character, firstDropItem, LocalDate.parse("2026-07-14"));

		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		when(bossMasterRepository.findById(20L)).thenReturn(Optional.of(boss));
		when(bossDropItemRepository.findByBossIdOrderByIdAsc(20L)).thenReturn(List.of(firstDropItem, secondDropItem));
		when(bossItemAcquisitionRepository.findByCharacter_IdAndBossDropItem_IdInOrderByAcquiredDateDescIdDesc(10L, List.of(100L, 101L)))
				.thenReturn(List.of(acquisition));

		List<BossDropItemAcquisitionStatusResponse> response = bossDropService.getAcquisitionStatus(1L, 20L, 10L);

		assertThat(response).hasSize(2);
		assertThat(response.get(0).acquired()).isTrue();
		assertThat(response.get(0).acquisitions()).extracting(BossItemAcquisitionResponse::id).containsExactly(1000L);
		assertThat(response.get(1).acquired()).isFalse();
		assertThat(response.get(1).acquisitions()).isEmpty();
	}

	@Test
	void createAcquisitionSavesBossItemAcquisition() {
		User user = user();
		MapleCharacter character = character(user);
		BossDropItem dropItem = dropItem(100L, boss(20L), "아케인셰이드 무기 상자");
		BossItemAcquisitionCreateRequest request = new BossItemAcquisitionCreateRequest(
				10L,
				100L,
				LocalDate.parse("2026-07-14"),
				"첫 획득"
		);
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		when(bossDropItemRepository.findById(100L)).thenReturn(Optional.of(dropItem));
		when(bossItemAcquisitionRepository.save(any(BossItemAcquisition.class))).thenAnswer(invocation -> {
			BossItemAcquisition saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 1000L);
			return saved;
		});

		BossItemAcquisitionResponse response = bossDropService.createAcquisition(1L, request);

		assertThat(response.id()).isEqualTo(1000L);
		assertThat(response.characterId()).isEqualTo(10L);
		assertThat(response.bossDropItemId()).isEqualTo(100L);
		assertThat(response.acquiredDate()).isEqualTo(LocalDate.parse("2026-07-14"));
		assertThat(response.memo()).isEqualTo("첫 획득");
	}

	@Test
	void createAcquisitionRejectsOtherUserCharacter() {
		BossItemAcquisitionCreateRequest request = new BossItemAcquisitionCreateRequest(
				10L,
				100L,
				LocalDate.parse("2026-07-14"),
				null
		);
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> bossDropService.createAcquisition(1L, request))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("캐릭터 없음");
	}

	@Test
	void deleteAcquisitionDeletesOwnedAcquisition() {
		User user = user();
		MapleCharacter character = character(user);
		BossItemAcquisition acquisition = acquisition(1000L, character, dropItem(100L, boss(20L), "아케인셰이드 무기 상자"), LocalDate.parse("2026-07-14"));
		when(bossItemAcquisitionRepository.findByIdAndCharacter_User_Id(1000L, 1L)).thenReturn(Optional.of(acquisition));

		bossDropService.deleteAcquisition(1L, 1000L);

		verify(bossItemAcquisitionRepository).delete(acquisition);
	}

	@Test
	void deleteAcquisitionRejectsOtherUserAcquisition() {
		when(bossItemAcquisitionRepository.findByIdAndCharacter_User_Id(1000L, 1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> bossDropService.deleteAcquisition(1L, 1000L))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("보스 드랍 획득 기록 없음");
	}

	private User user() {
		User user = User.create(OAuthProvider.KAKAO, "oauth-id", "user@example.com", "nickname");
		ReflectionTestUtils.setField(user, "id", 1L);
		return user;
	}

	private MapleCharacter character(User user) {
		MapleCharacter character = MapleCharacter.create(user, "ocid", "캐릭터", "스카니아", "히어로", 280, 1);
		ReflectionTestUtils.setField(character, "id", 10L);
		return character;
	}

	private BossMaster boss(Long id) {
		BossMaster boss = newEntity(BossMaster.class);
		ReflectionTestUtils.setField(boss, "id", id);
		ReflectionTestUtils.setField(boss, "bossName", "스우");
		ReflectionTestUtils.setField(boss, "difficulty", Difficulty.HARD);
		ReflectionTestUtils.setField(boss, "resetPeriod", ResetPeriod.WEEKLY);
		ReflectionTestUtils.setField(boss, "sortOrder", 1);
		ReflectionTestUtils.setField(boss, "active", true);
		return boss;
	}

	private BossDropItem dropItem(Long id, BossMaster boss, String itemName) {
		BossDropItem dropItem = newEntity(BossDropItem.class);
		ReflectionTestUtils.setField(dropItem, "id", id);
		ReflectionTestUtils.setField(dropItem, "boss", boss);
		ReflectionTestUtils.setField(dropItem, "itemName", itemName);
		ReflectionTestUtils.setField(dropItem, "itemImage", "https://example.com/item.png");
		ReflectionTestUtils.setField(dropItem, "itemDescription", "드랍 아이템");
		ReflectionTestUtils.setField(dropItem, "dropRateTier", DropRateTier.LOW);
		return dropItem;
	}

	private BossItemAcquisition acquisition(
			Long id,
			MapleCharacter character,
			BossDropItem dropItem,
			LocalDate acquiredDate
	) {
		BossItemAcquisition acquisition = BossItemAcquisition.create(character, dropItem, acquiredDate, "메모");
		ReflectionTestUtils.setField(acquisition, "id", id);
		return acquisition;
	}

	private <T> T newEntity(Class<T> type) {
		try {
			Constructor<T> constructor = type.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
