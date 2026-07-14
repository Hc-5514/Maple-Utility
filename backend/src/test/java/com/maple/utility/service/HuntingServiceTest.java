package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.maple.utility.dto.request.HuntingRecordRequest;
import com.maple.utility.dto.response.HuntingRecordResponse;
import com.maple.utility.entity.HuntingRecord;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.HuntingRecordRepository;

@ExtendWith(MockitoExtension.class)
class HuntingServiceTest {

	@Mock
	private HuntingRecordRepository huntingRecordRepository;

	@Mock
	private CharacterRepository characterRepository;

	private HuntingService huntingService;

	@BeforeEach
	void setUp() {
		huntingService = new HuntingService(huntingRecordRepository, characterRepository);
	}

	@Test
	void getRecordsReturnsOwnedCharacterRecords() {
		User user = user();
		MapleCharacter character = character(user);
		HuntingRecord record = huntingRecord(100L, character, LocalDate.parse("2026-07-14"));
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		when(huntingRecordRepository.findByCharacterIdAndRecordDateRange(
				10L,
				LocalDate.parse("2026-07-01"),
				LocalDate.parse("2026-07-31")
		)).thenReturn(List.of(record));

		List<HuntingRecordResponse> response = huntingService.getRecords(
				1L,
				10L,
				LocalDate.parse("2026-07-01"),
				LocalDate.parse("2026-07-31")
		);

		assertThat(response).hasSize(1);
		assertThat(response.getFirst().id()).isEqualTo(100L);
		assertThat(response.getFirst().mesoEarned()).isEqualTo(10_000_000L);
	}

	@Test
	void getRecordsRejectsInvalidDateRange() {
		User user = user();
		MapleCharacter character = character(user);
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));

		assertThatThrownBy(() -> huntingService.getRecords(
				1L,
				10L,
				LocalDate.parse("2026-07-31"),
				LocalDate.parse("2026-07-01")
		))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("조회 기간 오류");
	}

	@Test
	void createRecordSavesHuntingRecord() {
		User user = user();
		MapleCharacter character = character(user);
		HuntingRecordRequest request = request(LocalDate.parse("2026-07-14"), 10_000_000L, 12);
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		when(huntingRecordRepository.existsByCharacter_IdAndRecordDate(10L, LocalDate.parse("2026-07-14")))
				.thenReturn(false);
		when(huntingRecordRepository.save(any(HuntingRecord.class))).thenAnswer(invocation -> {
			HuntingRecord saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 100L);
			return saved;
		});

		HuntingRecordResponse response = huntingService.createRecord(1L, request);

		assertThat(response.id()).isEqualTo(100L);
		assertThat(response.characterId()).isEqualTo(10L);
		assertThat(response.recordDate()).isEqualTo(LocalDate.parse("2026-07-14"));
		assertThat(response.mesoEarned()).isEqualTo(10_000_000L);
		assertThat(response.solErdaEarned()).isEqualTo(12);
	}

	@Test
	void createRecordRejectsEmptyReward() {
		User user = user();
		MapleCharacter character = character(user);
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));

		assertThatThrownBy(() -> huntingService.createRecord(
				1L,
				request(LocalDate.parse("2026-07-14"), null, null)
		))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("사냥 보상 필요");
	}

	@Test
	void createRecordRejectsDuplicateDate() {
		User user = user();
		MapleCharacter character = character(user);
		HuntingRecordRequest request = request(LocalDate.parse("2026-07-14"), 10_000_000L, null);
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		when(huntingRecordRepository.existsByCharacter_IdAndRecordDate(10L, LocalDate.parse("2026-07-14")))
				.thenReturn(true);

		assertThatThrownBy(() -> huntingService.createRecord(1L, request))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("사냥 기록 중복");
	}

	@Test
	void updateRecordUpdatesOwnedRecord() {
		User user = user();
		MapleCharacter character = character(user);
		HuntingRecord record = huntingRecord(100L, character, LocalDate.parse("2026-07-14"));
		HuntingRecordRequest request = request(LocalDate.parse("2026-07-15"), 20_000_000L, 3);
		when(huntingRecordRepository.findByIdAndCharacter_User_Id(100L, 1L)).thenReturn(Optional.of(record));
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		when(huntingRecordRepository.existsByCharacter_IdAndRecordDateAndIdNot(10L, LocalDate.parse("2026-07-15"), 100L))
				.thenReturn(false);

		HuntingRecordResponse response = huntingService.updateRecord(1L, 100L, request);

		assertThat(response.recordDate()).isEqualTo(LocalDate.parse("2026-07-15"));
		assertThat(response.mesoEarned()).isEqualTo(20_000_000L);
		assertThat(response.solErdaEarned()).isEqualTo(3);
	}

	@Test
	void updateRecordRejectsDuplicateDate() {
		User user = user();
		MapleCharacter character = character(user);
		HuntingRecord record = huntingRecord(100L, character, LocalDate.parse("2026-07-14"));
		HuntingRecordRequest request = request(LocalDate.parse("2026-07-15"), 20_000_000L, 3);
		when(huntingRecordRepository.findByIdAndCharacter_User_Id(100L, 1L)).thenReturn(Optional.of(record));
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		when(huntingRecordRepository.existsByCharacter_IdAndRecordDateAndIdNot(10L, LocalDate.parse("2026-07-15"), 100L))
				.thenReturn(true);

		assertThatThrownBy(() -> huntingService.updateRecord(1L, 100L, request))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("사냥 기록 중복");
	}

	@Test
	void updateRecordRejectsOtherUserRecord() {
		when(huntingRecordRepository.findByIdAndCharacter_User_Id(100L, 1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> huntingService.updateRecord(
				1L,
				100L,
				request(LocalDate.parse("2026-07-15"), 20_000_000L, 3)
		))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("사냥 기록 없음");
	}

	@Test
	void deleteRecordDeletesOwnedRecord() {
		User user = user();
		MapleCharacter character = character(user);
		HuntingRecord record = huntingRecord(100L, character, LocalDate.parse("2026-07-14"));
		when(huntingRecordRepository.findByIdAndCharacter_User_Id(100L, 1L)).thenReturn(Optional.of(record));

		huntingService.deleteRecord(1L, 100L);

		verify(huntingRecordRepository).delete(record);
	}

	private HuntingRecordRequest request(LocalDate recordDate, Long mesoEarned, Integer solErdaEarned) {
		return new HuntingRecordRequest(
				10L,
				recordDate,
				mesoEarned,
				solErdaEarned,
				60,
				"세르니움",
				"메모"
		);
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

	private HuntingRecord huntingRecord(Long id, MapleCharacter character, LocalDate recordDate) {
		HuntingRecord record = HuntingRecord.create(
				character,
				recordDate,
				10_000_000L,
				12,
				60,
				"세르니움",
				"메모"
		);
		ReflectionTestUtils.setField(record, "id", id);
		return record;
	}
}
