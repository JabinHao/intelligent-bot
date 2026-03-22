package com.community.intelligentbot.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface AssistantService {

    @SystemMessage("You are a helpful community assistant. Answer questions clearly and concisely. " +
            "If context is provided, use it to answer. If the context does not contain relevant information, say so.")
    String chat(@MemoryId String memoryId, @UserMessage String userMessage);
}
