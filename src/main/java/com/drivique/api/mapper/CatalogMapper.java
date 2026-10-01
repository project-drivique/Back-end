package com.drivique.api.mapper;

import com.drivique.api.dto.CurrencyResponseDTO;
import com.drivique.api.dto.ExchangeRateResponseDTO;
import com.drivique.api.dto.LanguageResponseDTO;
import com.drivique.api.model.Currency;
import com.drivique.api.model.ExchangeRate;
import com.drivique.api.model.Language;
public final class CatalogMapper {
    private CatalogMapper() {}
    public static LanguageResponseDTO language(Language value) {
        return new LanguageResponseDTO(value.getId(), value.getCode(), value.getName(), value.getDefaultLanguage(), value.getActive());
    }
    public static CurrencyResponseDTO currency(Currency value) {
        return new CurrencyResponseDTO(value.getId(), value.getCode(), value.getName(), value.getSymbol(), value.getDefaultCurrency(), value.getActive());
    }
    public static ExchangeRateResponseDTO rate(ExchangeRate value) {
        return new ExchangeRateResponseDTO(value.getId(), value.getFromCurrency().getCode().trim(),
                value.getToCurrency().getCode().trim(), value.getRate(), value.getProvider(), value.getFetchedAt());
    }
}
