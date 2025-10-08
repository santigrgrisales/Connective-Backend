package com.fg.geminichatproxy.controller;

import com.fg.geminichatproxy.dto.ChatProxyRequest;
import com.fg.geminichatproxy.dto.ChatProxyResponse;
import com.fg.geminichatproxy.service.GeminiChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@CrossOrigin(
        origins = "http://localhost:5500",
        allowedHeaders = {"Authorization", "Content-Type", "Accept", "multipart/form-data"},
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS},
        allowCredentials = "true"
)
@RequestMapping("/apiGpt")
public class ChatController {

    private final GeminiChatService geminiChatService;
    private final Logger log = LoggerFactory.getLogger(ChatController.class);

    public ChatController(GeminiChatService geminiChatService) {
        this.geminiChatService = geminiChatService;
    }

    // Endpoint de diagnóstico rápido
    @GetMapping("/chat/ping")
    public ResponseEntity<String> ping() {
        log.info("PING recibido en /api/chat/ping");
        return ResponseEntity.ok("pong");
    }

    @PostMapping("/chat/Gemini")
    public ResponseEntity<ChatProxyResponse> chat(@RequestBody ChatProxyRequest req,
                                                  @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("POST /api/chat recibido - userMessage='{}' - hasContext={}", 
                  req != null ? req.userMessage : "null", 
                  req != null && req.context != null ? req.context.get("hasRegion") : "null");

        try {
            ChatProxyResponse resp = geminiChatService.askModel(req).block(); // ← .block() para hacerlo sincrónico
            log.info("Respuesta generada por servicio (long={}): {}", 
                     resp != null && resp.reply != null ? resp.reply.length() : 0,
                     resp != null ? resp.reply : "null");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Error en /api/chat: ", e);
            ChatProxyResponse err = new ChatProxyResponse();
            err.reply = "Error interno en el proxy de chat.";
            err.raw = e.getMessage();
            return ResponseEntity.status(500).body(err);
        }
    }
}
