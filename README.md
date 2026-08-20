# 校园智能知识库问答平台

基于 RuoYi-Vue、Spring Boot 3 和独立 Python Agent 的校园知识问答与学生个人业务查询平台。Java 门户负责认证、权限、限流、缓存、会话、文档管理和个人数据查询；Python Agent 负责公共知识检索、Prompt 组装、模型流式生成和学生频道白名单工具选择。

仓库不内置命中率、延迟、缓存命中率、文档数量或成功率结论。所有量化结果必须由 `evaluation/` 中的程序对真实数据和真实环境运行后产生。

## 双服务架构

### 公开知识问答

```text
POST /api/ai/chat/public/stream
  -> Java: 限流、会话 Scope、首轮公开缓存
  -> cache miss: WebClient -> Python /rag/public/stream
  -> Python: 查询向量化、RediSearch Top-K、Prompt、模型流式生成
  -> Java: 透传 answer/sources SSE，写回会话与公开缓存
```

### 学生个人业务

```text
POST /api/ai/chat/student/stream（登录且具有 student 角色）
  -> Java 从 SecurityContext 解析 userId -> Student -> studentId
  -> 成绩/余额确定性命中: Java StudentBusinessToolService 直接查询 MySQL
  -> 其他问题: Java 签发绑定身份和会话的短时 callback token
  -> Python 只能选择成绩/余额白名单工具
  -> Python 携 token 回调 Java /internal/ai/tool/**
  -> Java 校验 token、conversationId 和数据库身份绑定后查询 MySQL
```

Python 不连接 MySQL，不接收或生成 `studentId`/`userId` 工具参数。公开频道不会调用个人工具。Python 不可用时 Java 返回明确错误，不回退到旧 Java 聊天链路。

完整图和时序见 [docs/architecture.md](docs/architecture.md)，迁移决策和运维说明见 [docs/python-agent-migration.md](docs/python-agent-migration.md)。

## 会话、缓存与文档

会话 Redis Key 为：

```text
ai:chat:memory:{public|student}:{userScope}:{conversationId}
```

登录用户使用 `user:{userId}`，匿名用户使用服务端 `HttpSession` ID。Redis List 保留 7 天，最多 100 条，向 Python 发送最近 20 条。SSE 使用 `SseEmitter`，统一处理 60 秒超时、完成、异常、客户端断开和上游取消。

只有公共频道、无历史消息的首轮回答进入 2 小时共享缓存。缓存 Key 包含知识库版本和规范化问题 SHA-256；学生频道与多轮回答不使用共享缓存。公开问答限流 10 次/分钟，学生问答 20 次/分钟，文档上传/替换 5 次/分钟。

Java 文档导入管线保留 Spring AI：

```text
文件 SHA-256 去重 -> OSS -> DB docId -> Tika -> 结构化粗分
-> TokenTextSplitter -> overlap -> Embedding -> Redis VectorStore 写入
```

聊天运行时不再使用 Spring AI `ChatClient`、Function Calling 或 `ChatMemory`。Spring AI 依赖仅服务于文档解析、切分、Embedding 和 Redis VectorStore 写入/删除。

## 环境变量

以 [.env.example](.env.example) 和 [ai-agent/.env.example](ai-agent/.env.example) 为变量清单，将真实值放入本地环境或部署密钥系统，不要提交凭据。

关键变量：

- 公共依赖：`DASHSCOPE_API_KEY`、`DASHSCOPE_BASE_URL`、`DASHSCOPE_EMBEDDING_MODEL`、`VECTOR_REDIS_URI`、`VECTOR_INDEX_NAME`、`VECTOR_KEY_PREFIX`
- Python Agent：`DASHSCOPE_CHAT_MODEL`、`RAG_TOP_K`、`RAG_SIMILARITY_THRESHOLD`、`JAVA_CALLBACK_BASE_URL`、`AI_AGENT_HOST`、`AI_AGENT_PORT`
- Java 门户：`PYTHON_AGENT_BASE_URL`、`PYTHON_AGENT_CONNECT_TIMEOUT_MS`、`PYTHON_AGENT_STREAM_TIMEOUT`、`CALLBACK_TOKEN_SECRET`、`CALLBACK_TOKEN_TTL_SECONDS`
- Java 基础设施：`JWT_SECRET`、`MYSQL_*`、`REDIS_*`、`ALIYUN_OSS_*`、`RAG_CHUNK_SIZE`、`RAG_MIN_CHUNK_SIZE_CHARS`、`RAG_OVERLAP_CHARS`

`CALLBACK_TOKEN_SECRET` 只配置在 Java；Python 仅接收每次请求的短时 token。若旧凭据曾进入 Git 历史，必须到对应平台轮换，修改当前文件不能令历史凭据失效。

## 构建与启动

要求 JDK 17、Maven、Node.js、Python 3.11/3.12、MySQL 8、Redis Stack/RediSearch、阿里云 OSS 和可用的通义千问兼容接口。普通 Redis 不满足向量索引要求，服务不会静默降级。

1. 初始化 MySQL，执行项目 SQL 与 [sql/patch_knowledge_document_identity.sql](sql/patch_knowledge_document_identity.sql)，启动 Redis Stack，并配置环境变量。

2. 创建 Python 环境并安装依赖：

```powershell
cd ai-agent
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
```

3. 先启动 Python Agent：

```powershell
.\start.ps1
```

macOS/Linux 使用 `chmod +x ai-agent/start.sh && ai-agent/start.sh`。启动后可访问 `GET http://127.0.0.1:8090/metrics`；首次检索会严格校验 RediSearch schema。

4. 在仓库根目录构建并启动 Java：

```powershell
mvn -DskipTests compile
mvn -pl ruoyi-admin -am spring-boot:run
```

Java 默认监听 `8080`，并通过 `PYTHON_AGENT_BASE_URL` 访问 Python。Java 启动会装配 Embedding/VectorStore，因此同样需要模型 Key 和 Redis Stack 可用。

5. 启动前端：

```powershell
cd ruoyi-ui
npm install
npm run dev
```

生产构建使用 `npm run build:prod`。

## 指标口径

| 指标 | 采集位置 | 口径与读取方式 |
|---|---|---|
| `campus.ai.rag.retrieval` | Python | 查询向量化 + RediSearch 检索总耗时；`GET :8090/metrics` |
| `campus.ai.llm.stream` | Python | 模型生成迭代开始到结束/取消；`GET :8090/metrics` |
| `campus.ai.chat.first-token` | Python | 模型调用开始到首个非空回答片段；`GET :8090/metrics` |
| `campus.ai.chat.complete` | Java | Java 收到公开请求到 SSE 完成，含缓存和代理；Actuator `/actuator/metrics/campus.ai.chat.complete` |
| 公开缓存 hit/miss | Java + Redis | 真实 `get` 结果递增；管理员 `GET /system/ai/metrics/cache` |
| `campus.ai.llm.call` | Java | 学生频道 Python 工具型调用整体耗时；Actuator |
| `campus.ai.tool.query` | Java | Java 成绩/余额直达查询耗时；Actuator |

Python `/metrics` 的 `count`、`totalSeconds`、`averageSeconds`、`maxSeconds` 来自当前进程真实观测；无样本时平均值和最大值为 `null`。Java 缓存无样本时 `hitRate` 为 `null`。

## 评测与验证

[evaluation/README.md](evaluation/README.md) 给出 Ground Truth、`HitRate@1/@3/@5`、安全回归和性能脚本的真实运行方式。命中率评测器调用 Python `/rag/retrieve` 获取 Top-5，语义仍是“前 K 个来源中任一项匹配人工标注即命中”。正式 Ground Truth 当前为空时评测会直接失败，不会输出 `0%` 或推导数字。

```powershell
python -m unittest discover -s ai-agent/tests -v
mvn -DskipTests test-compile
node --check evaluation/security-regression.mjs
node --check evaluation/performance-benchmark.mjs
```

真实端到端运行还需要两个学生账号、管理员 token、已知真实成绩/余额、非空公开问题集，以及已启动的 MySQL、Redis Stack、OSS、Python、Java 和模型服务。缺少这些条件时只能报告静态/单元验证，不能宣称安全回归、命中率或性能结果。
