package com.community.intelligentbot.controller;

import com.community.intelligentbot.config.BotContext;
import com.community.intelligentbot.service.AssistantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final long SSE_TIMEOUT = 120_000L;

    private final List<BotContext> botContexts;

    @PostMapping(value = "/{botId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Object stream(@PathVariable String botId,
                         @RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        String message = request.get("message");

        if (userId == null || userId.isBlank() || message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId and message are required"));
        }

        AssistantService assistantService = findAssistantService(botId);
        if (assistantService == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown bot: " + botId));
        }

        String memoryId = botId + ":" + userId + ":api";
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        assistantService.chatStream(memoryId, message)
                .onPartialResponse(token -> {
                    try {
                        emitter.send(SseEmitter.event().data(token));
                    } catch (Exception e) {
                        log.debug("SSE send failed: {}", e.getMessage());
                    }
                })
                .onCompleteResponse(response -> {
                    try {
                        emitter.send(SseEmitter.event().data("[DONE]"));
                        emitter.complete();
                    } catch (Exception e) {
                        log.debug("SSE complete failed: {}", e.getMessage());
                    }
                })
                .onError(error -> {
                    log.error("[{}] SSE streaming error: {}", botId, error.getMessage(), error);
                    emitter.completeWithError(error);
                })
                .start();

        return emitter;
    }

    private AssistantService findAssistantService(String botId) {
        return botContexts.stream()
                .filter(ctx -> ctx.getId().equals(botId))
                .map(BotContext::getAssistantService)
                .findFirst()
                .orElse(null);
    }
}
