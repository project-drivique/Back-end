package com.drivique.api.catalog;
final class CatalogMapper {
    private CatalogMapper() {}
    static LanguageResponseDTO language(Language value) {
        return new LanguageResponseDTO(value.getId(), value.getCode(), value.getName(), value.getDefaultLanguage(), value.getActive());
    }
    static CurrencyResponseDTO currency(Currency value) {
        return new CurrencyResponseDTO(value.getId(), value.getCode(), value.getName(), value.getSymbol(), value.getDefaultCurrency(), value.getActive());
    }
    static ExchangeRateResponseDTO rate(ExchangeRate value) {
        return new ExchangeRateResponseDTO(value.getId(), value.getFromCurrency().getCode().trim(),
                value.getToCurrency().getCode().trim(), value.getRate(), value.getProvider(), value.getFetchedAt());
    }
}
