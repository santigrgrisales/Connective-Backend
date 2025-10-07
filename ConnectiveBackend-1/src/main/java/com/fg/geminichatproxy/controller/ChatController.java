package com.fg.geminichatproxy.controller;



import com.fg.geminichatproxy.dto.ChatProxyRequest;
import com.fg.geminichatproxy.dto.ChatProxyResponse;
import com.fg.geminichatproxy.service.GeminiChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Endpoint de proxy para el chat.
 * POST /api/chat
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final GeminiChatService geminiChatService;

    public ChatController(GeminiChatService geminiChatService) {
        this.geminiChatService = geminiChatService;
    }

    @PostMapping("/chat")
    public Mono<ResponseEntity<ChatProxyResponse>> chat(@RequestBody ChatProxyRequest req,
                                                        @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Opcional: validar token JWT/Session a través de tu sistema actual.
        // Si validas aquí y el token es inválido, retorna 401.

        // Llamada al servicio que contacta Gemini
        return geminiChatService.askModel(req)
                .map(resp -> ResponseEntity.ok(resp))
                .onErrorResume(e -> {
                    ChatProxyResponse err = new ChatProxyResponse();
                    err.reply = "Error interno: no se pudo obtener respuesta del proveedor de IA.";
                    err.raw = e.getMessage();
                    return Mono.just(ResponseEntity.status(500).body(err));
                });
    }
}
