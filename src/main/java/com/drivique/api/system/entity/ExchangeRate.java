package com.drivique.api.system.entity;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "exchange_rates", schema = "core", uniqueConstraints = @UniqueConstraint(columnNames = {"from_currency_id", "to_currency_id", "fetched_at"}))
public class ExchangeRate {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "from_currency_id")
    private Currency fromCurrency;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "to_currency_id")
    private Currency toCurrency;
    @Column(nullable = false, precision = 16, scale = 6)
    private BigDecimal rate;
    @Column(nullable = false, length = 80)
    private String provider;
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
    protected ExchangeRate() {}
    public UUID getId() { return id; }
    public Currency getFromCurrency() { return fromCurrency; }
    public Currency getToCurrency() { return toCurrency; }
    public BigDecimal getRate() { return rate; }
    public String getProvider() { return provider; }
    public Instant getFetchedAt() { return fetchedAt; }
    public ExchangeRate(Currency from, Currency to, BigDecimal rate, String provider, Instant fetchedAt) {
        this.fromCurrency = from; this.toCurrency = to; this.rate = rate; this.provider = provider; this.fetchedAt = fetchedAt;
    }
}
