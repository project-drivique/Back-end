package com.drivique.api;

import com.drivique.api.catalog.ExchangeRateProvider;
import com.drivique.api.catalog.Language;
import com.drivique.api.catalog.LanguageRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class CatalogIntegrationTests extends DatabaseHealthTestSupport {
    @Autowired MockMvc mvc;
    @Autowired LanguageRepository languages;
    @Autowired CacheManager caches;
    @MockitoBean ExchangeRateProvider provider;

    @BeforeEach
    void resetCatalogs() {
        jdbc.update("delete from core.exchange_rates");
        jdbc.update("delete from core.currencies");
        languages.deleteAll();
        caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
        reset(provider);
    }

    @Test
    void publicCatalogsExposeOnlyActiveEntriesAndDtos() throws Exception {
        languages.saveAndFlush(new Language("es", "Español"));
        Language inactive = new Language("en", "English");
        inactive.toggleStatus();
        languages.saveAndFlush(inactive);
        currency("COP", true);
        currency("USD", false);
        mvc.perform(get("/api/v1/languages").contextPath("/api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("es"))
                .andExpect(jsonPath("$[0].isActive").value(true));
        mvc.perform(get("/api/v1/currencies").contextPath("/api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("COP"));
    }

    @Test
    void anonymousCannotWrite() throws Exception {
        mvc.perform(post("/api/v1/languages").contextPath("/api")
                .contentType("application/json").content("{\"code\":\"es\",\"name\":\"Español\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/languages/" + UUID.randomUUID() + "/toggle-status").contextPath("/api"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/exchange-rates/sync").contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotWrite() throws Exception {
        mvc.perform(post("/api/v1/languages").contextPath("/api")
                .contentType("application/json").content("{\"code\":\"es\",\"name\":\"Español\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/languages/" + UUID.randomUUID() + "/toggle-status").contextPath("/api"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/exchange-rates/sync").contextPath("/api"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(provider);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void adminCreatesLanguageAndRejectsDuplicatesAndInvalidCode() throws Exception {
        mvc.perform(post("/api/v1/languages").contextPath("/api")
                .contentType("application/json").content("{\"code\":\"es\",\"name\":\"Español\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isDefault").value(false));
        mvc.perform(post("/api/v1/languages").contextPath("/api")
                .contentType("application/json").content("{\"code\":\"es\",\"name\":\"Otro\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/languages").contextPath("/api")
                .contentType("application/json").content("{\"code\":\"ES\",\"name\":\"Otro\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/languages/" + UUID.randomUUID() + "/toggle-status").contextPath("/api"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void languageCacheIsUsedAndEvictedAfterToggle() throws Exception {
        Language saved = languages.saveAndFlush(new Language("es", "Español"));
        mvc.perform(get("/api/v1/languages").contextPath("/api"))
                .andExpect(jsonPath("$[0].name").value("Español"));
        jdbc.update("update core.languages set name = 'Spanish' where id = ?", saved.getId());
        mvc.perform(get("/api/v1/languages").contextPath("/api"))
                .andExpect(jsonPath("$[0].name").value("Español"));
        mvc.perform(patch("/api/v1/languages/" + saved.getId() + "/toggle-status").contextPath("/api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(false));
        mvc.perform(get("/api/v1/languages").contextPath("/api"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void syncStoresProviderRatesAndInvalidatesCache() throws Exception {
        currency("COP", true);
        currency("USD", true);
        mvc.perform(get("/api/v1/exchange-rates/latest").contextPath("/api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        when(provider.name()).thenReturn("TEST_ONLY");
        when(provider.fetch()).thenReturn(List.of(
                new ExchangeRateProvider.Quote("USD", "COP", new BigDecimal("4100.123456"), Instant.parse("2026-09-28T12:00:00Z")),
                new ExchangeRateProvider.Quote("USD", "COP", new BigDecimal("4200.654321"), Instant.parse("2026-09-28T13:00:00Z"))));
        mvc.perform(post("/api/v1/exchange-rates/sync").contextPath("/api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/exchange-rates/latest").contextPath("/api"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rate").value(4200.654321));
        jdbc.update("delete from core.exchange_rates");
        mvc.perform(get("/api/v1/exchange-rates/latest").contextPath("/api"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void invalidProviderResponseDoesNotWritePartialRates() throws Exception {
        currency("COP", true);
        currency("USD", true);
        when(provider.name()).thenReturn("TEST_ONLY");
        when(provider.fetch()).thenReturn(List.of(
                new ExchangeRateProvider.Quote("USD", "COP", BigDecimal.ONE, Instant.now()),
                new ExchangeRateProvider.Quote("COP", "USD", BigDecimal.ZERO, Instant.now())));
        mvc.perform(post("/api/v1/exchange-rates/sync").contextPath("/api"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        assertThat(jdbc.queryForObject("select count(*) from core.exchange_rates", Integer.class)).isZero();
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void providerFailurePreservesStoredRatesAndCache() throws Exception {
        currency("COP", true);
        currency("USD", true);
        when(provider.name()).thenReturn("Frankfurter v2");
        when(provider.fetch()).thenReturn(List.of(new ExchangeRateProvider.Quote(
                "USD", "COP", new BigDecimal("4200.654321"), Instant.now())));
        mvc.perform(post("/api/v1/exchange-rates/sync").contextPath("/api"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/exchange-rates/latest").contextPath("/api"))
                .andExpect(jsonPath("$[0].rate").value(4200.654321));
        when(provider.fetch()).thenThrow(new IllegalStateException("upstream unavailable"));
        assertThat(jdbc.queryForObject("select count(*) from core.exchange_rates", Integer.class)).isEqualTo(1);
        mvc.perform(post("/api/v1/exchange-rates/sync").contextPath("/api"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/v1/exchange-rates/latest").contextPath("/api"))
                .andExpect(jsonPath("$[0].rate").value(4200.654321));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void roundsProviderPrecisionToDatabaseScale() throws Exception {
        currency("COP", true);
        currency("USD", true);
        when(provider.name()).thenReturn("Frankfurter v2");
        when(provider.fetch()).thenReturn(List.of(new ExchangeRateProvider.Quote(
                "COP", "USD", new BigDecimal("0.000303237"), Instant.now())));
        mvc.perform(post("/api/v1/exchange-rates/sync").contextPath("/api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].rate").value(0.000303));
        assertThat(jdbc.queryForObject("select rate from core.exchange_rates", BigDecimal.class))
                .isEqualByComparingTo("0.000303");
    }
    private void currency(String code, boolean active) {
        jdbc.update("insert into core.currencies (id, code, name, symbol, is_default, is_active) values (?, ?, ?, ?, false, ?)",
                UUID.randomUUID(), code, code, "$", active);
    }
}
