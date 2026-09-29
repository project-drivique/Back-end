package com.drivique.api.system.integration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
/** Server-side adapter for an approved source. No client-supplied financial quotes. */
public interface ExchangeRateProvider {
    String name();
    List<Quote> fetch(List<String> currencyCodes);
    record Quote(String fromCurrency, String toCurrency, BigDecimal rate, Instant fetchedAt) {}
}
