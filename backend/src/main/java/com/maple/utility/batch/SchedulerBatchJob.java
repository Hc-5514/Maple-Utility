package com.maple.utility.batch;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.User;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.service.SchedulerSyncService;

@Component
public class SchedulerBatchJob {

	private final UserRepository userRepository;
	private final CharacterRepository characterRepository;
	private final SchedulerSyncService schedulerSyncService;

	public SchedulerBatchJob(
			UserRepository userRepository,
			CharacterRepository characterRepository,
			SchedulerSyncService schedulerSyncService
	) {
		this.userRepository = userRepository;
		this.characterRepository = characterRepository;
		this.schedulerSyncService = schedulerSyncService;
	}

	@Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
	public void syncDailyScheduler() {
		for (User user : userRepository.findAll()) {
			List<MapleCharacter> favoriteCharacters = characterRepository
					.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(user.getId());
			if (favoriteCharacters.isEmpty()) {
				continue;
			}
			schedulerSyncService.syncCharactersForBatch(user.getId(), favoriteCharacters);
		}
	}
}
