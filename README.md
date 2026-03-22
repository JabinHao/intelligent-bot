# Intelligent Bot

An intelligent Discord bot powered by RAG (Retrieval-Augmented Generation), built with Spring Boot and LangChain4j.

## Tech Stack

- **Java 17** + **Spring Boot 3.5**
- **LangChain4j** - LLM integration (Alibaba DashScope / Qwen)
- **Milvus** - Vector database for RAG
- **JDA** - Discord bot framework
- **Redis** - Caching

## Prerequisites

- Java 17+
- Maven 3.9+
- Docker & Docker Compose (for Milvus)
- A DashScope API key ([dashscope.console.aliyun.com](https://dashscope.console.aliyun.com))
- A Discord bot token ([discord.com/developers](https://discord.com/developers/applications))

## Getting Started

### 1. Start Milvus

```bash
docker network create --subnet=172.30.4.0/24 my-network-1
docker compose -f milvus-standalone-docker-compose.yml up -d
```

### 2. Set Environment Variables

```bash
export OPENAI_API_KEY=your-dashscope-api-key
export DISCORD_BOT_TOKEN=your-discord-bot-token
```

### 3. Run the Application

```bash
./mvnw spring-boot:run
```

## Project Structure

```
src/main/java/com/community/intelligentbot/
└── IntelligentBotApplication.java    # Application entry point
```

## Configuration

Key settings in `application.yml`:

| Config | Description |
|--------|-------------|
| `langchain4j.open-ai.chat-model` | LLM for chat (Qwen3-Max via DashScope) |
| `langchain4j.open-ai.embedding-model` | Embedding model (text-embedding-v4) |
| `milvus.uri` | Milvus vector database connection |
| `discord.bot.token` | Discord bot token |

## License

MIT
