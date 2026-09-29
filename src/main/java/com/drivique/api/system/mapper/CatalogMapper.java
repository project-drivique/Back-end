package com.drivique.api.system.mapper;

import com.drivique.api.system.dto.CurrencyResponseDTO;
import com.drivique.api.system.dto.ExchangeRateResponseDTO;
import com.drivique.api.system.dto.LanguageResponseDTO;
import com.drivique.api.system.entity.Currency;
import com.drivique.api.system.entity.ExchangeRate;
import com.drivique.api.system.entity.Language;
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
