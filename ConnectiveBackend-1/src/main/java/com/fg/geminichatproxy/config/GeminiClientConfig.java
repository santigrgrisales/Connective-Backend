package com.fg.geminichatproxy.config;

import com.google.genai.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración del Client de Google GenAI (Gemini).
 *
 * Forma de obtener la API key (orden de preferencia):
 * 1) Variable de entorno GEMINI_API_KEY
 * 2) Variable de entorno GOOGLE_API_KEY (la que la librería normalmente espera)
 * 3) Propiedad en application.properties: gemini.api.key (útil en dev, NO recomendable en prod)
 *
 * Para pruebas locales, define la variable de entorno con tu clave:
 * export GEMINI_API_KEY="AppiKeyGeminiConnective+"   # REPLACE_WITH: AppiKeyGeminiConnective+
 *
 * En Windows PowerShell:
 * $env:GEMINI_API_KEY="AppiKeyGeminiConnective+"    # REPLACE_WITH: AppiKeyGeminiConnective+
 *
 * NOTA: evita commitear la clave en el repo. Usa variables de entorno en CI/servidor.
 */
@Configuration
public class GeminiClientConfig {

    // (opcional) leer desde application.properties si el equipo lo desea
    @Value("${gemini.api.key:#{null}}")
    private String geminiApiKeyFromProps;

    @Bean
    public Client geminiClient() {
        // 1) intenta GEMINI_API_KEY
        String key = System.getenv("GEMINI_API_KEY");
        if (key == null || key.isBlank()) {
            // 2) intenta GOOGLE_API_KEY (la que la librería puede buscar por defecto)
            key = System.getenv("GOOGLE_API_KEY");
        }
        if ((key == null || key.isBlank()) && geminiApiKeyFromProps != null && !geminiApiKeyFromProps.isBlank()) {
            key = geminiApiKeyFromProps;
        }

        if (key == null || key.isBlank()) {
            // Mensaje claro para debugging en desarrollo; evita crear el client sin clave.
            throw new IllegalStateException(
                "Gemini API key no encontrada. Define la variable de entorno GEMINI_API_KEY o GOOGLE_API_KEY " +
                "o configura 'gemini.api.key' en application.properties. " +
                "Ejemplo (Linux/macOS): export GEMINI_API_KEY=\"AppiKeyGeminiConnective+\""
            );
        }

        // Construir el Client pasando la API key explícitamente (recomendado)
        // REPLACE_WITH: AppiKeyGeminiConnective+ -> No insertar aquí la clave; usar variable de entorno
        Client client = Client.builder()
                .apiKey(key)
                .build();

        return client;
    }
}
