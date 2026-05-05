package com.ruoyi.system.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.ruoyi.system.Oss.AliOssService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * RAG 工作流服务
 *
 * 职责：
 * 1. 从 OSS / 本地输入流读取文档
 * 2. 使用 Tika 解析文本
 * 3. 对长文本进行切片和重叠拼接
 * 4. 写入 Redis 向量库
 * 5. 聊天时执行向量检索并将检索结果作为系统提示词注入模型
 */
@Service
public class RagService
{
    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    /**
     * 经验值：
     * chunkSize 不宜过大，否则召回精度下降；也不宜过小，否则上下文碎片化严重。
     * 这里先取一个适中的配置，后续可按业务语料继续调优。
     */
    private static final int CHUNK_SIZE = 800;
    private static final int MIN_CHUNK_SIZE_CHARS = 350;
    private static final int MIN_CHUNK_LENGTH_TO_EMBED = 10;
    private static final int MAX_NUM_CHUNKS = 10_000;
    private static final int OVERLAP_CHARS = 120;
    private static final int TOP_K = 3;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final AliOssService aliOssService;

    public RagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder, AliOssService aliOssService)
    {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        this.aliOssService = aliOssService;
    }

    /**
     * 从 OSS URL 读取文档并写入向量库
     *
     * @param fileUrl OSS 文件访问 URL
     */
    public void importOssFileToVectorStore(String fileUrl)
    {
        String fileName = extractFileName(fileUrl);
        try (InputStream inputStream = aliOssService.getObjectInputStreamByUrl(fileUrl))
        {
            importInputStreamToVectorStore(inputStream, fileName);
        }
        catch (Exception e)
        {
            throw new RuntimeException("导入 OSS 文档到向量库失败: " + fileUrl, e);
        }
    }

    /**
     * 从本地 / 任意输入流读取文档并写入向量库
     *
     * @param inputStream 输入流
     * @param fileName 文件名
     */
    public void importInputStreamToVectorStore(InputStream inputStream, String fileName)
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
            InputStreamResource resource = new InputStreamResource(source);

            // 1. 使用 Tika 解析 PDF / Word / TXT / HTML 等文本内容
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> rawDocuments = reader.get();
            if (rawDocuments == null || rawDocuments.isEmpty())
            {
                log.warn("Tika 未解析出有效文本，fileName={}", fileName);
                return;
            }

            // 2. 使用 TokenTextSplitter 做基础切片
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(CHUNK_SIZE)
                .withMinChunkSizeChars(MIN_CHUNK_SIZE_CHARS)
                .withMinChunkLengthToEmbed(MIN_CHUNK_LENGTH_TO_EMBED)
                .withMaxNumChunks(MAX_NUM_CHUNKS)
                .withKeepSeparator(true)
                .build();
            List<Document> chunkedDocuments = splitter.apply(rawDocuments);
            if (chunkedDocuments == null || chunkedDocuments.isEmpty())
            {
                log.warn("文档切片结果为空，fileName={}", fileName);
                return;
            }

            // 3. 手动追加 overlap，增强跨段语义连续性
            List<Document> enrichedDocuments = applyOverlapAndMetadata(chunkedDocuments, fileName);

            // 4. 写入 Redis 向量库
            vectorStore.accept(enrichedDocuments);
            log.info("文档已向量化并写入 Redis 向量库，fileName={}, chunks={}", fileName, enrichedDocuments.size());
        }
        catch (Exception e)
        {
            throw new RuntimeException("导入文档到向量库失败: " + fileName, e);
        }
    }

    /**
     * 示例：聊天前先检索相关片段，再把片段作为 System Prompt 喂给模型。
     *
     * @param query 用户问题
     * @return 最终回答
     */
    public String answerWithRag(String query)
    {
        List<Document> retrievedDocuments = retrieveRelevantDocuments(query);
        String systemPrompt = buildSystemPrompt(retrievedDocuments);

        return chatClient.prompt()
            .system(systemPrompt)
            .user(query)
            .call()
            .content();
    }

    /**
     * 流式版本示例：适合 SSE 对话接口直接复用。
     *
     * @param query 用户问题
     * @return 流式文本片段
     */
    public Flux<String> streamAnswerWithRag(String query)
    {
        List<Document> retrievedDocuments = retrieveRelevantDocuments(query);
        String systemPrompt = buildSystemPrompt(retrievedDocuments);

        return chatClient.prompt()
            .system(systemPrompt)
            .user(query)
            .stream()
            .content();
    }

    /**
     * 向量检索示例：先做相似度搜索，再把召回结果拼接成系统提示词。
     *
     * @param query 用户问题
     * @return Top-K 相关片段
     */
    public List<Document> retrieveRelevantDocuments(String query)
    {
        SearchRequest searchRequest = SearchRequest.builder()
            .query(query)
            .topK(TOP_K)
            .similarityThreshold(0.6d)
            .build();
        return vectorStore.similaritySearch(searchRequest);
    }

    private List<Document> applyOverlapAndMetadata(List<Document> chunkedDocuments, String fileName)
    {
        List<Document> result = new ArrayList<>(chunkedDocuments.size());
        String previousChunkText = "";

        for (int i = 0; i < chunkedDocuments.size(); i++)
        {
            Document current = chunkedDocuments.get(i);
            String currentText = current.getText() == null ? "" : current.getText();

            if (currentText.isBlank())
            {
                continue;
            }

            String overlappedText = currentText;
            if (!previousChunkText.isBlank())
            {
                int start = Math.max(0, previousChunkText.length() - OVERLAP_CHARS);
                String overlap = previousChunkText.substring(start);
                overlappedText = overlap + System.lineSeparator() + currentText;
            }

            Map<String, Object> metadata = new HashMap<>(current.getMetadata());
            metadata.put("fileName", fileName);
            metadata.put("source", fileName);
            metadata.put("chunkIndex", i);
            metadata.put("chunkSize", currentText.length());

            result.add(new Document(overlappedText, metadata));
            previousChunkText = currentText;
        }

        return result;
    }

    public String buildSystemPrompt(List<Document> retrievedDocuments)
    {
        if (retrievedDocuments == null || retrievedDocuments.isEmpty())
        {
            return """
                你是校园智能助手。
                当前未检索到可用知识片段，请仅在有把握时回答；
                如果缺少依据，请明确说明“当前知识库中没有检索到足够信息”。
                """;
        }

        String context = retrievedDocuments.stream()
            .map(document -> {
                Object fileName = document.getMetadata().getOrDefault("fileName", "unknown");
                Object chunkIndex = document.getMetadata().getOrDefault("chunkIndex", -1);
                return "[来源文件=" + fileName + ", chunk=" + chunkIndex + "]\n" + document.getText();
            })
            .collect(Collectors.joining("\n\n--------------------\n\n"));

        return """
            你是校园智能助手，请优先依据下面检索到的知识片段回答用户问题。
            如果知识片段已经足够支撑答案，请直接给出简洁、准确、结构化的回复。
            如果知识片段不足以支撑结论，请明确说明“不确定”或“知识库信息不足”，不要编造事实。

            已检索到的知识片段如下：
            %s
            """.formatted(context);
    }

    private String extractFileName(String fileUrl)
    {
        int index = fileUrl.lastIndexOf('/');
        return index >= 0 ? fileUrl.substring(index + 1) : fileUrl;
    }
}
