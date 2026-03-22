package com.community.intelligentbot.controller;

import com.community.intelligentbot.config.BotContext;
import com.community.intelligentbot.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final List<BotContext> botContexts;

    @PostMapping("/{botId}/ingest")
    public ResponseEntity<Map<String, Object>> ingest(@PathVariable String botId,
                                                       @RequestBody Map<String, String> request) {
        DocumentIngestionService service = findIngestionService(botId);
        if (service == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown bot: " + botId));
        }

        String path = request.get("path");
        if (path == null || path.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "path is required"));
        }

        int count = service.ingestFromDirectory(path);
        return ResponseEntity.ok(Map.of(
                "message", "Documents ingested successfully",
                "botId", botId,
                "count", count
        ));
    }

    @PostMapping("/{botId}/ingest/text")
    public ResponseEntity<Map<String, String>> ingestText(@PathVariable String botId,
                                                           @RequestBody Map<String, String> request) {
        DocumentIngestionService service = findIngestionService(botId);
        if (service == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown bot: " + botId));
        }

        String title = request.get("title");
        String content = request.get("content");
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "title and content are required"));
        }

        service.ingestText(title, content);
        return ResponseEntity.ok(Map.of("message", "Text document ingested successfully", "botId", botId, "title", title));
    }

    private DocumentIngestionService findIngestionService(String botId) {
        return botContexts.stream()
                .filter(ctx -> ctx.getId().equals(botId))
                .map(BotContext::getDocumentIngestionService)
                .findFirst()
                .orElse(null);
    }
}
