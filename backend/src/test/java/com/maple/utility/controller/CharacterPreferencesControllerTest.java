package com.maple.utility.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.maple.utility.dto.request.CharacterPreferencesRequest.Preference;
import com.maple.utility.exception.GlobalExceptionHandler;
import com.maple.utility.security.JwtAuthentication;
import com.maple.utility.service.CharacterService;
import com.maple.utility.service.SyncJobService;

@ExtendWith(MockitoExtension.class)
class CharacterPreferencesControllerTest {
	@Mock CharacterService service;
	@Mock SyncJobService syncJobs;
	private MockMvc mvc;
	private final UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(new JwtAuthentication(7L), null, List.of());

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new CharacterController(service, syncJobs))
				.setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	@Test
	void orderedPreferencesUseAuthenticatedUserId() throws Exception {
		List<Preference> items = List.of(new Preference(20L, false), new Preference(10L, true));
		when(service.updatePreferences(7L, items)).thenReturn(List.of());
		mvc.perform(put("/api/v1/characters/preferences").principal(authentication)
				.contentType("application/json")
				.content("{\"characters\":[{\"characterId\":20,\"favorite\":false},{\"characterId\":10,\"favorite\":true}]}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
		verify(service).updatePreferences(7L, items);
	}

	@Test
	void missingOrInvalidValuesReturnStructuredBadRequest() throws Exception {
		for (String body : List.of("{}", "{\"characters\":null}", "{\"characters\":[null]}",
				"{\"characters\":[{\"favorite\":true}]}", "{\"characters\":[{\"characterId\":1}]}",
				"{\"characters\":[{\"characterId\":-1,\"favorite\":true}]}")) {
			mvc.perform(put("/api/v1/characters/preferences").principal(authentication)
					.contentType("application/json").content(body))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
					.andExpect(jsonPath("$.timestamp").exists());
		}
		verifyNoInteractions(service);
	}

	@Test
	void malformedJsonReturnsStructuredBadRequest() throws Exception {
		mvc.perform(put("/api/v1/characters/preferences").principal(authentication)
				.contentType("application/json").content("{\"characters\":oops}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
		verifyNoInteractions(service);
	}
}
