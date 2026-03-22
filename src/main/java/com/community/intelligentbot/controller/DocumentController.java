package com.community.intelligentbot.controller;

import com.community.intelligentbot.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody Map<String, String> request) {
        String path = request.get("path");
        if (path == null || path.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "path is required"));
        }

        int count = documentIngestionService.ingestFromDirectory(path);
        return ResponseEntity.ok(Map.of(
                "message", "Documents ingested successfully",
                "count", count
        ));
    }
}
