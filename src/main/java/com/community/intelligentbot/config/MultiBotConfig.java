package com.community.intelligentbot.config;

import com.community.intelligentbot.listener.MessageListener;
import com.community.intelligentbot.service.AssistantService;
import com.community.intelligentbot.service.DocumentIngestionService;
import com.community.intelligentbot.service.guardrail.ContentModerationGuardrail;
import com.community.intelligentbot.service.guardrail.TopicFilterGuardrail;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
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

        // 2. Retrieval augmentor with query transformer
        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(CompressingQueryTransformer.builder()
                        .chatModel(chatModel)
                        .build())
                .contentRetriever(EmbeddingStoreContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .maxResults(5)
                        .minScore(0.7)
                        .build())
                .build();

        // 3. AssistantService per bot with custom persona
        AssistantService assistantService = AiServices.builder(AssistantService.class)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .retrievalAugmentor(retrievalAugmentor)
                .systemMessageProvider(memoryId -> botConfig.getPersona())
                .inputGuardrails(contentModerationGuardrail, topicFilterGuardrail)
                .build();

        // 4. Document ingestion service per bot
        DocumentIngestionService documentIngestionService = new DocumentIngestionService(embeddingModel, embeddingStore, redisTemplate, botConfig.getId());
        documentIngestionService.loadLocalKnowledge(botConfig.getKnowledgePath());

        // 5. MessageListener per bot
        MessageListener messageListener = new MessageListener(assistantService, botConfig.getId());

        // 6. JDA instance per bot
        var jda = JDABuilder.createDefault(botConfig.getToken(),
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(messageListener)
                .build()
                .awaitReady();

        return BotContext.builder()
                .id(botConfig.getId())
                .jda(jda)
                .assistantService(assistantService)
                .documentIngestionService(documentIngestionService)
                .build();
    }
}
