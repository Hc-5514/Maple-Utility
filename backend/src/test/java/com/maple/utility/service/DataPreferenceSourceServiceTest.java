package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.maple.utility.service.DataPreferenceSourceService.PreferenceSource;

@ExtendWith(MockitoExtension.class)
class DataPreferenceSourceServiceTest {
    @Mock JdbcTemplate jdbc;

    @ParameterizedTest
    @CsvSource({"EXPLICIT,LEGACY,true", "EXPLICIT,UNKNOWN,true", "LEGACY,UNKNOWN,true",
        "UNKNOWN,LEGACY,false", "LEGACY,EXPLICIT,false", "UNKNOWN,EXPLICIT,false"})
    void intentStateDeterminesPriorityBeforeProviderOrSaveTime(String candidate, String existing, boolean preferred) {
        var service = new DataPreferenceSourceService(jdbc);
        var later = new PreferenceSource(101, candidate, LocalDateTime.of(2026, 10, 16, 0, 0));
        var earlier = new PreferenceSource(102, existing, LocalDateTime.of(2026, 10, 15, 23, 59, 59));

        assertThat(service.prefer(later, earlier)).isEqualTo(preferred);
        verifyNoInteractions(jdbc);
    }

    @Test
    void explicitSaveRecordsActualActorUsingDatabaseTimestampInsteadOfOwner() {
        var service = new DataPreferenceSourceService(jdbc);

        service.saveExplicit("FAVORITE", 301, 999);

        verify(jdbc).update(contains("'EXPLICIT', CURRENT_TIMESTAMP"), eq("FAVORITE"), eq(301L), eq(999L));
    }
}
