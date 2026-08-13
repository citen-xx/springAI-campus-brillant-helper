package com.ruoyi.system.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.ruoyi.system.Oss.AliOssService;
import com.ruoyi.system.domain.KnowledgeDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class RagService
{
    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int MIN_CHUNK_LENGTH_TO_EMBED = 20;
    private static final int MAX_NUM_CHUNKS = 10_000;
    private static final Pattern HEADING_PATTERN = Pattern.compile(
        "^(第[一二三四五六七八九十百零〇0-9]+[编章节条款].*|[一二三四五六七八九十百]+、.*|[0-9]+[.、].*)$");

    private final VectorStore vectorStore;
    private final AliOssService aliOssService;
    private final MeterRegistry meterRegistry;
    private final RagProperties properties;

    public RagService(VectorStore vectorStore, AliOssService aliOssService, MeterRegistry meterRegistry,
        RagProperties properties)
    {
        this.vectorStore = vectorStore;
        this.aliOssService = aliOssService;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    public void importOssFileToVectorStore(KnowledgeDoc knowledgeDoc)
    {
        validateDocument(knowledgeDoc);
        try (InputStream inputStream = aliOssService.getObjectInputStreamByUrl(knowledgeDoc.getFileUrl()))
        {
            List<Document> chunks = parseAndChunk(inputStream, knowledgeDoc);
            if (chunks.isEmpty())
            {
                throw new IllegalStateException("文档没有可入库的文本内容");
            }
            vectorStore.accept(chunks);
            log.info("Knowledge document indexed, docId={}, chunks={}", knowledgeDoc.getDocId(), chunks.size());
        }
        catch (Exception ex)
        {
            throw new RuntimeException("文档向量化失败, docId=" + knowledgeDoc.getDocId(), ex);
        }
    }

    public void replaceDocument(KnowledgeDoc knowledgeDoc)
    {
        deleteByDocumentId(knowledgeDoc.getDocId());
        importOssFileToVectorStore(knowledgeDoc);
    }

    public List<Document> retrieveRelevantDocuments(String query)
    {
        return retrieveRelevantDocuments(query, properties.getTopK());
    }

    public List<Document> retrieveRelevantDocuments(String query, int topK)
    {
        if (query == null || query.isBlank())
        {
            return List.of();
        }
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(Math.max(1, Math.min(topK, 20)))
            .similarityThreshold(properties.getSimilarityThreshold())
            .build();
        List<Document> documents = Timer.builder("campus.ai.rag.retrieval")
            .description("RAG vector retrieval latency")
            .register(meterRegistry)
            .record(() -> vectorStore.similaritySearch(request));
        return documents == null ? List.of() : documents;
    }

    public void deleteByDocumentId(Long docId)
    {
        if (docId == null)
        {
            throw new IllegalArgumentException("docId 不能为空");
        }
        FilterExpressionBuilder filter = new FilterExpressionBuilder();
        vectorStore.delete(filter.eq("docId", docId).build());
        log.info("Vector chunks deleted, docId={}", docId);
    }

    public List<RagSource> toSources(List<Document> documents)
    {
        if (documents == null)
        {
            return List.of();
        }
        return documents.stream().map(document -> new RagSource(
            longMetadata(document, "docId"),
            stringMetadata(document, "fileName"),
            stringMetadata(document, "section"),
            intMetadata(document, "chunkIndex"),
            stringMetadata(document, "sourceUrl"),
            document.getScore()
        )).toList();
    }

    public String buildSystemPrompt(List<Document> retrievedDocuments)
    {
        if (retrievedDocuments == null || retrievedDocuments.isEmpty())
        {
            return """
                你是校园智能助手。当前没有检索到足够的校园知识依据。
                请明确回答“当前知识库中没有检索到足够信息”，不要编造制度内容。
                """;
        }
        String context = retrievedDocuments.stream().map(document -> {
            Map<String, Object> metadata = document.getMetadata();
            return "[docId=" + metadata.get("docId") + ", 文件=" + metadata.get("fileName")
                + ", 章节=" + metadata.get("section") + ", chunk=" + metadata.get("chunkIndex") + "]\n"
                + document.getText();
        }).collect(Collectors.joining("\n\n--------------------\n\n"));

        return """
            你是校园智能助手。请只依据下面检索到的校园知识片段回答。
            片段不足时明确说明知识库信息不足，不要编造事实。
            回答正文不要伪造来源编号；来源由系统另行以结构化数据返回。

            检索片段：
            %s
            """.formatted(context);
    }

    private List<Document> parseAndChunk(InputStream inputStream, KnowledgeDoc knowledgeDoc)
    {
        TikaDocumentReader reader = new TikaDocumentReader(new InputStreamResource(inputStream));
        List<Document> rawDocuments = reader.get();
        if (rawDocuments == null || rawDocuments.isEmpty())
        {
            return List.of();
        }

        List<SectionBlock> sections = new ArrayList<>();
        for (Document rawDocument : rawDocuments)
        {
            sections.addAll(splitSections(rawDocument.getText()));
        }

        TokenTextSplitter tokenSplitter = TokenTextSplitter.builder()
            .withChunkSize(properties.getChunkSize())
            .withMinChunkSizeChars(properties.getMinChunkSizeChars())
            .withMinChunkLengthToEmbed(MIN_CHUNK_LENGTH_TO_EMBED)
            .withMaxNumChunks(MAX_NUM_CHUNKS)
            .withKeepSeparator(true)
            .build();

        List<ChunkDraft> drafts = new ArrayList<>();
        for (SectionBlock section : sections)
        {
            List<Document> split = tokenSplitter.apply(List.of(new Document(section.text())));
            for (Document document : split)
            {
                if (document.getText() != null && !document.getText().isBlank())
                {
                    drafts.add(new ChunkDraft(section.title(), document.getText().trim()));
                }
            }
        }

        List<Document> result = new ArrayList<>(drafts.size());
        String previousText = "";
        String previousSection = "";
        String documentUpdatedAt = knowledgeDoc.getUpdateTime() == null
            ? Instant.now().toString() : knowledgeDoc.getUpdateTime().toInstant().toString();
        for (int index = 0; index < drafts.size(); index++)
        {
            ChunkDraft draft = drafts.get(index);
            String text = draft.text();
            if (!previousText.isBlank() && draft.section().equals(previousSection))
            {
                int overlapStart = Math.max(0, previousText.length() - properties.getOverlapChars());
                text = previousText.substring(overlapStart) + System.lineSeparator() + text;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("docId", knowledgeDoc.getDocId());
            metadata.put("fileName", knowledgeDoc.getDocName());
            metadata.put("sourceUrl", knowledgeDoc.getFileUrl());
            metadata.put("source", knowledgeDoc.getFileUrl());
            metadata.put("documentType", knowledgeDoc.getDocumentType());
            metadata.put("section", draft.section());
            metadata.put("chunkIndex", index);
            metadata.put("updatedAt", documentUpdatedAt);
            metadata.put("contentHash", knowledgeDoc.getContentHash());
            String chunkId = UUID.nameUUIDFromBytes(
                (knowledgeDoc.getDocId() + ":" + knowledgeDoc.getContentHash() + ":" + index)
                    .getBytes(StandardCharsets.UTF_8)).toString();
            result.add(new Document(chunkId, text, metadata));
            previousText = draft.text();
            previousSection = draft.section();
        }
        return result;
    }

    private List<SectionBlock> splitSections(String text)
    {
        if (text == null || text.isBlank())
        {
            return List.of();
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] paragraphs = normalized.split("\\n\\s*\\n|(?m)(?=^第[一二三四五六七八九十百零〇0-9]+[编章节条款])");
        List<SectionBlock> sections = new ArrayList<>();
        String currentTitle = "正文";
        for (String paragraph : paragraphs)
        {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }
            String firstLine = trimmed.lines().findFirst().orElse("").trim();
            if (firstLine.length() <= 80 && HEADING_PATTERN.matcher(firstLine).matches())
            {
                currentTitle = firstLine;
            }
            sections.add(new SectionBlock(currentTitle, trimmed));
        }
        return sections;
    }

    private void validateDocument(KnowledgeDoc document)
    {
        if (document == null || document.getDocId() == null || document.getFileUrl() == null
            || document.getContentHash() == null)
        {
            throw new IllegalArgumentException("文档 docId、fileUrl、contentHash 不能为空");
        }
    }

    private String stringMetadata(Document document, String key)
    {
        Object value = document.getMetadata().get(key);
        return value == null ? null : value.toString();
    }

    private Long longMetadata(Document document, String key)
    {
        Object value = document.getMetadata().get(key);
        return value instanceof Number number ? number.longValue() : value == null ? null : Long.valueOf(value.toString());
    }

    private Integer intMetadata(Document document, String key)
    {
        Object value = document.getMetadata().get(key);
        return value instanceof Number number ? number.intValue() : value == null ? null : Integer.valueOf(value.toString());
    }

    private record SectionBlock(String title, String text)
    {
    }

    private record ChunkDraft(String section, String text)
    {
    }

    public record RagSource(Long docId, String fileName, String section, Integer chunkIndex, String sourceUrl,
                            Double score)
    {
    }
}
