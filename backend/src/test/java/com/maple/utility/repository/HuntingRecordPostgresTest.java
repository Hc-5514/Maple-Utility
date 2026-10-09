package com.maple.utility.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Sort;

import com.maple.utility.entity.HuntingRecord;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;

import jakarta.persistence.EntityManager;

@EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = ".+")
@DataJpaTest(properties = {
		"spring.datasource.url=${FLYWAY_TEST_URL}",
		"spring.datasource.username=${FLYWAY_TEST_USER}",
		"spring.datasource.password=${FLYWAY_TEST_PASSWORD}",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.test.database.replace=none"
})
class HuntingRecordPostgresTest {

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private HuntingRecordRepository repository;

	private Long userId;
	private Long firstFavoriteCharacterId;
	private Long firstRecordId;
	private Long latestRecordId;

	@BeforeEach
	void setUp() {
		String suffix = UUID.randomUUID().toString();
		User user = User.create(OAuthProvider.KAKAO, "hunting-user-" + suffix, null, "사냥 사용자");
		entityManager.persist(user);
		userId = user.getId();

		MapleCharacter firstFavorite = favoriteCharacter(user, "first-" + suffix, "첫 즐겨찾기");
		MapleCharacter secondFavorite = favoriteCharacter(user, "second-" + suffix, "둘째 즐겨찾기");
		MapleCharacter unfavorite = MapleCharacter.create(user, "unfavorite-" + suffix, "즐겨찾기 해제", "엘리시움", "히어로", 280, 2);
		entityManager.persist(firstFavorite);
		entityManager.persist(secondFavorite);
		entityManager.persist(unfavorite);

		User otherUser = User.create(OAuthProvider.KAKAO, "other-user-" + suffix, null, "다른 사용자");
		entityManager.persist(otherUser);
		MapleCharacter otherFavorite = favoriteCharacter(otherUser, "other-" + suffix, "다른 사용자 즐겨찾기");
		entityManager.persist(otherFavorite);

		HuntingRecord first = record(firstFavorite, "2026-10-01");
		HuntingRecord latest = record(secondFavorite, "2026-10-03");
		entityManager.persist(first);
		entityManager.persist(latest);
		entityManager.persist(record(unfavorite, "2026-10-02"));
		entityManager.persist(record(otherFavorite, "2026-10-02"));
		entityManager.flush();
		firstFavoriteCharacterId = firstFavorite.getId();
		firstRecordId = first.getId();
		latestRecordId = latest.getId();
		entityManager.clear();
	}

	@Test
	void optionalDateConditionsRunAgainstPostgresWithoutUntypedNullParameters() {
		Sort sort = Sort.by(Sort.Order.desc("recordDate"), Sort.Order.desc("id"));

		assertThat(ids(null, null, sort)).containsExactly(latestRecordId, firstRecordId);
		assertThat(ids(LocalDate.parse("2026-10-02"), null, sort)).containsExactly(latestRecordId);
		assertThat(ids(null, LocalDate.parse("2026-10-02"), sort)).containsExactly(firstRecordId);
		assertThat(ids(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-01"), sort)).containsExactly(firstRecordId);
		assertThat(repository.findAll(HuntingRecordSpecifications.characterRecords(firstFavoriteCharacterId, null, null), sort))
				.extracting(HuntingRecord::getId)
				.containsExactly(firstRecordId);
	}

	private List<Long> ids(LocalDate from, LocalDate to, Sort sort) {
		return repository.findAll(HuntingRecordSpecifications.favoriteRecords(userId, from, to), sort).stream()
				.map(HuntingRecord::getId)
				.toList();
	}

	private MapleCharacter favoriteCharacter(User user, String ocid, String name) {
		MapleCharacter character = MapleCharacter.create(user, ocid, name, "엘리시움", "히어로", 280, 0);
		character.toggleFavorite();
		return character;
	}

	private HuntingRecord record(MapleCharacter character, String date) {
		return HuntingRecord.create(character, LocalDate.parse(date), 1_000_000L, 1, null, "사냥터", null, null);
	}
}
