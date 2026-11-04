package com.maple.utility.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import com.maple.utility.config.QuerydslConfig;
import com.maple.utility.dto.response.StatsBossItemResponse;
import com.maple.utility.entity.BossDropItem;
import com.maple.utility.entity.BossItemAcquisition;
import com.maple.utility.entity.BossItemKind;
import com.maple.utility.entity.BossMaster;
import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.entity.User;

import jakarta.persistence.EntityManager;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({QuerydslConfig.class, StatsQueryRepository.class})
class StatsDetailItemLabelTest {

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private StatsQueryRepository repository;

	@Test
	void historyShowsBoxNameWithDetailItemInParentheses() {
		for (String table : List.of("users", "characters", "boss_master", "boss_drop_items", "boss_item_acquisitions")) {
			for (String column : List.of("created_at", "updated_at")) {
				entityManager.createNativeQuery("ALTER TABLE " + table + " ALTER COLUMN " + column
						+ " SET DEFAULT CURRENT_TIMESTAMP").executeUpdate();
			}
		}
		User user = User.create(OAuthProvider.KAKAO, "detail-user", null, "상세");
		entityManager.persist(user);
		MapleCharacter character = MapleCharacter.create(user, "ocid-detail", "꼬농", "엘리시움", "마법사", 294, 0);
		entityManager.persist(character);
		BossMaster boss = BeanUtils.instantiateClass(BossMaster.class);
		ReflectionTestUtils.setField(boss, "bossName", "카링");
		ReflectionTestUtils.setField(boss, "difficulty", Difficulty.HARD);
		ReflectionTestUtils.setField(boss, "resetPeriod", ResetPeriod.WEEKLY);
		entityManager.persist(boss);
		BossDropItem box = dropItem(boss, "혼돈의 칠흑 장신구 상자");
		BossDropItem plain = dropItem(boss, "생명의 연마석");
		BossItemAcquisition withDetail = BossItemAcquisition.create(character, box, LocalDate.parse("2026-10-08"), null);
		ReflectionTestUtils.setField(withDetail, "detailItem", "마력이 깃든 안대");
		BossItemAcquisition withoutDetail = BossItemAcquisition.create(character, box, LocalDate.parse("2026-10-01"), null);
		BossItemAcquisition blankDetail = BossItemAcquisition.create(character, plain, LocalDate.parse("2026-09-24"), null);
		ReflectionTestUtils.setField(blankDetail, "detailItem", " ");
		entityManager.persist(withDetail);
		entityManager.persist(withoutDetail);
		entityManager.persist(blankDetail);
		entityManager.flush();
		entityManager.clear();

		var page = repository.findBossItemStats(List.of(character.getId()), null, null, null, null, 0, 10);

		assertThat(page.content()).extracting(StatsBossItemResponse::itemName).containsExactly(
				"혼돈의 칠흑 장신구 상자 (마력이 깃든 안대)", "혼돈의 칠흑 장신구 상자", "생명의 연마석");
	}

	private BossDropItem dropItem(BossMaster boss, String itemName) {
		BossDropItem item = BeanUtils.instantiateClass(BossDropItem.class);
		ReflectionTestUtils.setField(item, "boss", boss);
		ReflectionTestUtils.setField(item, "itemName", itemName);
		ReflectionTestUtils.setField(item, "itemKind", BossItemKind.RANDOM);
		ReflectionTestUtils.setField(item, "defaultQuantity", 1);
		entityManager.persist(item);
		return item;
	}
}
