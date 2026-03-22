package com.community.intelligentbot.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class DocumentIngestionService {

    private final EmbeddingStoreIngestor ingestor;

    public DocumentIngestionService(EmbeddingModel embeddingModel, MilvusEmbeddingStore embeddingStore) {
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
            int count = ingestFromDirectory(knowledgePath);
            log.info("Loaded {} local knowledge documents from {}", count, knowledgePath);
        } catch (Exception e) {
            log.error("Failed to load local knowledge from {}: {}", knowledgePath, e.getMessage(), e);
        }
    }

    public int ingestFromDirectory(String directoryPath) {
        log.info("Ingesting documents from directory: {}", directoryPath);
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(directoryPath);
        ingestor.ingest(documents);
        log.info("Successfully ingested {} documents", documents.size());
        return documents.size();
    }

    public void ingestSingleDocument(String filePath) {
        log.info("Ingesting document: {}", filePath);
        Document document = FileSystemDocumentLoader.loadDocument(filePath);
        ingestor.ingest(document);
        log.info("Successfully ingested document: {}", filePath);
    }

    public void ingestText(String title, String content) {
        log.info("Ingesting text document: {}", title);
        Metadata metadata = new Metadata();
        metadata.put("title", title);
        Document document = Document.from(content, metadata);
        ingestor.ingest(document);
        log.info("Successfully ingested text document: {}", title);
    }
}
