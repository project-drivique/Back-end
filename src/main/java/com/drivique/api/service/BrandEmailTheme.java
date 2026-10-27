package com.drivique.api.service;

import com.drivique.api.model.BrandConfiguration;
import com.drivique.api.repository.BrandRepository;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Aplica la paleta de marca activa a los correos HTML: los azules originales de Drivique se
 * sustituyen por los colores que el administrador eligió en la marca.
 */
@Component
public class BrandEmailTheme {

    private static final String DEFAULT_NAME = "Drivique";
    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
    /** La palabra Drivique suelta; no cuando forma parte de un correo, una dirección web o una palabra más larga. */
    private static final Pattern BRAND_WORD = Pattern.compile("(?<![A-Za-z0-9@./_-])Drivique(?![A-Za-z0-9@_-])(?!\\.[A-Za-z])");
    private final BrandRepository brands;

    public BrandEmailTheme(BrandRepository brands) {
        this.brands = brands;
    }

    /** Nombre de la marca activa; "Drivique" mientras no se haya configurado otro. */
    public String companyName() {
        try {
            return brands.findByActiveTrue()
                    .map(BrandConfiguration::getCompanyName)
                    .filter(name -> name != null && !name.isBlank())
                    .map(String::strip)
                    .orElse(DEFAULT_NAME);
        } catch (RuntimeException ex) {
            return DEFAULT_NAME;
        }
    }

    public String apply(String html) {
        if (html == null) return null;
        try {
            BrandConfiguration brand = brands.findByActiveTrue().orElse(null);
            if (brand == null) {
                return html;
            }
            String out = html;
            if (valid(brand.getPrimaryColor()) && valid(brand.getSecondaryColor()) && valid(brand.getAccentColor())) {
                String primary = brand.getPrimaryColor();
                String secondary = brand.getSecondaryColor();
                String accent = brand.getAccentColor();
                out = replaceHex(out, "#1e3a8a", secondary);
                out = replaceHex(out, "#2563eb", primary);
                out = replaceHex(out, "#3b82f6", primary);
                out = replaceHex(out, "#1d4ed8", mix(primary, "#000000", 0.12));
                out = replaceHex(out, "#1e40af", mix(primary, "#000000", 0.2));
                out = replaceHex(out, "#60a5fa", accent);
                out = replaceHex(out, "#93c5fd", mix(primary, "#ffffff", 0.6));
                out = replaceHex(out, "#bfdbfe", mix(primary, "#ffffff", 0.7));
                out = replaceHex(out, "#dbeafe", mix(primary, "#ffffff", 0.82));
                out = replaceHex(out, "#eff6ff", mix(primary, "#ffffff", 0.92));
                // Fondos oscuros de la página del conductor: siguen la paleta en vez de quedarse en un azul fijo
                out = replaceHex(out, "#09132b", mix(secondary, "#000000", 0.6));
                out = replaceHex(out, "#172554", mix(secondary, "#000000", 0.3));
                out = replaceHex(out, "#0b1329", mix(secondary, "#000000", 0.7));
                out = out.replace("rgba(30, 58, 138,", "rgba(" + rgb(secondary) + ",");
            }
            String name = brand.getCompanyName();
            if (name != null && !name.isBlank()) {
                String clean = name.strip();
                out = out.replace(">DRIVIQUE</h1>", ">" + escape(clean.toUpperCase()) + "</h1>");
                if (!DEFAULT_NAME.equals(clean)) {
                    // El nombre también en los textos del cuerpo y del pie; las direcciones y los correos no se tocan
                    out = BRAND_WORD.matcher(out).replaceAll(java.util.regex.Matcher.quoteReplacement(escape(clean)));
                }
            }
            return out;
        } catch (RuntimeException ex) {
            return html;
        }
    }

    private static String rgb(String hex) {
        return Integer.parseInt(hex.substring(1, 3), 16) + ", " + Integer.parseInt(hex.substring(3, 5), 16) + ", " + Integer.parseInt(hex.substring(5, 7), 16);
    }

    private static boolean valid(String color) {
        return color != null && HEX.matcher(color).matches();
    }

    private static String replaceHex(String html, String from, String to) {
        return Pattern.compile(Pattern.quote(from), Pattern.CASE_INSENSITIVE).matcher(html).replaceAll(to);
    }

    private static String mix(String a, String b, double weight) {
        StringBuilder sb = new StringBuilder("#");
        for (int i = 0; i < 3; i++) {
            int ca = Integer.parseInt(a.substring(1 + i * 2, 3 + i * 2), 16);
            int cb = Integer.parseInt(b.substring(1 + i * 2, 3 + i * 2), 16);
            sb.append(String.format("%02x", Math.round(ca + (cb - ca) * weight)));
        }
        return sb.toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
