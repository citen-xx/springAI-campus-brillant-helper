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
import com.ruoyi.system.Oss.AliOssService;
import com.ruoyi.system.domain.KnowledgeDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

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
    private final RagProperties properties;

    public RagService(VectorStore vectorStore, AliOssService aliOssService, RagProperties properties)
    {
        this.vectorStore = vectorStore;
        this.aliOssService = aliOssService;
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

    private record SectionBlock(String title, String text)
    {
    }

    private record ChunkDraft(String section, String text)
    {
    }

}
