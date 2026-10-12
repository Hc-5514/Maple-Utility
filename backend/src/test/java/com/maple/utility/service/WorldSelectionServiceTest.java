package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;
import com.maple.utility.entity.UserSelectedWorld;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.repository.UserSelectedWorldRepository;

@ExtendWith(MockitoExtension.class)
class WorldSelectionServiceTest {
	@Mock UserRepository users;
	@Mock UserSelectedWorldRepository selections;
	private WorldSelectionService service;
	private final User user = User.create(OAuthProvider.KAKAO, "world-user", null, "월드 사용자");

	@BeforeEach
	void setUp() {
		service = new WorldSelectionService(users, selections);
	}

	@Test
	void newUserStartsWithNoWorldsAndQueryUsesAuthenticatedUserId() {
		when(users.findById(2L)).thenReturn(Optional.of(user));
		assertThat(service.getSelection(2L).worldNames()).isEmpty();
		verify(selections).findByUser_Id(2L);
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void savesMultipleWorldsInCanonicalOrderWithOwnerLock() {
		when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
		assertThat(service.saveSelection(1L, List.of("헬리오스", "루나", "스카니아")).worldNames())
				.containsExactly("스카니아", "루나", "헬리오스");
		var order = org.mockito.Mockito.inOrder(users, selections);
		order.verify(users).findByIdForUpdate(1L);
		order.verify(selections).deleteAllByUserId(1L);
		ArgumentCaptor<List<UserSelectedWorld>> saved = ArgumentCaptor.forClass((Class) List.class);
		order.verify(selections).saveAll(saved.capture());
		assertThat(saved.getValue()).allSatisfy(world -> assertThat(world.getUser()).isSameAs(user));
	}

	@Test
	void emptySelectionRemovesAllSavedWorlds() {
		when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
		assertThat(service.saveSelection(1L, List.of()).worldNames()).isEmpty();
		verify(selections).deleteAllByUserId(1L);
		verify(selections).saveAll(List.of());
	}

	@Test
	void rejectsDuplicateInvalidAndNullValuesBeforeWriting() {
		for (List<String> invalid : List.of(List.of("루나", "루나"), List.of("알 수 없음"), Arrays.asList((String) null))) {
			assertThatThrownBy(() -> service.saveSelection(1L, invalid)).isInstanceOf(ApiException.class);
		}
		assertThatThrownBy(() -> service.saveSelection(1L, null)).isInstanceOf(ApiException.class);
		verifyNoInteractions(users, selections);
	}

	@Test
	void missingUserCannotSaveSelection() {
		assertThatThrownBy(() -> service.saveSelection(99L, List.of("루나")))
				.isInstanceOf(ApiException.class).hasMessage("사용자 없음");
		verifyNoInteractions(selections);
	}
}
