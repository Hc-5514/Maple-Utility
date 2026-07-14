package com.maple.utility;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.maple.utility.repository.BossDropItemRepository;
import com.maple.utility.repository.BossItemAcquisitionRepository;
import com.maple.utility.repository.BossMasterRepository;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.DataSyncLogRepository;
import com.maple.utility.repository.SchedulerBossRecordRepository;
import com.maple.utility.repository.SchedulerDailyRecordRepository;
import com.maple.utility.repository.SchedulerWeeklyRecordRepository;
import com.maple.utility.repository.UserApiKeyRepository;
import com.maple.utility.repository.UserRepository;

@SpringBootTest(properties = {
		"JWT_SECRET=test-jwt-secret-for-context-load",
		"KAKAO_CLIENT_ID=test-kakao-client-id",
		"KAKAO_CLIENT_SECRET=test-kakao-client-secret",
		"KAKAO_REDIRECT_URI=http://localhost/oauth/kakao/callback",
		"NEXON_CLIENT_ID=test-nexon-client-id",
		"NEXON_CLIENT_SECRET=test-nexon-client-secret",
		"NEXON_REDIRECT_URI=http://localhost/oauth/nexon/callback",
		"NEXON_TOKEN_URI=http://localhost/oauth/nexon/token",
		"NEXON_USER_INFO_URI=http://localhost/oauth/nexon/user",
		"NEXON_API_KEY_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
})
class MapleUtilityApplicationTests {

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private UserApiKeyRepository userApiKeyRepository;

	@MockitoBean
	private CharacterRepository characterRepository;

	@MockitoBean
	private DataSyncLogRepository dataSyncLogRepository;

	@MockitoBean
	private BossMasterRepository bossMasterRepository;

	@MockitoBean
	private BossDropItemRepository bossDropItemRepository;

	@MockitoBean
	private BossItemAcquisitionRepository bossItemAcquisitionRepository;

	@MockitoBean
	private SchedulerDailyRecordRepository schedulerDailyRecordRepository;

	@MockitoBean
	private SchedulerWeeklyRecordRepository schedulerWeeklyRecordRepository;

	@MockitoBean
	private SchedulerBossRecordRepository schedulerBossRecordRepository;

	@Test
	void contextLoads() {
	}

}
