package com.community.intelligentbot.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

@Slf4j
public class ChatHistoryEmbeddingService {

    private static final String DEDUP_KEY_PREFIX = "chat_history_embedded:";

    private final EmbeddingModel embeddingModel;
    private final MilvusEmbeddingStore chatHistoryStore;
    private final StringRedisTemplate redisTemplate;
    private final String botId;

    public ChatHistoryEmbeddingService(EmbeddingModel embeddingModel,
                                       MilvusEmbeddingStore chatHistoryStore,
                                       StringRedisTemplate redisTemplate,
                                       String botId) {
        this.embeddingModel = embeddingModel;
        this.chatHistoryStore = chatHistoryStore;
        this.redisTemplate = redisTemplate;
        this.botId = botId;
    }

    public void embedConversationTurn(String memoryId, String userMessage, String assistantResponse) {
        String[] parts = memoryId.split(":");
        String userId = parts[1];
        String contextType = parts[2]; // "dm" or "guild"

        String formattedText = String.format("[%s] User asked: %s\nAssistant answered: %s",
                LocalDate.now(), userMessage, assistantResponse);

        String hash = sha256(formattedText);
        if (isAlreadyEmbedded(hash)) {
            log.debug("Conversation turn already embedded, skipping");
            return;
        }

        TextSegment segment = TextSegment.from(formattedText,
                dev.langchain4j.data.document.Metadata.from("userId", userId)
                        .put("botId", botId)
                        .put("contextType", contextType)
                        .put("timestamp", String.valueOf(System.currentTimeMillis())));

        Embedding embedding = embeddingModel.embed(segment).content();
        chatHistoryStore.add(embedding, segment);
        markEmbedded(hash);

        log.debug("[{}] Embedded conversation turn for user {}", botId, userId);
    }

    private boolean isAlreadyEmbedded(String hash) {
        String key = DEDUP_KEY_PREFIX + botId + ":" + hash;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private void markEmbedded(String hash) {
        String key = DEDUP_KEY_PREFIX + botId + ":" + hash;
        redisTemplate.opsForValue().set(key, "1");
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
