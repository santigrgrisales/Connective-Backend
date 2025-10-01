package com.fg.visualizationlogic.util;

import java.text.Normalizer;

public class NameNormalizer {

    /**
     * Normaliza un nombre: quita acentos, caracteres no alfanuméricos, mayúsculas y colapsa espacios.
     */
    public static String normalize(String s) {
        if (s == null) return null;
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toUpperCase()
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return n;
    }
}
