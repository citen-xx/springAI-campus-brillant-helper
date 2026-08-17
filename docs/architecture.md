# 校园智能知识库问答平台架构

## 总体架构

```mermaid
flowchart LR
    USER["Vue 2 用户端 / 管理端"]

    subgraph JAVA["Java 门户 · Spring Boot 3"]
        SECURITY["Security + JWT + RateLimiter"]
        CHAT["SseChatController"]
        CACHE["PublicKnowledgeCacheService"]
        MEMORY["RedisChatMemory"]
        ROUTER["ChatIntentRouter"]
        CALLBACK["AiToolCallbackController"]
        TOOL["StudentBusinessToolService"]
        DOC["KnowledgeDocController + RagService"]
    end

    subgraph PYTHON["Python Agent · FastAPI"]
        API["/rag/public/stream\n/rag/student/stream\n/rag/retrieve"]
        RETRIEVER["RedisVectorRetriever"]
        MODEL["LangChain ChatOpenAI"]
        METRICS["/metrics"]
    end

    MYSQL["MySQL"]
    REDIS["Redis"]
    VECTOR["Redis Stack / RediSearch"]
    OSS["Aliyun OSS"]
    DASHSCOPE["DashScope compatible API"]

    USER --> SECURITY --> CHAT
    CHAT --> ROUTER
    CHAT --> CACHE --> REDIS
    CHAT --> MEMORY --> REDIS
    CHAT --> API
    API --> RETRIEVER --> VECTOR
    RETRIEVER --> DASHSCOPE
    API --> MODEL --> DASHSCOPE
    MODEL --> CALLBACK --> TOOL --> MYSQL
    CHAT --> TOOL
    DOC --> OSS
    DOC --> MYSQL
    DOC --> VECTOR
    API --> METRICS
```

边界约束：

- Java 是公网门户和身份可信边界，负责认证、授权、限流、会话 Scope、缓存及个人数据查询。
- Python 只负责检索、生成和白名单工具选择，不连接 MySQL。
- Spring AI 只存在于 Java 文档导入/维护管线：Tika、`TokenTextSplitter`、`EmbeddingModel`、Redis `VectorStore` 写入/删除。
- Python 服务失败时 Java 明确结束 SSE 并返回错误，不切回旧聊天实现。

## 公开问答时序

```mermaid
sequenceDiagram
    participant FE as Vue
    participant J as Java SseChatController
    participant C as Redis Public Cache
    participant P as Python Agent
    participant V as RediSearch
    participant L as Model API

    FE->>J: POST /api/ai/chat/public/stream
    J->>J: rate limit + scoped conversation
    J->>C: get(version + question hash)
    alt cache hit
        C-->>J: answer + sources
        J-->>FE: SSE answer, sources
    else cache miss
        J->>P: POST /rag/public/stream + recent history
        P->>V: embed query + KNN Top-K
        V-->>P: chunks + scores
        P->>L: prompt + history + chunks
        L-->>P: answer deltas
        P-->>J: SSE answer deltas, sources
        J->>C: put(answer + real sources)
        J-->>FE: SSE answer, sources
    end
```

来源由检索结果转换，不由模型生成。Java 的 `campus.ai.chat.complete` 包含缓存和代理全程；Python 分别记录检索、模型流和首回答片段耗时。

## 学生频道与 callback 安全模型

```mermaid
sequenceDiagram
    participant FE as Student
    participant J as Java Portal
    participant DB as MySQL
    participant P as Python Agent
    participant CB as Java Callback API

    FE->>J: JWT + POST /api/ai/chat/student/stream
    J->>DB: userId -> Student -> studentId
    alt 确定性成绩/余额意图
        J->>DB: query by server-resolved studentId
        DB-->>J: result
        J-->>FE: SSE answer
    else 其他学生问题
        J->>J: issue 60-120s callback token
        J->>P: question + token + scoped conversationId
        P->>P: model chooses allow-listed tool
        P->>CB: token + conversationId + subject/empty body
        CB->>CB: verify signature, expiry, identity and conversation
        CB->>DB: re-check user binding and query
        DB-->>CB: result
        CB-->>P: structured tool result
        P-->>J: SSE answer, sources
        J-->>FE: SSE answer, sources
    end
```

成绩工具只允许 `subject`，余额工具没有参数。callback 请求体不是身份来源；Java 使用 token 中的 `userId/studentId` 与数据库绑定再次比对。公开入口不会签发 callback token。

## 文档导入与索引

```mermaid
sequenceDiagram
    participant A as Admin
    participant J as Java KnowledgeDocService
    participant O as OSS
    participant T as Tika + TokenTextSplitter
    participant E as Embedding API
    participant V as Redis VectorStore
    participant DB as MySQL

    A->>J: import/replace file
    J->>J: SHA-256 duplicate check
    J->>O: upload source file
    J->>DB: persist stable docId + metadata
    J->>O: read InputStream
    J->>T: parse, section split, chunk, overlap
    J->>E: embed chunks
    J->>V: accept chunks with metadata
    J->>J: increment knowledge-base version
```

Chunk metadata 包含 `docId`、`fileName`、`sourceUrl`、`documentType`、`section`、`chunkIndex`、`updatedAt`、`contentHash`。Python 首次检索时通过 `FT.INFO` 校验索引类型、前缀、字段、向量维度、算法和距离度量，不创建新索引也不静默降级。

## 会话与 SSE 生命周期

```text
ai:chat:memory:{public|student}:{user:{userId}|anonymous:{sessionId}}:{conversationId}
```

Java 读取最近 20 条、最多保存 100 条，TTL 7 天。自有 `ChatMessage` DTO 保持原有 Redis JSON `type/text` 格式兼容。SSE 超时、完成、异常、发送失败和客户端断开统一关闭生命周期，并取消 Reactor 订阅或异步 Future。

## 指标归属

| 指标 | Owner | 起止点 |
|---|---|---|
| `campus.ai.rag.retrieval` | Python | `retrieve()` 调用开始至 Embedding + RediSearch 完成/失败 |
| `campus.ai.llm.stream` | Python | 模型生成开始至迭代结束、异常或取消 |
| `campus.ai.chat.first-token` | Python | 模型生成开始至首个非空回答片段 |
| `campus.ai.chat.complete` | Java | 公开 HTTP/SSE 请求进入至缓存命中或 Python 流完整完成 |
| cache hit/miss | Java + Redis | 公开首轮缓存真实读取结果 |
| `campus.ai.llm.call` | Java | 学生频道 Python 工具型请求整体完成/失败 |
| `campus.ai.tool.query` | Java | 确定性成绩/余额查询 |

Python 指标从 `GET /metrics` 读取；Java Timer 从 Actuator `/actuator/metrics/{name}` 读取；缓存计数从管理员接口 `/system/ai/metrics/cache` 读取。

## 关键代码

- Java 聊天入口：`ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SseChatController.java`
- Python 客户端：`ruoyi-admin/src/main/java/com/ruoyi/web/service/PythonPublicRagClient.java`
- callback：`ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/AiToolCallbackController.java`
- 学生查询：`ruoyi-system/src/main/java/com/ruoyi/system/service/StudentBusinessToolService.java`
- 文档管线：`ruoyi-system/src/main/java/com/ruoyi/system/service/RagService.java`
- Python API/指标：`ai-agent/app/main.py`、`ai-agent/app/service.py`、`ai-agent/app/metrics.py`
- Python 检索：`ai-agent/app/vector_store.py`
