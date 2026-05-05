package com.ruoyi.system.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

/**
 * AI 知识库向量化服务
 *
 * 负责把 OSS 文件流解析、切片并写入 Redis 向量库。
 *
 * @author ruoyi
 */
@Service
public class AiKnowledgeService
{
    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeService.class);

    private final VectorStore vectorStore;

    public AiKnowledgeService(VectorStore vectorStore)
    {
        this.vectorStore = vectorStore;
    }

    /**
     * 导入 OSS 文件到向量库。
     *
     * @param inputStream OSS 文件流
     * @param fileName 文件名
     */
    public void importOssFileToVectorStore(InputStream inputStream, String fileName)
    {
        if (inputStream == null)
        {
            throw new IllegalArgumentException("inputStream 不能为空");
        }
        if (fileName == null || fileName.isBlank())
        {
            throw new IllegalArgumentException("fileName 不能为空");
        }

        try (InputStream source = inputStream)
        {
            // 1. 使用 Tika 读取 OSS 文件流中的文本内容
            InputStreamResource resource = new InputStreamResource(source);
            TikaDocumentReader documentReader = new TikaDocumentReader(resource);
            List<Document> rawDocuments = documentReader.get();

            if (rawDocuments == null || rawDocuments.isEmpty())
            {
                log.warn("文件解析结果为空，fileName={}", fileName);
                return;
            }

            // 2. 使用 TokenTextSplitter 进行文本切片
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> chunkedDocuments = splitter.apply(rawDocuments);

            if (chunkedDocuments == null || chunkedDocuments.isEmpty())
            {
                log.warn("文件切片结果为空，fileName={}", fileName);
                return;
            }

            // 3. 给每个切片补充文件名等元数据
            List<Document> enrichedDocuments = chunkedDocuments.stream()
                .map(document -> {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("fileName", fileName);
                    metadata.put("source", fileName);
                    return new Document(document.getText(), metadata);
                })
                .toList();

            // 4. 写入 Redis 向量库
            vectorStore.accept(enrichedDocuments);

            log.info("OSS 文件已成功向量化并写入向量库，fileName={}, chunks={}", fileName, enrichedDocuments.size());
        }
        catch (IOException e)
        {
            throw new RuntimeException("读取 OSS 文件流失败: " + fileName, e);
        }
        catch (Exception e)
        {
            throw new RuntimeException("导入知识库向量库失败: " + fileName, e);
        }
    }
}
