# 校园智能知识库问答平台

基于 RuoYi、Spring Boot 3、Spring AI、通义千问兼容接口、Redis/RediSearch、阿里云 OSS、MyBatis 和 Vue 2 的校园知识问答与学生个人业务查询平台。

项目只保留一条 AI 主链路：公开知识进入 RAG，成绩和一卡通余额进入受服务端身份约束的业务 Tool。仓库不内置命中率、延迟、缓存命中率、文档数量或成功率结论，所有量化结果必须由 `evaluation/` 中的程序对真实数据运行后产生。

## 主要链路

### 公开知识问答

```text
POST /api/ai/chat/public/stream
  -> 规则 Router: PUBLIC_KNOWLEDGE
  -> 首轮公开答案缓存（知识库版本 + 规范化问题 SHA-256）
  -> Redis VectorStore Top-K 检索
  -> 检索片段注入 System Prompt
  -> 通义千问流式生成
  -> SSE answer 事件
  -> SSE sources 事件（docId/fileName/section/chunkIndex/sourceUrl/score）
```

### 学生个人业务

```text
POST /api/ai/chat/student/stream（必须登录且具有 student 角色）
  -> 规则 Router
  -> STUDENT_SCORE: 仅接收 subject
  -> CARD_BALANCE: 不接收身份参数
  -> 服务端从 SecurityContext 重新解析 userId -> Student -> studentId
  -> Service/Tool 再次校验 ToolContext 与当前学生一致
  -> 查询 MySQL 并经 SSE 返回
```

“Java 是什么”不会因包含 Java 被识别为成绩查询；“我的成绩”缺少课程时会要求补充课程。该 Router 是集中管理的确定性规则，不是机器学习分类器。

## 会话与 SSE

客户端 `conversationId` 只用于区分当前用户的不同对话，最终 Redis Key 由服务端统一生成：

```text
ai:chat:memory:{public|student}:{userScope}:{conversationId}
```

登录用户的 `userScope` 为 `user:{userId}`；匿名用户使用服务端 `HttpSession` ID。相同 `conversationId` 在不同用户、匿名会话、公开/学生频道之间不会共享上下文。Redis List TTL 为 7 天，最多保留 100 条消息；新建对话前，前端会调用对应 `DELETE /api/ai/chat/{channel}/conversations/{conversationId}` 清理旧数据。

SSE 使用 `SseEmitter`，并统一处理 60 秒超时、正常完成、异常、客户端断开、Reactor `Disposable.dispose()` 和异步 `Future.cancel(true)`。线程池任务使用 `DelegatingSecurityContextRunnable` 传播登录上下文。

## 文档入库与更新

新增文档只能通过 `POST /system/knowledge/import-file`，更新文件通过 `PUT /system/knowledge/{docId}/file`。两者都要求后台权限并使用 Redis 限流。

```text
文件 SHA-256 去重
  -> 上传 OSS
  -> DB 生成稳定 docId
  -> Tika 解析
  -> 标题/章节/条款/自然段粗分
  -> 超长段落 TokenTextSplitter
  -> 适量 overlap
  -> Embedding
  -> Redis VectorStore
```

Chunk Metadata 包含 `docId`、`fileName`、`sourceUrl`、`documentType`、`section`、`chunkIndex`、`updatedAt` 和 `contentHash`。不能稳定解析页码时不生成虚假页码。更新会按 `docId` 删除旧向量后重建；删除时先移除向量和 DB 记录，再以可重试清理方式删除 OSS 对象。每次成功导入、更新或删除都会递增知识库版本，使旧公开答案缓存自然失效。

数据库升级前执行 [patch_knowledge_document_identity.sql](sql/patch_knowledge_document_identity.sql)，为 `knowledge_doc` 增加内容哈希、文档类型和唯一索引。

## 缓存、限流与指标

只有公开知识首轮回答可进入 2 小时共享缓存。成绩、一卡通余额、学生频道和多轮上下文回答不调用该缓存。缓存 hit/miss 保存为 Redis 计数器，后台可通过 `GET /system/ai/metrics/cache` 查看真实值。

公开问答限流 10 次/分钟，学生问答 20 次/分钟，知识文档上传/替换 5 次/分钟。限流使用项目原有 Redis Lua 机制；匿名请求按 IP，登录请求按用户 ID。

Actuator 暴露 `health`、`info` 和 `metrics`，Micrometer 记录：

- `campus.ai.rag.retrieval`：向量检索耗时
- `campus.ai.chat.first-token`：首 Token 延迟
- `campus.ai.llm.stream`：流式模型调用耗时
- `campus.ai.llm.call`：学生频道带 Tool 能力的模型调用耗时
- `campus.ai.chat.complete`：公开 RAG 完整响应耗时
- `campus.ai.tool.query`：成绩/余额 Tool 查询耗时

## 配置

复制 [.env.example](.env.example) 中的变量名到本地环境或部署密钥管理系统。关键变量包括：

- `DASHSCOPE_API_KEY`、`DASHSCOPE_BASE_URL`
- `JWT_SECRET`
- `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`
- `REDIS_HOST`、`REDIS_PORT`、`REDIS_DATABASE`、`REDIS_PASSWORD`
- `VECTOR_REDIS_URI`、`VECTOR_INDEX_NAME`、`VECTOR_KEY_PREFIX`
- `ALIYUN_OSS_ACCESS_KEY_ID`、`ALIYUN_OSS_ACCESS_KEY_SECRET`、`ALIYUN_OSS_BUCKET_NAME`
- `RAG_CHUNK_SIZE`、`RAG_MIN_CHUNK_SIZE_CHARS`、`RAG_OVERLAP_CHARS`、`RAG_TOP_K`、`RAG_SIMILARITY_THRESHOLD`

不要提交真实凭据。若旧凭据曾进入 Git 历史，仅修改当前配置不会令旧凭据失效，必须人工到对应平台轮换并视情况清理历史。

## 构建

环境要求：JDK 17、Maven、Node.js、MySQL 8、支持 RediSearch 的 Redis、阿里云 OSS 和可用的通义千问兼容接口。

```bash
mvn -DskipTests compile
cd ruoyi-ui && npm install && npm run build:prod
```

本次仓库内测试脚本按要求只做了 `test-compile`，没有执行测试，也没有启动 MySQL、Redis/RediSearch、OSS 或模型集成环境。

## 评测

[evaluation/README.md](evaluation/README.md) 说明了人工 Ground Truth 格式、`HitRate@1/@3/@5` 评测器、失败案例输出、安全/SSE 回归脚本和带预热的延迟统计脚本。当前 Ground Truth 正式文件为空，必须导入原先真实人工标注的 500 条数据后才能引用 Top-3 命中率；不能从示例数据推导任何简历数字。

项目已经具备 Java 后端实习和 AI 应用项目展示所需的核心闭环。下一步应优先补真实数据和真实集成验证，不应继续为了增加关键词引入 LangGraph、MCP、Multi-Agent、Kafka、Milvus 或 Elasticsearch。
