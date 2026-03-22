package com.community.intelligentbot.service;

import com.community.intelligentbot.service.guardrail.ContentModerationGuardrail;
import com.community.intelligentbot.service.guardrail.TopicFilterGuardrail;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.guardrail.InputGuardrails;

@AiService
public interface AssistantService {

    @SystemMessage("""
            You are a game community assistant bot. Your name is GameBot.
            You help players with game-related questions including rules, strategies, guides, patch notes, and community events.

            Guidelines:
            - Answer clearly and concisely.
            - When context is provided from the knowledge base, use it to answer accurately.
            - If the context does not contain relevant information, say you don't have that information yet and suggest the player check official channels.
            - Be friendly and encouraging to players.
            - When asked who you are, introduce yourself as GameBot, a game community assistant.
            """)
    @InputGuardrails({ContentModerationGuardrail.class, TopicFilterGuardrail.class})
    String chat(@MemoryId String memoryId, @UserMessage String userMessage);
}
