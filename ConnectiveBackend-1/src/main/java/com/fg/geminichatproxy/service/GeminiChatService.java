package com.fg.geminichatproxy.service;


import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.fg.geminichatproxy.dto.ChatProxyRequest; 
import com.fg.geminichatproxy.dto.ChatProxyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * Servicio que habla con Gemini (gemini-2.5-flash).
 * Usa la librería oficial google-genai.
 */
@Service
public class GeminiChatService {

    private final Client client;
    private final Logger log = LoggerFactory.getLogger(GeminiChatService.class);

    public GeminiChatService(Client geminiClient) {
        this.client = geminiClient;
    }

    /**
     * Construye el prompt en servidor y llama al modelo.
     * Retorna un Mono para integrarlo fácilmente en controladores WebFlux o sincronamente (.block()).
     */
    public Mono<ChatProxyResponse> askModel(ChatProxyRequest req) {
        return Mono.fromCallable(() -> {
            // 1) system prompt (servidor): regla clara para el asistente
            String systemPrompt = """
                    Eres el 'Asistente de Brecha Digital' para un dashboard que muestra el Índice de Desarrollo Digital en regiones de Colombia.
                    Reglas:
                    1) Si no hay contexto (no hay región seleccionada ni filtros), NO inventes análisis. Indica pasos claros para obtener contexto en la UI.
                    2) Si hay contexto, da: (a) resumen breve de 1-2 líneas; (b) hasta 3 recomendaciones accionables con 1 línea de justificación; (c) 2 indicadores sugeridos para seguir impacto.
                    3) Lenguaje claro y conciso. Formato: Resumen:, Recomendaciones:, Indicadores:
                    """;

            // 2) context summary (se envía como parte del prompt; no expongas datos sensibles)
            String contextSummary = buildContextSummary(req.context);

            // 3) Mensaje final (concatenar system + context + user)
            String fullPrompt = systemPrompt + "\n\nContexto:\n" + contextSummary + "\n\nPregunta del usuario:\n" + (req.userMessage != null ? req.userMessage : "");

            // 4) Llamada al modelo (gemini-2.5-flash)
            // La librería oficial permite:
            // GenerateContentResponse response = client.models.generateContent("gemini-2.5-flash", fullPrompt, null);
            GenerateContentResponse response = client.models.generateContent("gemini-2.5-flash", fullPrompt, null);

            // 5) Construir respuesta
            ChatProxyResponse out = new ChatProxyResponse();
            out.reply = response.text(); // método convenience .text()
            out.raw = response; // útil para debug (o null en prod)
            return out;

        }).subscribeOn(Schedulers.boundedElastic()); // ejecutar en hilo bloqueante (la lib puede ser bloqueante)
    }

    private String buildContextSummary(Map<String,Object> context) {
        if (context == null || context.isEmpty()) return "Sin contexto (no hay región seleccionada ni filtros aplicados).";
        StringBuilder sb = new StringBuilder();
        // ejemplo: context puede contener hasRegion, regionName, regionMetric, filtersSummary
        if (Boolean.TRUE.equals(context.get("hasRegion"))) {
            sb.append("Región seleccionada: ").append(context.getOrDefault("regionName","(nombre no disponible)")).append(". ");
            Object regionMetricObj = context.get("regionMetric");
            if (regionMetricObj instanceof Map) {
                Map<?,?> m = (Map<?,?>) regionMetricObj;
                Object indiceObj = m.get("indice");
                String indiceStr = indiceObj != null ? String.valueOf(indiceObj) : "N/A";
                sb.append("Índice: ").append(indiceStr).append(", ");

                Object idRegionObj = m.get("idRegion");
                String idRegionStr = idRegionObj != null ? String.valueOf(idRegionObj) : "N/A";
                sb.append("Hogares (idRegion): ").append(idRegionStr).append(". ");
            }
        }
        if (context.get("filtersSummary") != null) {
            sb.append("Filtros aplicados: ").append(context.get("filtersSummary")).append(". ");
        } else if (Boolean.TRUE.equals(context.get("filtersApplied"))) {
            sb.append("Filtros aplicados (detalles no enviados). ");
        }

        if (sb.length() == 0) return "Contexto no detallado.";
        return sb.toString();
    }
}
