package com.community.intelligentbot.config;

import com.community.intelligentbot.listener.MessageListener;
import com.community.intelligentbot.service.AssistantService;
import com.community.intelligentbot.service.ChatHistoryEmbeddingService;
import com.community.intelligentbot.service.DocumentIngestionService;
import com.community.intelligentbot.service.tool.DiscordTools;
import com.community.intelligentbot.service.guardrail.ContentModerationGuardrail;
import com.community.intelligentbot.service.guardrail.TopicFilterGuardrail;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MultiBotConfig {

    private final BotProperties botProperties;
    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final EmbeddingModel embeddingModel;
    private final ChatMemoryProvider chatMemoryProvider;
    private final ContentModerationGuardrail contentModerationGuardrail;
    private final TopicFilterGuardrail topicFilterGuardrail;
    private final StringRedisTemplate redisTemplate;

    @Value("${milvus.host}")
    private String milvusHost;

    @Value("${milvus.port}")
    private int milvusPort;

    @Bean
    public List<BotContext> botContexts() throws InterruptedException {
        List<BotContext> contexts = new ArrayList<>();

        for (BotProperties.BotConfig botConfig : botProperties.getBots()) {
            log.info("Initializing bot: {}", botConfig.getId());
            BotContext context = createBotContext(botConfig);
            contexts.add(context);
            log.info("Bot '{}' initialized successfully", botConfig.getId());
        }

        return contexts;
    }

    private BotContext createBotContext(BotProperties.BotConfig botConfig) throws InterruptedException {
        // 1. Milvus embedding store per bot
        MilvusEmbeddingStore embeddingStore = MilvusEmbeddingStore.builder()
                .host(milvusHost)
                .port(milvusPort)
                .collectionName(botConfig.getId() + "_knowledge_base")
                .dimension(1024)
                .indexType(IndexType.FLAT)
                .metricType(MetricType.COSINE)
                .consistencyLevel(ConsistencyLevelEnum.EVENTUALLY)
                .build();

        // 2. Milvus embedding store for chat history per bot
        MilvusEmbeddingStore chatHistoryStore = MilvusEmbeddingStore.builder()
                .host(milvusHost)
                .port(milvusPort)
                .collectionName(botConfig.getId() + "_chat_history")
                .dimension(1024)
                .indexType(IndexType.FLAT)
                .metricType(MetricType.COSINE)
                .consistencyLevel(ConsistencyLevelEnum.EVENTUALLY)
                .build();

        // 3. Content retrievers: knowledge base + chat history
        ContentRetriever knowledgeBaseRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .displayName("knowledge-base")
                .maxResults(5)
                .minScore(0.7)
                .build();

        ContentRetriever chatHistoryRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(chatHistoryStore)
                .embeddingModel(embeddingModel)
                .displayName("chat-history")
                .maxResults(3)
                .minScore(0.6)
                .dynamicFilter(query -> {
                    String memId = query.metadata().chatMemoryId().toString();
                    String userId = memId.split(":")[1];
                    return new dev.langchain4j.store.embedding.filter.comparison.IsEqualTo("userId", userId);
                })
                .build();

        // 4. Retrieval augmentor with query transformer and multi-source routing
        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(CompressingQueryTransformer.builder()
                        .chatModel(chatModel)
                        .build())
                .queryRouter(new DefaultQueryRouter(knowledgeBaseRetriever, chatHistoryRetriever))
                .build();

        // 5. JDA instance per bot (created first so tools can use it)
        var jda = JDABuilder.createDefault(botConfig.getToken(),
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT)
                .build()
                .awaitReady();

        // 6. Discord tools with JDA access
        DiscordTools discordTools = new DiscordTools(jda);

        // 7. AssistantService per bot with custom persona and tools
        AssistantService assistantService = AiServices.builder(AssistantService.class)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .retrievalAugmentor(retrievalAugmentor)
                .systemMessageProvider(memoryId -> botConfig.getPersona())
                .inputGuardrails(contentModerationGuardrail, topicFilterGuardrail)
                .tools(discordTools)
                .build();

        // 8. Document ingestion service per bot
        DocumentIngestionService documentIngestionService = new DocumentIngestionService(embeddingModel, embeddingStore, redisTemplate, botConfig.getId());
        documentIngestionService.loadLocalKnowledge(botConfig.getKnowledgePath());

        // 9. Chat history embedding service per bot
        ChatHistoryEmbeddingService chatHistoryEmbeddingService = new ChatHistoryEmbeddingService(
                embeddingModel, chatHistoryStore, redisTemplate, botConfig.getId());

        // 10. Register MessageListener after AssistantService is ready
        MessageListener messageListener = new MessageListener(assistantService, botConfig.getId(), chatHistoryEmbeddingService);
        jda.addEventListener(messageListener);

        return BotContext.builder()
                .id(botConfig.getId())
                .jda(jda)
                .assistantService(assistantService)
                .documentIngestionService(documentIngestionService)
                .chatHistoryEmbeddingService(chatHistoryEmbeddingService)
                .build();
    }
}
