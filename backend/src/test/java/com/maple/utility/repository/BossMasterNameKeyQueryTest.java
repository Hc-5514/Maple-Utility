package com.maple.utility.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

import com.maple.utility.entity.BossMaster;
import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.service.BossNames;

import jakarta.persistence.EntityManager;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class BossMasterNameKeyQueryTest {

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private BossMasterRepository repository;

	@Test
	void findsBossIgnoringSpacesInEitherName() {
		for (String column : List.of("created_at", "updated_at")) {
			entityManager.createNativeQuery("ALTER TABLE boss_master ALTER COLUMN " + column
					+ " SET DEFAULT CURRENT_TIMESTAMP").executeUpdate();
		}
		boss("블러디 퀸", Difficulty.CHAOS);
		boss("가엔슬", Difficulty.CHAOS);
		boss("시즌 보스 메이린", Difficulty.HARD);
		boss("스우", Difficulty.HARD);
		entityManager.flush();
		entityManager.clear();

		// 넥슨은 띄어쓰기 없이 보내는 경우가 있어 표기가 달라도 같은 보스로 매칭
		assertThat(repository.findByNameKeyAndDifficulty(BossNames.lookupKey("블러디퀸"), Difficulty.CHAOS))
				.get().extracting(BossMaster::getBossName).isEqualTo("블러디 퀸");
		assertThat(repository.findByNameKeyAndDifficulty(BossNames.lookupKey("블러디 퀸"), Difficulty.CHAOS)).isPresent();
		assertThat(repository.findByNameKeyAndDifficulty(BossNames.lookupKey("가디언 엔젤 슬라임"), Difficulty.CHAOS))
				.get().extracting(BossMaster::getBossName).isEqualTo("가엔슬");
		assertThat(repository.findByNameKeyAndDifficulty(BossNames.lookupKey("시즌보스메이린"), Difficulty.HARD)).isPresent();
		assertThat(repository.findByNameKeyAndDifficulty(BossNames.lookupKey("스우"), Difficulty.HARD)).isPresent();
		// 난이도가 다르거나 없는 보스는 매칭되지 않음
		assertThat(repository.findByNameKeyAndDifficulty(BossNames.lookupKey("블러디퀸"), Difficulty.HARD)).isEmpty();
		assertThat(repository.findByNameKeyAndDifficulty(BossNames.lookupKey("없는 보스"), Difficulty.HARD)).isEmpty();
	}

	private void boss(String name, Difficulty difficulty) {
		BossMaster boss = BeanUtils.instantiateClass(BossMaster.class);
		ReflectionTestUtils.setField(boss, "bossName", name);
		ReflectionTestUtils.setField(boss, "difficulty", difficulty);
		ReflectionTestUtils.setField(boss, "resetPeriod", ResetPeriod.WEEKLY);
		entityManager.persist(boss);
	}
}
