package com.cwnupaa.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HonorShowcaseServiceTests {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final HonorShowcaseService service = new HonorShowcaseService(db, new ObjectMapper());

    @Test void absenceMeansDefaultButSavedEmptyListHidesAll() {
        when(db.queryForList(anyString(), eq(String.class), eq(6L))).thenReturn(List.of());
        assertThat(service.read(6L).customized()).isFalse();
        when(db.queryForList(anyString(), eq(String.class), eq(6L))).thenReturn(List.of("[]"));
        assertThat(service.read(6L)).isEqualTo(new HonorShowcaseService.Preferences(true, List.of()));
        assertThat(service.save(6L, List.of(), Set.of()).customized()).isTrue();
        verify(db).update(contains("ON CONFLICT"), eq(6L), eq("[]"));
    }

    @Test void mixedSourcesKeepUserOrderThroughPersistence() {
        var keys = List.of("certificate-G-9", "record-18", "certificate-V-7");
        var result = service.save(6L, keys, Set.copyOf(keys));
        assertThat(result.selectedKeys()).containsExactlyElementsOf(keys);
        verify(db).update(contains("ON CONFLICT"), eq(6L), eq("[\"certificate-G-9\",\"record-18\",\"certificate-V-7\"]"));
        when(db.queryForList(anyString(), eq(String.class), eq(6L)))
                .thenReturn(List.of("[\"certificate-G-9\",\"record-18\",\"certificate-V-7\"]"));
        assertThat(service.read(6L)).isEqualTo(result);
    }

    @Test void rejectsUnownedRevokedMalformedAndDuplicateEntriesBeforeWriting() {
        for (List<String> keys : List.of(List.of("record-99"), List.of("certificate-G-99"),
                List.of("record-1", "record-1"), List.of("record--1"), List.of("record-0"), Arrays.asList((String)null))) {
            assertThatThrownBy(() -> service.save(6L, keys, Set.of("record-1"))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service.save(6L, null, Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(6L, Collections.nCopies(1001, "record-1"), Set.of("record-1"))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(db);
    }

    @Test void resetDeletesOnlyOwnersPreference() {
        assertThat(service.reset(6L).customized()).isFalse();
        verify(db).update("DELETE FROM besti_honor_showcase WHERE user_id=?", 6L);
        verifyNoMoreInteractions(db);
    }

    @Test void corruptConfigurationDoesNotAccidentallyRevealAllHonors() {
        when(db.queryForList(anyString(), eq(String.class), eq(6L))).thenReturn(List.of("bad-json"));
        assertThatThrownBy(() -> service.read(6L)).isInstanceOf(IllegalStateException.class);
    }
}
