# 项目进阶知识汇总

说明：
- 下面内容只依据当前代码库、`docs/`、`benchmark/` 和 git 历史整理。
- 能从代码直接证明的写 `事实`。
- 基于代码做出的合理判断写 `推断`。
- 性能数字、个人主导范围、真实业务规模等没有证据的内容统一标记为 `需要本人补充`。

## 1. 这套系统的核心不是“AI 聊天”，而是“业务边界收口”

### 你先要讲清楚的主线
- 公共问答走知识库检索，解决规章制度、办事流程这类“学校通用问题”。
- 学生个人数据走受控工具调用，解决成绩、一卡通余额这类“只能查自己的数据”。
- 管理员查询走独立后台接口，解决“需要全量查询但不能经过学生工具链”的场景。

### 代码证据
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SseChatController.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/EduApiController.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/AdminEduController.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/KnowledgeDocController.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/RagService.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/config/EduAiFunctionConfig.java`

### 面试时可以怎么说
- “这个项目真正难的地方不是把模型接进来，而是把公共知识、学生隐私数据和管理员全量查询拆成三条链路，避免混用同一套入口。”

---

## 2. RAG 的完整链路，不是“问模型”这么简单

### 这条链路做了什么
1. 文档先上传到 OSS。
2. `TikaDocumentReader` 负责把 PDF、Word、TXT、HTML 之类的内容解析成文本。
3. `TokenTextSplitter` 把长文切成 chunk。
4. `applyOverlapAndMetadata` 给 chunk 补上 overlap 和来源信息。
5. `VectorStore` 写入向量。
6. 查询时用 `SearchRequest` 做召回。
7. `buildSystemPrompt` 把召回片段拼进 prompt。
8. `ChatClient` 最终生成回答。

### 代码证据
- `ruoyi-system/src/main/java/com/ruoyi/system/service/RagService.java#importInputStreamToVectorStore`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/RagService.java#retrieveRelevantDocuments`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/RagService.java#buildSystemPrompt`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/AiController.java#chat`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SseChatController.java#publicStreamChat`
- `ruoyi-admin/src/main/java/com/ruoyi/web/config/SpringAiRedisVectorStoreConfig.java`

### 进阶点
- `TOP_K = 3` 和 `similarityThreshold = 0.6d` 本质是在“召回”和“噪声”之间做平衡。
- `fileUrl`、`source`、`fileName`、`chunk` 这些 metadata 不只是为了展示，而是为了溯源和删除精度。
- `AiController#chat` 和 `SseChatController#publicStreamChat` 都做了缓存短路，说明“先查缓存、再检索、再生成”。

### 面试时可以怎么说
- “RAG 在这个项目里不是一句概念，而是一条完整的工程链路，核心是把校内文档先结构化，再把检索结果稳定地注入模型上下文。”

### 追问风险
- `TOP_K` 为什么是 3。
- chunk size 为什么是 800。
- 没有真实 benchmark 时不要硬报命中率或响应时间，属于 `需要本人补充`。

---

## 3. Function Calling 的核心，是把“查什么”和“查谁”分开

### 这部分解决了什么问题
- 模型可以决定“要不要查成绩”，但不能决定“查哪个学生”。
- 学生身份不能由前端参数或 prompt 决定，必须由后端登录态恢复。
- 管理员如果要查全量数据，必须走单独接口，而不是复用学生工具。

### 代码证据
- `ruoyi-admin/src/main/java/com/ruoyi/web/config/EduAiFunctionConfig.java#getStudentScore`
- `ruoyi-admin/src/main/java/com/ruoyi/web/config/EduAiFunctionConfig.java#getCardBalance`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/CurrentStudentService.java`
- `ruoyi-system/src/main/resources/mapper/system/StudentMapper.xml#selectStudentByUserId`
- `sql/patch_student_user_binding.sql`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/EduApiController.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/AdminEduController.java`

### 进阶点
- 工具描述里已经明确写了不要接收 `studentId`。
- `CurrentStudentService` 先拿 `SecurityUtils.getUserId()`，再去查学生绑定关系。
- `ToolContext` 只是补充上下文，不是身份来源本身。
- `SseChatController#resolveForcedToolName` 和 `#handleDirectStudentToolCall` 说明这套实现还支持“单工具直达”。

### 面试时可以怎么说
- “我把模型的能力和身份的决定权拆开了。模型只负责判断要不要调用工具，真正的 `studentId` 永远由后端从登录态里解析。”

### 追问风险
- prompt injection 怎么防。
- 学生账号没绑定时怎么处理。
- 为什么学生工具不能复用管理员接口。

---

## 4. SSE 不是“边说边回”这么简单，关键是生命周期管理

### 这部分解决了什么问题
- 模型回答长，前端不能等完整结果一次性返回。
- 用户中途关闭页面时，后端不能继续白跑。
- 流式过程中出错时，要能清理资源，不要把异常当成普通错误堆日志。

### 代码证据
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SseChatController.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SseChatController.java#StreamLifecycle`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SseChatController.java#safeSend`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/AiController.java`
- `benchmark/security/sse-abort-test.js`
- `docs/benchmark.md`

### 进阶点
- `SseChatController` 同时保存了 `Disposable` 和 `CompletableFuture`，所以公共流和学生流都能被取消。
- `AbortController` 在前端负责中止请求，后端负责真正释放模型流或任务线程。
- `client disconnected` 这类情况更像生命周期事件，不应该和业务错误混为一谈。

### 面试时可以怎么说
- “SSE 的难点不在返回流，而在断连、超时和异常时能不能把上游任务真正停掉。”

### 追问风险
- 如何区分用户主动关闭和服务端故障。
- 取消失败会不会泄漏线程。
- 断连验证和性能压测不是一回事。

---

## 5. Redis 在这个项目里承担了两种角色

### 角色 1：会话记忆
- `RedisChatMemory` 负责保存多轮对话上下文。
- key 以 `conversationId` 为隔离粒度。
- 代码里还限制了历史长度，避免无限膨胀。

### 角色 2：缓存短路
- `AiController#chat` 和 `SseChatController` 都有按问句缓存回答的逻辑。
- 这能减少重复问题的模型调用，但也可能带来缓存污染和 key 归一化问题。

### 代码证据
- `ruoyi-admin/src/main/java/com/ruoyi/web/service/RedisChatMemory.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SseChatController.java#chatMemoryAdvisor`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/AiController.java`

### 进阶点
- `RedisChatMemory` 解决的是“多轮对话不要丢上下文”。
- 问答缓存解决的是“重复问题不要重复消耗模型”。
- 两者不是一回事，不能混讲。

### 面试时可以怎么说
- “Redis 在这里不是单纯的缓存，而是同时承担会话上下文和问答短路两种能力。”

### 追问风险
- TTL 为什么是 7 天。
- 只按 query 缓存会不会串数据。
- `conversationId` 丢了会发生什么。

---

## 6. 向量库实现有回退逻辑，但回退不等于等价

### 这部分做了什么
- 如果 Redis 支持 RediSearch，就用 `RedisVectorStore`。
- 如果环境不支持，就回退到 `SimpleVectorStore`。

### 代码证据
- `ruoyi-admin/src/main/java/com/ruoyi/web/config/SpringAiRedisVectorStoreConfig.java`

### 进阶点
- 这个设计提高了开发可用性，但会改变能力边界。
- 回退到 `SimpleVectorStore` 之后，向量检索更偏内存态，和完整 Redis 向量能力不是一回事。
- 所以面试时不能说“Redis 向量库和简单内存实现完全等价”。

### 面试时可以怎么说
- “我做了环境兼容，但也保留了能力降级路径；它能保证项目能跑，但不代表生产能力完全一致。”

### 追问风险
- 如何发现自己是不是跑在回退模式。
- 生产环境能不能接受这个回退。

---

## 7. 知识库导入和删除，真正难的是一致性

### 导入链路
- `KnowledgeDocController#importFile` 先上传 OSS。
- `KnowledgeDocServiceImpl` 再落库并触发异步导入。
- `RagService#importOssFileToVectorStore` 把文件内容变成 chunk 和向量。

### 删除链路
- `RagService#deleteByFileUrl` 先删向量。
- 再删 OSS。
- 最后删数据库记录。

### 代码证据
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/KnowledgeDocController.java#importFile`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/impl/KnowledgeDocServiceImpl.java#asyncUploadToDifyEngine`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/impl/KnowledgeDocServiceImpl.java#deleteSingleKnowledgeDoc`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/RagService.java#deleteByFileUrl`
- `docs/knowledge-delete-test.md`

### 进阶点
- 删除顺序不能乱，否则会出现“DB 删了但向量还在”或者“文件删了但索引还在”的脏数据。
- 历史数据里可能只有 `source/fileName`，所以代码保留了 fallback。
- 这个 fallback 会带来同名误删风险，属于当前技术债。

### 面试时可以怎么说
- “知识库删除不是一个 CRUD，而是 OSS、向量库和数据库三层一致性的收尾问题。”

### 追问风险
- 为什么不直接先删 DB。
- 同名文件怎么避免误删。
- `docId` 为什么没有成为所有 chunk 的强主键。

---

## 8. 旧链路和新链路并存，说明项目经历过迁移

### 代码证据
- `git commit cb4f59bb`：从 Dify 迁到 Spring AI。
- `git commit 07b4d1b9`：补 RAG、SSE、知识导入、Function Calling。
- `ruoyi-system/src/main/java/com/ruoyi/system/service/AiKnowledgeService.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/RagService.java`

### 进阶点
- `AiKnowledgeService` 看起来是旧实现残留，真正承担当前知识导入和检索的是 `RagService`。
- 面试时如果被问到“为什么不删干净”，要诚实承认这是重构残留，不要包装成“为了兼容所有场景”。

### 面试时可以怎么说
- “这个项目是迁移式演进，不是一次性重写，所以仓库里会留下部分旧实现和过渡痕迹。”

### 追问风险
- 哪些类现在已经没在主链路里用了。
- 迁移过程中丢了什么能力。

---

## 9. 安全、鉴权和日志是这项目里最应该强调的工程化能力

### 鉴权和权限
- `JwtAuthenticationTokenFilter` 负责把 token 变成认证信息。
- `SecurityConfig` 负责全局安全链路。
- `@PreAuthorize` 负责角色级接口控制。

### 日志和异常
- `LogAspect` 会把操作日志写到 `SysOperLog`。
- 敏感字段如密码会被过滤。
- `GlobalExceptionHandler` 统一处理常见异常，客户端断连要特殊看待。

### 额外过滤
- `FilterConfig` 还注册了 XSS、Referer、Repeatable 相关过滤器。

### 代码证据
- `ruoyi-framework/src/main/java/com/ruoyi/framework/config/SecurityConfig.java`
- `ruoyi-framework/src/main/java/com/ruoyi/framework/security/filter/JwtAuthenticationTokenFilter.java`
- `ruoyi-framework/src/main/java/com/ruoyi/framework/web/exception/GlobalExceptionHandler.java`
- `ruoyi-framework/src/main/java/com/ruoyi/framework/aspectj/LogAspect.java`
- `ruoyi-framework/src/main/java/com/ruoyi/framework/config/FilterConfig.java`

### 进阶点
- 这套项目不是只会做功能，是真的有权限边界、操作日志和异常治理。
- 但 `application.yml` 里硬编码敏感信息是当前明显安全债。

### 面试时可以怎么说
- “我会把这项目讲成一个有鉴权、有审计、有异常治理的后端，而不是一个简单的 AI demo。”

### 追问风险
- 哪些异常应该是 401，哪些应该是 403。
- 为什么 client disconnected 不应当按 error 处理。
- 敏感配置为什么不能直接进仓库。

---

## 10. 限流这块要诚实讲，当前不是“完全完善”

### 事实
- `RateLimiterAspect#doBefore()` 当前是空的，说明全局 AOP 限流没有真正生效。
- 真正起作用的更像是 controller 里的 Redis Lua 限流脚本。

### 代码证据
- `ruoyi-framework/src/main/java/com/ruoyi/framework/aspectj/RateLimiterAspect.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/AiController.java#init`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SseChatController.java#init`
- `git commit 18eacb88`

### 进阶点
- 面试时不要说“全局限流已经做好了”，这和当前仓库事实不一致。
- 更稳妥的说法是“项目里有 endpoint 级的 Lua 限流，但全局切面当前处于暂时关闭或未完成状态”。

### 面试时可以怎么说
- “这块我会如实说成技术债，而不是把注释掉的代码说成已经上线。”

---

## 11. 测试和 benchmark 要分清“验证了什么”和“还没验证什么”

### 当前能证明的
- `docs/security-test.md` 里有权限边界验证。
- `docs/knowledge-delete-test.md` 里有删除闭环验证。
- `benchmark/security/sse-abort-test.js` 里有 SSE 断连验证。

### 当前不能证明的
- 不能证明真实 QPS。
- 不能证明 P99。
- 不能证明“响应时间下降了多少百分比”。

### 代码和文档证据
- `docs/security-test.md`
- `docs/knowledge-delete-test.md`
- `docs/benchmark.md`
- `benchmark/security/sse-abort-test.js`
- `ruoyi-admin/src/test/java/test/t1/t1.java`

### 进阶点
- 这里最适合面试时强调“我知道哪些是证据，哪些是需要本人补充”。
- 如果你没有真实压测结果，就不要把体感说成事实。

### 面试时可以怎么说
- “仓库里有安全脚本和 abort 验证，但真实压测和性能数字目前没有完整落盘，所以这些结果我会标记成需要本人补充。”

---

## 12. 这几个地方是当前最明显的技术债

### 明显债务
- `application.yml` 里有敏感配置硬编码。
- `RateLimiterAspect` 实际没生效。
- `SseChatController` 责任有点重，混了缓存、限流、鉴权、RAG、工具分发和生命周期管理。
- `AiKnowledgeService` 和 `RagService` 有重复感，像迁移残留。
- `sql/ry_20260320.sql` 没能在主 SQL 里直接看到 `student`、`knowledge_doc`、`ai_common_qa` 的完整 DDL。
- 自动化测试偏少，更多是手工脚本和文档验证。

### 代码证据
- `ruoyi-admin/src/main/resources/application.yml`
- `ruoyi-framework/src/main/java/com/ruoyi/framework/aspectj/RateLimiterAspect.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SseChatController.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/AiKnowledgeService.java`
- `sql/ry_20260320.sql`

### 面试时可以怎么说
- “我不会把这些问题包装成已解决，而是会把它们当成项目的后续优化方向。”

---

## 13. 你可以直接背的 30 秒总述

- “这个项目本质上不是一个普通 AI 聊天 demo，而是一个把校园公共知识问答、学生个人数据查询和管理员全量查询拆成三条链路的后端系统。”
- “公共问答走 RAG，学生个人数据走后端受控 Function Calling，管理员查询走独立接口，核心是把模型能力和数据权限边界分开。”
- “我重点做的是后端 AI 链路，包括知识库导入检索、SSE 流式返回、Redis 会话记忆和学生身份收口。”
- “这个项目里最难的不是接模型，而是处理越权、断连、删除一致性和可追溯性这些工程问题。”

