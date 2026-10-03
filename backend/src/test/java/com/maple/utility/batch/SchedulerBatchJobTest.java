package com.maple.utility.batch;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.service.SyncJobService;

@ExtendWith(MockitoExtension.class)
class SchedulerBatchJobTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private SyncJobService syncJobService;

	private SchedulerBatchJob schedulerBatchJob;

	@BeforeEach
	void setUp() {
		schedulerBatchJob = new SchedulerBatchJob(userRepository, syncJobService);
	}

	@Test
	void syncDailySchedulerSyncsFavoriteCharactersByUser() {
		User firstUser = user(1L);
		User secondUser = user(2L);
		when(userRepository.findAll()).thenReturn(List.of(firstUser, secondUser));

		schedulerBatchJob.syncDailyScheduler();

		verify(syncJobService).startScheduler(1L, true, true);
		verify(syncJobService).startScheduler(2L, true, true);
	}

	private User user(Long id) {
		User user = User.create(OAuthProvider.KAKAO, "oauth-id-" + id, "user" + id + "@example.com", "nickname");
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

}
