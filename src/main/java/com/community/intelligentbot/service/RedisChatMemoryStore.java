package com.community.intelligentbot.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final long TTL_HOURS = 24;

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public RedisChatMemoryStore(StringRedisTemplate redisTemplate,
                                @Value("${bot.id}") String botId) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = "chat:memory:" + botId + ":";
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = redisTemplate.opsForValue().get(keyPrefix + memoryId);
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        return ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = keyPrefix + memoryId;
        String json = ChatMessageSerializer.messagesToJson(messages);
        redisTemplate.opsForValue().set(key, json, TTL_HOURS, TimeUnit.HOURS);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        redisTemplate.delete(keyPrefix + memoryId);
    }
}
