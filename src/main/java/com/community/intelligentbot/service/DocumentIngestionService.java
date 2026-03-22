package com.community.intelligentbot.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Slf4j
public class DocumentIngestionService {

    private final EmbeddingStoreIngestor ingestor;
    private final StringRedisTemplate redisTemplate;
    private final String botId;

    private static final String INGESTED_KEY_PREFIX = "ingested:";

    public DocumentIngestionService(EmbeddingModel embeddingModel,
                                     MilvusEmbeddingStore embeddingStore,
                                     StringRedisTemplate redisTemplate,
                                     String botId) {
        this.redisTemplate = redisTemplate;
        this.botId = botId;
        this.ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(800, 200))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
    }

    public void loadLocalKnowledge(String knowledgePath) {
        if (knowledgePath == null || knowledgePath.isBlank()) {
            return;
        }
        Path path = Path.of(knowledgePath);
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            log.warn("Knowledge path does not exist or is not a directory: {}", knowledgePath);
            return;
        }
        try {
            List<Document> documents = FileSystemDocumentLoader.loadDocuments(knowledgePath);
            int ingested = 0;
            for (Document doc : documents) {
                String fileName = doc.metadata().getString("file_name");
                String hash = sha256(doc.text());
                if (isAlreadyIngested(fileName, hash)) {
                    log.debug("Skipping already ingested document: {}", fileName);
                    continue;
                }
                ingestor.ingest(doc);
                markIngested(fileName, hash);
                ingested++;
            }
            log.info("Loaded {} new documents from {} ({} skipped)",
                    ingested, knowledgePath, documents.size() - ingested);
        } catch (Exception e) {
            log.error("Failed to load local knowledge from {}: {}", knowledgePath, e.getMessage(), e);
        }
    }

    public int ingestFromDirectory(String directoryPath) {
        log.info("Ingesting documents from directory: {}", directoryPath);
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(directoryPath);
        int ingested = 0;
        for (Document doc : documents) {
            String fileName = doc.metadata().getString("file_name");
            String hash = sha256(doc.text());
            if (isAlreadyIngested(fileName, hash)) {
                log.debug("Skipping already ingested document: {}", fileName);
                continue;
            }
            ingestor.ingest(doc);
            markIngested(fileName, hash);
            ingested++;
        }
        log.info("Ingested {} new documents ({} skipped)", ingested, documents.size() - ingested);
        return ingested;
    }

    public void ingestSingleDocument(String filePath) {
        Document document = FileSystemDocumentLoader.loadDocument(filePath);
        String fileName = document.metadata().getString("file_name");
        String hash = sha256(document.text());
        if (isAlreadyIngested(fileName, hash)) {
            log.info("Document already ingested, skipping: {}", filePath);
            return;
        }
        ingestor.ingest(document);
        markIngested(fileName, hash);
        log.info("Successfully ingested document: {}", filePath);
    }

    public void ingestText(String title, String content) {
        String hash = sha256(content);
        if (isAlreadyIngested(title, hash)) {
            log.info("Text already ingested, skipping: {}", title);
            return;
        }
        Metadata metadata = new Metadata();
        metadata.put("title", title);
        Document document = Document.from(content, metadata);
        ingestor.ingest(document);
        markIngested(title, hash);
        log.info("Successfully ingested text document: {}", title);
    }

    private boolean isAlreadyIngested(String name, String hash) {
        String storedHash = redisTemplate.opsForValue().get(ingestedKey(name));
        return hash.equals(storedHash);
    }

    private void markIngested(String name, String hash) {
        redisTemplate.opsForValue().set(ingestedKey(name), hash);
    }

    private String ingestedKey(String name) {
        return INGESTED_KEY_PREFIX + botId + ":" + name;
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
