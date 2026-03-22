package com.community.intelligentbot.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class DocumentIngestionService {

    private final EmbeddingStoreIngestor ingestor;

    public DocumentIngestionService(EmbeddingModel embeddingModel, MilvusEmbeddingStore embeddingStore) {
        this.ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(800, 200))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
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
}
