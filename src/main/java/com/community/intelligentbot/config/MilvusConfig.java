package com.community.intelligentbot.config;

import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MilvusConfig {

    @Value("${milvus.host}")
    private String host;

    @Value("${milvus.port}")
    private int port;

    @Bean
    public MilvusEmbeddingStore milvusEmbeddingStore() {
        return MilvusEmbeddingStore.builder()
                .host(host)
                .port(port)
                .collectionName("knowledge_base")
                .dimension(1024) // text-embedding-v4 output dimension
                .indexType(IndexType.FLAT)
                .metricType(MetricType.COSINE)
                .consistencyLevel(ConsistencyLevelEnum.EVENTUALLY)
                .build();
    }
}
