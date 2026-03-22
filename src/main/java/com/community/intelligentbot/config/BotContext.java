package com.community.intelligentbot.config;

import com.community.intelligentbot.service.AssistantService;
import com.community.intelligentbot.service.ChatHistoryEmbeddingService;
import com.community.intelligentbot.service.DocumentIngestionService;
import lombok.Builder;
import lombok.Getter;
import net.dv8tion.jda.api.JDA;

@Getter
@Builder
public class BotContext {

    private final String id;
    private final JDA jda;
    private final AssistantService assistantService;
    private final DocumentIngestionService documentIngestionService;
    private final ChatHistoryEmbeddingService chatHistoryEmbeddingService;
}
