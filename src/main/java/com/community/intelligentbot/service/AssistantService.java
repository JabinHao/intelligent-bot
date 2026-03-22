package com.community.intelligentbot.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface AssistantService {

    TokenStream chatStream(@MemoryId String memoryId, @UserMessage String userMessage);
}
