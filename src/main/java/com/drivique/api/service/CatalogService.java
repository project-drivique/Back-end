package com.drivique.api.service;

import com.drivique.api.dto.CurrencyResponseDTO;
import com.drivique.api.dto.ExchangeRateResponseDTO;
import com.drivique.api.dto.LanguageRequestDTO;
import com.drivique.api.dto.LanguageResponseDTO;
import com.drivique.api.model.Currency;
import com.drivique.api.model.ExchangeRate;
import com.drivique.api.model.Language;
import com.drivique.api.integration.ExchangeRateProvider;
import com.drivique.api.mapper.CatalogMapper;
import com.drivique.api.repository.CurrencyRepository;
import com.drivique.api.repository.ExchangeRateRepository;
import com.drivique.api.repository.LanguageRepository;

import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import java.util.*;
import java.math.RoundingMode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatalogService {
    private final LanguageRepository languages;
    private final CurrencyRepository currencies;
    private final ExchangeRateRepository rates;
    private final ObjectProvider<ExchangeRateProvider> provider;

    public CatalogService(LanguageRepository languages, CurrencyRepository currencies,
            ExchangeRateRepository rates, ObjectProvider<ExchangeRateProvider> provider) {
        this.languages = languages; this.currencies = currencies; this.rates = rates; this.provider = provider;
    }

    @Transactional(readOnly = true)
    @Cacheable("activeLanguages")
    public List<LanguageResponseDTO> languages() {
        return languages.findByActiveTrueOrderByCodeAsc().stream().map(CatalogMapper::language).toList();
    }

    @Transactional(readOnly = true)
    public List<CurrencyResponseDTO> currencies() {
        return currencies.findByActiveTrueOrderByCodeAsc().stream().map(CatalogMapper::currency).toList();
    }

    @Transactional(readOnly = true)
    @Cacheable("latestRates")
    public List<ExchangeRateResponseDTO> latest() {
        return rates.findLatestForActivePairs().stream().map(CatalogMapper::rate).toList();
    }

    @Transactional
    @CacheEvict(value = "activeLanguages", allEntries = true)
    public LanguageResponseDTO create(LanguageRequestDTO input) {
        String name = input.name().strip();
        if (name.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (languages.existsByCodeOrName(input.code(), name))
            throw new ConflictException("Duplicate language");
        try {
            return CatalogMapper.language(languages.saveAndFlush(new Language(input.code(), name)));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Language conflicts with database constraints");
        }
    }

    @Transactional
    @CacheEvict(value = "activeLanguages", allEntries = true)
    public LanguageResponseDTO toggle(UUID id) {
        Language language = languages.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Language not found"));
        language.toggleStatus();
        return CatalogMapper.language(language);
    }

    @Transactional
    @CacheEvict(value = "latestRates", allEntries = true)
    public List<ExchangeRateResponseDTO> sync() {
        ExchangeRateProvider source = provider.getIfAvailable();
        if (source == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        Map<String, Currency> active = new HashMap<>();
        currencies.findByActiveTrueOrderByCodeAsc().forEach(c -> active.put(c.getCode().trim(), c));
        List<ExchangeRateProvider.Quote> quotes;
        try { quotes = source.fetch(active.keySet().stream().sorted().toList()); }
        catch (RuntimeException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE); }
        if (source.name() == null || source.name().isBlank() || source.name().length() > 80
                || quotes == null || quotes.isEmpty()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        List<ExchangeRate> imported = new ArrayList<>();
        for (var quote : quotes) {
            if (quote == null || !active.containsKey(quote.fromCurrency()) || !active.containsKey(quote.toCurrency())
                    || quote.fromCurrency().equals(quote.toCurrency()) || quote.rate() == null
                    || quote.rate().signum() <= 0 || quote.fetchedAt() == null)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
            java.math.BigDecimal value;
            try { value = quote.rate().setScale(6, RoundingMode.HALF_EVEN); }
            catch (ArithmeticException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE); }
            if (value.signum() <= 0 || value.precision() > 16) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
            imported.add(new ExchangeRate(active.get(quote.fromCurrency()), active.get(quote.toCurrency()),
                    value, source.name(), quote.fetchedAt()));
        }
        try { return rates.saveAllAndFlush(imported).stream().map(CatalogMapper::rate).toList(); }
        catch (DataIntegrityViolationException ex) { throw new ConflictException("Duplicate or conflicting quote"); }
    }
}
