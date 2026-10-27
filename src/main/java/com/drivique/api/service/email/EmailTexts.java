package com.drivique.api.service.email;

import com.drivique.api.service.BrandEmailTheme;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * Textos de los correos en el idioma del destinatario. Si falta una clave en un idioma se usa el español (archivo
 * base), y {brand} siempre se reemplaza por el nombre de la marca activa.
 */
@Component
public class EmailTexts {

    private final ResourceBundleMessageSource source = new ResourceBundleMessageSource();

    @Autowired(required = false)
    private BrandEmailTheme brandTheme;

    public EmailTexts() {
        source.setBasename("email/email");
        source.setDefaultEncoding("UTF-8");
        // Sin esto un servidor configurado en inglés mostraría los correos en inglés aunque el usuario pida español
        source.setFallbackToSystemLocale(false);
    }

    /** Texto de la clave; los pares son {nombre, valor} y reemplazan {nombre} en el texto. */
    public String get(Locale locale, String key, String... pairs) {
        String text = source.getMessage(key, null, key, locale == null ? EmailLanguage.DEFAULT : locale);
        text = text.replace("{brand}", brandName());
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", pairs[i + 1] == null ? "" : pairs[i + 1]);
        }
        return text;
    }

    /** Traduce el texto de un botón que los servicios arman en español; si no hay traducción se deja tal cual. */
    public String actionLabel(Locale locale, String spanishLabel) {
        if (spanishLabel == null || spanishLabel.isBlank()) {
            return spanishLabel;
        }
        String found = source.getMessage("label." + slug(spanishLabel), null, null, locale == null ? EmailLanguage.DEFAULT : locale);
        return found == null ? spanishLabel : found;
    }

    public String brandName() {
        return brandTheme != null ? brandTheme.companyName() : "Drivique";
    }

    static String slug(String label) {
        String plain = Normalizer.normalize(label.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
