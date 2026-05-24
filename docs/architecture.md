# 校园智能知识库助手架构与核心链路图

## 1. 系统总体架构图

```mermaid
flowchart LR
    U["用户"]
    FE["前端 chat.vue / edu-tools.vue"]

    subgraph CTRL["Controller"]
        PUB["SseChatController public/stream"]
        STU["SseChatController student/stream"]
        LEGACY["SseChatController stream (legacy public)"]
        EDU["EduApiController"]
        ADMIN["AdminEduController"]
        DOC["KnowledgeDocController"]
    end

    subgraph APP["Service / Tool / AI"]
        CURRENT["CurrentStudentService"]
        RAG["RagService"]
        CHAT["Spring AI ChatClient"]
        MEMORY["RedisChatMemory"]
        TOOL["EduAiFunctionConfig"]
        OSSSVC["AliOssService"]
    end

    subgraph DATA["Storage / Infra"]
        REDIS["Redis"]
        VECTOR["VectorStore"]
        DB["MySQL + MyBatis"]
        OSS["Aliyun OSS"]
        LLM["LLM API"]
    end

    U --> FE
    FE --> PUB
    FE --> STU
    FE --> LEGACY
    FE --> EDU
    FE --> ADMIN
    FE --> DOC

    STU --> CURRENT
    EDU --> CURRENT
    CURRENT --> DB

    PUB --> RAG
    STU --> RAG
    LEGACY --> RAG
    DOC --> OSSSVC
    DOC --> RAG

    PUB --> CHAT
    STU --> CHAT
    LEGACY --> CHAT
    CHAT --> MEMORY
    MEMORY --> REDIS
    CHAT --> TOOL
    TOOL --> CURRENT
    TOOL --> DB
    CHAT --> LLM

    ADMIN --> DB
    ADMIN --> CURRENT
    RAG --> VECTOR
    VECTOR --> REDIS
    RAG --> OSSSVC
    OSSSVC --> OSS
    DOC --> DB
```

代码路径：

- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SseChatController.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/EduApiController.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/AdminEduController.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/KnowledgeDocController.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/CurrentStudentService.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/RagService.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/config/EduAiFunctionConfig.java`

## 2. RAG 问答时序图

```mermaid
sequenceDiagram
    participant U as 用户
    participant C as SseChatController
    participant R as RagService
    participant V as VectorStore
    participant AI as ChatClient
    participant LLM as 大模型

    U->>C: 提问
    C->>R: retrieveRelevantDocuments(question)
    R->>V: similaritySearch(topK=3, threshold=0.6)
    V-->>R: 相关文档片段
    R-->>C: documents
    C->>R: buildSystemPrompt(documents)
    R-->>C: systemPrompt
    C->>AI: prompt + systemPrompt
    AI->>LLM: 流式调用
    LLM-->>AI: ChatResponse
    AI-->>C: Flux<ChatResponse>
    C-->>U: SSE 增量返回
```

代码路径：

- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SseChatController.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/RagService.java`

证据不足说明：

- 当前仓库未发现“准确率 35% -> 88%”评测数据。

## 3. 文档上传入库时序图

```mermaid
sequenceDiagram
    participant U as 用户
    participant C as KnowledgeDocController
    participant OSSSVC as AliOssService
    participant OSS as OSS
    participant R as RagService
    participant V as VectorStore
    participant DB as KnowledgeDocMapper

    U->>C: 上传文档
    C->>OSSSVC: upload(file)
    OSSSVC->>OSS: putObject
    OSS-->>OSSSVC: fileUrl
    C->>DB: 保存文档记录
    C->>R: importOssFileToVectorStore(fileUrl)
    R->>OSSSVC: getObjectInputStreamByUrl(fileUrl)
    OSSSVC->>OSS: getObject
    OSS-->>R: InputStream
    R->>R: 解析 + 切块 + 向量化
    R->>V: vectorStore.accept(documents)
    V-->>R: 入库完成
    C->>DB: 更新状态
    C-->>U: 返回结果
```

代码路径：

- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/KnowledgeDocController.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/Oss/AliOssService.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/RagService.java`

证据不足说明：

- 当前代码更接近固定 `CHUNK_SIZE=800` 与 `OVERLAP_CHARS=120`，不要讲成 `500-800 Token` 动态滑窗。

## 4. Function Calling 工具调用时序图

```mermaid
sequenceDiagram
    participant U as 学生用户
    participant C as SseChatController
    participant CS as CurrentStudentService
    participant AI as ChatClient
    participant TOOL as EduAiFunctionConfig
    participant DB as MySQL

    U->>C: POST /api/ai/chat/student/stream
    C->>CS: requireCurrentStudent()
    CS-->>C: currentStudent
    C->>CS: buildToolContext()
    CS-->>C: currentStudentId
    C->>AI: functions(getStudentScore, getCardBalance)
    AI->>TOOL: 调用工具
    TOOL->>CS: requireCurrentStudentId(toolContext)
    CS-->>TOOL: currentStudentId
    TOOL->>DB: 查成绩/余额
    DB-->>TOOL: 数据
    TOOL-->>AI: 工具结果
```

代码路径：

- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SseChatController.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/config/EduAiFunctionConfig.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/CurrentStudentService.java`

说明：

- 学生工具已去身份入参。
- 工具不会信任模型传入的 `studentId`。

## 5. SSE 流式响应时序图

```mermaid
sequenceDiagram
    participant FE as 前端
    participant C as SseChatController
    participant E as SseEmitter
    participant AI as ChatClient
    participant LLM as 大模型

    FE->>C: 发起 stream 请求
    C->>E: new SseEmitter(60000)
    C-->>FE: 建立 SSE 连接
    C->>AI: stream().chatResponse()
    AI->>LLM: 调用模型
    LLM-->>AI: 流式返回
    AI-->>C: ChatResponse
    C->>E: 发送 delta
    E-->>FE: data: {"event":"message","answer":"..."}
```

代码路径：

- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SseChatController.java`
- `ruoyi-ui/src/views/ai/chat.vue`

证据不足说明：

- 当前代码已实现 SSE 生命周期管理：每个请求统一持有 `SseEmitter`、`Flux` 订阅 `Disposable`、异步任务 `CompletableFuture`。
- 前端 `chat.vue` 已使用 `AbortController`，在页面离开、重复发送和“停止生成”时主动中止请求。
- `public / legacy chat` 断连、超时、异常时会统一 `close` 并尝试 `dispose` 上游 `Flux`。
- `student chat` 因为是“单次工具分发 + SSE 单条回推”，主要是 `cancel Future` 并阻止后续回推，不是逐 token 工具流。
- 这是资源释放优化，不改变学生 / 管理员权限边界，也不改变 `studentId` 绑定策略。
- 当前仓库未发现“首字响应 500ms”压测证据。

## 6. Redis ChatMemory 时序图

```mermaid
sequenceDiagram
    participant C as SseChatController
    participant M as RedisChatMemory
    participant R as Redis

    C->>M: get(conversationId, 20)
    M->>R: 读取最近消息
    R-->>M: history
    M-->>C: history
    C->>M: add(conversationId, messages)
    M->>R: rightPushAll
    M->>R: expire(7 days)
    M->>R: trim(max 100)
```

代码路径：

- `ruoyi-admin/src/main/java/com/ruoyi/web/service/RedisChatMemory.java`

证据不足说明：

- 当前真实实现是“最近 20 条读取 + 最多 100 条存储 + 7 天 TTL”，不是 `4096 Token` 窗口。

## 7. 管理员全量查询链路

```mermaid
sequenceDiagram
    participant A as Admin
    participant FE as edu-tools.vue
    participant C as AdminEduController
    participant SM as StudentMapper
    participant SSM as StudentScoreMapper
    participant CCM as CampusCardMapper
    participant DB as MySQL

    A->>FE: 输入 studentId
    FE->>C: GET /system/admin/edu/score or /card/balance
    C->>C: @PreAuthorize("@ss.hasRole('admin')")
    C->>SM: selectStudentByStudentId(studentId)
    SM->>DB: 查 student
    DB-->>SM: student
    alt 查成绩
        C->>SSM: selectScoresByStudentId(studentId)
        SSM->>DB: 查 student_score
        DB-->>SSM: scores
    else 查余额
        C->>CCM: selectBalanceByStudentId(studentId)
        CCM->>DB: 查 campus_card_account
        DB-->>CCM: balance
    end
    C-->>FE: AjaxResult
```

代码路径：

- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/AdminEduController.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/mapper/StudentScoreMapper.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/mapper/CampusCardMapper.java`
- `ruoyi-ui/src/views/ai/edu-tools.vue`
- `ruoyi-ui/src/api/system/edu.js`

说明：

- 这条链路是管理员独立后台接口。
- 不经过学生 Function Calling 工具链。

## 8. 面试现场 1 分钟画图版

### 8.1 总体图

`前端 -> Controller -> RAG / Tool / ChatMemory -> LLM / Redis / DB / OSS`

口头顺序：

1. 先讲聊天入口和文档入库入口。
2. 再讲公共 RAG、学生工具和 Redis 会话。
3. 最后补管理员独立查询接口。

### 8.2 权限边界图

`public chat -> 只做公共问答`

`student chat -> CurrentStudentService -> 学生工具 -> 只查自己`

`admin api -> admin role -> 按 studentId 查全量`

口头顺序：

1. 先讲为什么拆三条入口。
2. 再讲学生侧为什么不信任 `studentId`。
3. 最后讲管理员为什么必须走独立后台接口。
## 9. 知识库删除闭环

当前知识库删除链路已经补成“外部资源先删，数据库最后删”：

1. 后端根据 `docId` 查询 `knowledge_doc`
2. 从记录里的 `fileUrl` 提取文件定位信息
3. `RagService.deleteByFileUrl(fileUrl)` 按向量 metadata 删除对应 chunk
4. `AliOssService.deleteObjectByUrl(fileUrl)` 删除 OSS 源文件
5. 外部资源都成功后，再删除 `knowledge_doc` 数据库记录

这么做的原因是：

- 如果先删 DB，再删外部资源失败，就会失去定位信息
- OSS 和 Redis Vector Store 不在同一个本地事务里，不适合硬做分布式事务
- 当前更稳的策略是“尽力删除 + 明确日志 + DB 最后删除”
