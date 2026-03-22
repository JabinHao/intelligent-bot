# Intelligent Discord Bot - RAG Implementation Plan

## Context
Building a RAG-powered Discord bot as a learning project for AI development. The Discord bot is already created (token ready). The project skeleton exists with all dependencies configured. We need to implement the actual bot logic, RAG pipeline, and chat memory.

## Package Structure
```
com.community.intelligentbot
├── IntelligentBotApplication.java          (exists)
├── config/
│   ├── DiscordBotConfig.java               (Step 1)
│   ├── MilvusConfig.java                   (Step 2)
│   └── ChatMemoryConfig.java               (Step 4)
├── service/
│   ├── AssistantService.java               (Step 1 - @AiService interface)
│   ├── DocumentIngestionService.java       (Step 2)
│   └── RedisChatMemoryStore.java           (Step 4)
├── listener/
│   └── MessageListener.java               (Step 1)
└── controller/
    └── DocumentController.java            (Step 2)
```

## Step 1: Wire LLM Chat (Discord + Qwen)

**Goal:** Bot listens in Discord, sends messages to Qwen3-Max, replies with LLM response.

### 1.1 `service/AssistantService.java`
- Interface annotated with `@AiService`
- Method: `String chat(@MemoryId String memoryId, @UserMessage String userMessage)`
- `@SystemMessage` with bot persona
- Auto-wired with `ChatModel` from Spring context (auto-configured from yml)

### 1.2 `listener/MessageListener.java`
- `@Component` extending JDA's `ListenerAdapter`
- Autowires `AssistantService`
- Overrides `onMessageReceived(MessageReceivedEvent event)`
- Ignores bot messages, responds when mentioned or in DMs
- Handles Discord's 2000-char message limit (split long responses)

### 1.3 `config/DiscordBotConfig.java`
- `@Configuration` class, reads `discord.bot.token` via `@Value`
- Creates `@Bean JDA` with gateway intents: `GUILD_MESSAGES`, `DIRECT_MESSAGES`, `MESSAGE_CONTENT`
- Registers `MessageListener`

**Milestone:** Bot replies in Discord via Qwen3-Max.

---

## Step 2: Milvus Embedding Store + Document Ingestion

**Goal:** Configure Milvus, build ingestion service, expose REST endpoint.

### 2.1 `config/MilvusConfig.java`
- `@Configuration`, reads `milvus.uri` and `milvus.port`
- `@Bean MilvusEmbeddingStore` with `dimension(1024)` (text-embedding-v4 output)

### 2.2 `service/DocumentIngestionService.java`
- `@Service`, autowires `EmbeddingModel` + `MilvusEmbeddingStore`
- Uses `EmbeddingStoreIngestor` with `DocumentSplitters.recursive(800, 200)`
- Methods: `ingestFromDirectory(String path)`, `ingestSingleDocument(String path)`

### 2.3 `controller/DocumentController.java`
- `@RestController` at `/api/documents`
- `POST /api/documents/ingest` — triggers ingestion from a given directory path

**Milestone:** POST to ingest docs, verify embeddings stored in Milvus.

---

## Step 3: Add RAG to Chat Pipeline

**Goal:** Retrieve relevant docs from Milvus to augment LLM prompts.

### 3.1 Add `ContentRetriever` bean to `MilvusConfig.java`
```java
@Bean
ContentRetriever contentRetriever(MilvusEmbeddingStore store, EmbeddingModel model) {
    return EmbeddingStoreContentRetriever.builder()
        .embeddingStore(store)
        .embeddingModel(model)
        .maxResults(5)
        .minScore(0.7)
        .build();
}
```
- No changes to `AssistantService` needed — LangChain4j auto-wires `ContentRetriever` into `@AiService`

**Milestone:** Bot answers grounded in ingested documents.

---

## Step 4: Redis-Backed Chat Memory

**Goal:** Persist per-user conversation history in Redis.

### 4.1 `service/RedisChatMemoryStore.java`
- `@Component` implementing `ChatMemoryStore`
- Uses `StringRedisTemplate`, key pattern: `chat:memory:{discordUserId}`
- Serializes/deserializes with LangChain4j's `ChatMessageSerializer`/`ChatMessageDeserializer`

### 4.2 `config/ChatMemoryConfig.java`
- `@Bean ChatMemoryProvider` using `MessageWindowChatMemory` with `maxMessages(20)` and `RedisChatMemoryStore`

**Milestone:** Bot remembers per-user history across restarts.

---

## Key Pitfalls
- **Embedding dimension:** text-embedding-v4 = 1024 dims, must match Milvus collection
- **JDA 6 API:** builder pattern differs from JDA 5, verify at implementation
- **Discord MESSAGE_CONTENT intent:** must be enabled in Discord Developer Portal (privileged intent)
- **Discord 2000-char limit:** split long LLM responses
- **DashScope rate limits:** watch for rate limiting during bulk ingestion

## Verification
- Step 1: Send a message in Discord, bot replies with LLM-generated response
- Step 2: `curl -X POST localhost:8090/api/documents/ingest -d '{"path":"/some/dir"}'` succeeds
- Step 3: Ask bot about ingested content, response uses document context
- Step 4: Restart app, bot remembers previous conversation
