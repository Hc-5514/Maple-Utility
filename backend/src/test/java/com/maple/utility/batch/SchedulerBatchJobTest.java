package com.maple.utility.batch;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.service.SchedulerSyncService;

@ExtendWith(MockitoExtension.class)
class SchedulerBatchJobTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private CharacterRepository characterRepository;

	@Mock
	private SchedulerSyncService schedulerSyncService;

	private SchedulerBatchJob schedulerBatchJob;

	@BeforeEach
	void setUp() {
		schedulerBatchJob = new SchedulerBatchJob(userRepository, characterRepository, schedulerSyncService);
	}

	@Test
	void syncDailySchedulerSyncsFavoriteCharactersByUser() {
		User firstUser = user(1L);
		User secondUser = user(2L);
		MapleCharacter favorite = character(firstUser, 10L);
		when(userRepository.findAll()).thenReturn(List.of(firstUser, secondUser));
		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(favorite));
		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(2L)).thenReturn(List.of());

		schedulerBatchJob.syncDailyScheduler();

		verify(schedulerSyncService).syncCharactersForBatch(1L, List.of(favorite));
		verify(schedulerSyncService, never()).syncCharactersForBatch(2L, List.of());
	}

	private User user(Long id) {
		User user = User.create(OAuthProvider.KAKAO, "oauth-id-" + id, "user" + id + "@example.com", "nickname");
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private MapleCharacter character(User user, Long id) {
		MapleCharacter character = MapleCharacter.create(user, "ocid-" + id, "캐릭터", "스카니아", "히어로", 280, 1);
		ReflectionTestUtils.setField(character, "id", id);
		return character;
	}
}
