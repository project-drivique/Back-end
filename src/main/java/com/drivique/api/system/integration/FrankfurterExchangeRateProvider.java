package com.drivique.api.system.integration;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FrankfurterExchangeRateProvider implements ExchangeRateProvider {
    private final RestClient client;

    public FrankfurterExchangeRateProvider(
            @Value("${catalog.exchange-rates.base-url:https://api.frankfurter.dev}") String baseUrl,
            @Value("${catalog.exchange-rates.timeout-ms:5000}") int timeoutMs) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public String name() { return "Frankfurter v2"; }

    @Override
    public List<Quote> fetch(List<String> currencyCodes) {
        List<String> codes = currencyCodes.stream().map(String::trim).sorted().toList();
        if (codes.size() < 2) throw new IllegalStateException("At least two active currencies required");
        List<Quote> result = new ArrayList<>();
        Instant fetchedAt = Instant.now();
        for (String base : codes) {
            Set<String> expected = new HashSet<>(codes);
            expected.remove(base);
            Rate[] rows = client.get().uri(uri -> uri.path("/v2/rates")
                    .queryParam("base", base).queryParam("quotes", String.join(",", new TreeSet<>(expected)))
                    .build()).retrieve().body(Rate[].class);
            if (rows == null) throw new IllegalStateException("Empty response");
            for (Rate row : rows) {
                if (row == null || !base.equals(row.base()) || !expected.remove(row.quote())
                        || row.date() == null || row.date().isAfter(LocalDate.now(ZoneOffset.UTC))
                        || row.rate() == null || row.rate().signum() <= 0)
                    throw new IllegalStateException("Invalid provider response");
                result.add(new Quote(base, row.quote(), row.rate(), fetchedAt));
            }
            if (!expected.isEmpty()) throw new IllegalStateException("Incomplete provider response");
        }
        return List.copyOf(result);
    }

    public record Rate(LocalDate date, String base, String quote, BigDecimal rate) {}
}
