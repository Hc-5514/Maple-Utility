package com.maple.utility.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;
import com.maple.utility.exception.ApiException;
import com.maple.utility.service.WorldSelectionService;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(WorldSelectionService.class)
class WorldSelectionPersistenceTest {
	@org.springframework.test.context.bean.override.mockito.MockitoBean
	com.maple.utility.service.DataPreferenceSourceService preferenceSources;
	@Autowired EntityManager entityManager;
	@Autowired WorldSelectionService service;
	@Autowired UserSelectedWorldRepository selections;
	private User first;
	private User second;

	@BeforeEach
	void setUp() {
		for (String table : List.of("users", "user_selected_worlds")) {
			for (String column : List.of("created_at", "updated_at")) {
				entityManager.createNativeQuery("ALTER TABLE " + table + " ALTER COLUMN " + column
						+ " SET DEFAULT CURRENT_TIMESTAMP").executeUpdate();
			}
		}
		first = User.create(OAuthProvider.KAKAO, "first-world-user", null, "첫 사용자");
		second = User.create(OAuthProvider.KAKAO, "second-world-user", null, "둘째 사용자");
		entityManager.persist(first);
		entityManager.persist(second);
		entityManager.flush();
	}

	@Test
	void replaceRetainsOnlyRequestedWorldsAndKeepsOtherUsersIsolated() {
		assertThat(service.getSelection(first.getId()).worldNames()).isEmpty();
		service.saveSelection(first.getId(), List.of("스카니아", "루나"));
		service.saveSelection(second.getId(), List.of("베라"));
		service.saveSelection(first.getId(), List.of("루나", "헬리오스"));
		entityManager.flush();
		entityManager.clear();
		assertThat(service.getSelection(first.getId()).worldNames()).containsExactly("루나", "헬리오스");
		assertThat(service.getSelection(second.getId()).worldNames()).containsExactly("베라");
		service.saveSelection(first.getId(), List.of());
		entityManager.flush();
		assertThat(selections.findByUser_Id(first.getId())).isEmpty();
		assertThat(service.getSelection(second.getId()).worldNames()).containsExactly("베라");
	}

	@Test
	void rejectsInvalidReplacementBeforeDeletingExistingSelection() {
		service.saveSelection(first.getId(), List.of("루나"));
		assertThatThrownBy(() -> service.saveSelection(first.getId(), List.of("없는월드")))
				.isInstanceOf(ApiException.class);
		assertThat(selections.findByUser_Id(first.getId())).extracting(world -> world.getWorldName())
				.containsExactly("루나");
	}
}
