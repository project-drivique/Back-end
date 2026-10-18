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

    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private final BrandRepository brands;

    public BrandEmailTheme(BrandRepository brands) {
        this.brands = brands;
    }

    public String apply(String html) {
        if (html == null) return null;
        try {
            BrandConfiguration brand = brands.findByActiveTrue().orElse(null);
            if (brand == null || !valid(brand.getPrimaryColor()) || !valid(brand.getSecondaryColor()) || !valid(brand.getAccentColor())) {
                return html;
            }
            String primary = brand.getPrimaryColor();
            String secondary = brand.getSecondaryColor();
            String accent = brand.getAccentColor();
            String out = html;
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
            String name = brand.getCompanyName();
            if (name != null && !name.isBlank()) {
                out = out.replace(">DRIVIQUE</h1>", ">" + escape(name.strip().toUpperCase()) + "</h1>");
            }
            return out;
        } catch (RuntimeException ex) {
            return html;
        }
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
