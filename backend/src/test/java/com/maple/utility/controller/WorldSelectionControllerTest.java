package com.maple.utility.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.maple.utility.dto.response.WorldSelectionResponse;
import com.maple.utility.exception.GlobalExceptionHandler;
import com.maple.utility.security.JwtAuthentication;
import com.maple.utility.service.WorldSelectionService;

@ExtendWith(MockitoExtension.class)
class WorldSelectionControllerTest {
	@Mock WorldSelectionService service;
	private MockMvc mvc;
	private final UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(new JwtAuthentication(7L), null, List.of());

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new WorldSelectionController(service))
				.setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	@Test
	void readUsesJwtUserAndReturnsWorldNames() throws Exception {
		when(service.getSelection(7L)).thenReturn(new WorldSelectionResponse(List.of("루나")));
		mvc.perform(get("/api/v1/characters/world-selection").principal(authentication))
				.andExpect(status().isOk()).andExpect(jsonPath("$.worldNames[0]").value("루나"));
		verify(service).getSelection(7L);
	}

	@Test
	void writeAcceptsEmptySelection() throws Exception {
		when(service.saveSelection(7L, List.of())).thenReturn(new WorldSelectionResponse(List.of()));
		mvc.perform(put("/api/v1/characters/world-selection").principal(authentication)
						.contentType("application/json").content("{\"worldNames\":[]}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.worldNames").isEmpty());
		verify(service).saveSelection(7L, List.of());
	}

	@Test
	void missingOrBlankWorldNamesReturnStructuredValidationError() throws Exception {
		for (String body : List.of("{}", "{\"worldNames\":[\"\"]}", "{\"worldNames\":[null]}")) {
			mvc.perform(put("/api/v1/characters/world-selection").principal(authentication)
							.contentType("application/json").content(body))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
					.andExpect(jsonPath("$.timestamp").exists());
		}
		verifyNoInteractions(service);
	}
}
