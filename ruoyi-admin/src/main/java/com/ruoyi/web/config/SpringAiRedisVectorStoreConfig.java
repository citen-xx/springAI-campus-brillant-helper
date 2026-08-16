package com.ruoyi.web.config;

import java.net.URI;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPooled;

/**
 * Spring AI Redis Vector Store 显式配置
 */
@Configuration
public class SpringAiRedisVectorStoreConfig
{
    private static final Logger log = LoggerFactory.getLogger(SpringAiRedisVectorStoreConfig.class);

    @Bean
    public JedisPooled jedisPooled(@Value("${spring.ai.vectorstore.redis.uri}") String redisUri)
    {
        return new JedisPooled(URI.create(redisUri));
    }

    @Bean
    public VectorStore vectorStore(
        JedisPooled jedisPooled,
        EmbeddingModel embeddingModel,
        @Value("${spring.ai.vectorstore.redis.index:campus_knowledge_v2}") String index,
        @Value("${spring.ai.vectorstore.redis.prefix:campus_knowledge_v2}") String prefix,
        @Value("${spring.ai.vectorstore.redis.initialize-schema:true}") boolean initializeSchema)
    {
        try
        {
            // 先主动探测 RediSearch 能力，普通 Redis 不支持 FT._LIST
            jedisPooled.ftList();

            log.info("Detected Redis Stack / RediSearch, using RedisVectorStore.");
            return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(index)
                .prefix(prefix)
                .metadataFields(
                    MetadataField.numeric("docId"),
                    MetadataField.tag("documentType"),
                    MetadataField.tag("contentHash"),
                    MetadataField.text("fileName"),
                    MetadataField.text("section"),
                    MetadataField.text("sourceUrl"))
                .initializeSchema(initializeSchema)
                .build();
        }
        catch (Exception ex)
        {
            throw new IllegalStateException(
                "Redis VectorStore requires Redis Stack / RediSearch; FT._LIST capability check failed", ex);
        }
    }
}
