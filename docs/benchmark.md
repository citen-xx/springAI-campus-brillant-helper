# 校园智能知识库助手压测与性能说明

## 1. 压测目标

当前项目最值得压测的链路有五类：

1. SSE 长连接稳定性
2. 首字响应时间
3. RAG 问答总耗时
4. Redis ChatMemory 读写与截断耗时
5. 管理员教务接口和学生自助接口的安全边界

说明：

- 当前仓库已经补了压测脚本模板和安全测试脚本模板。
- 当前仓库未发现真实压测结果。

## 2. 当前仓库可确认的环境事实

| 项目 | 结论 | 证据 |
|---|---|---|
| Java 版本 | 17 | `pom.xml` |
| Spring Boot 版本 | 3.3.3 | `pom.xml` |
| SSE 实现 | `SseEmitter` | `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SseChatController.java` |
| 向量检索 | `VectorStore` | `ruoyi-system/src/main/java/com/ruoyi/system/service/RagService.java` |
| 聊天上下文 | `RedisChatMemory` | `ruoyi-admin/src/main/java/com/ruoyi/web/service/RedisChatMemory.java` |
| 学生自助接口 | 已实现 | `EduApiController.java` |
| 管理员教务接口 | 已实现 | `AdminEduController.java` |

## 3. 被测接口

| 接口 | 方法 | Token | 说明 |
|---|---|---|---|
| `/api/ai/chat/public/stream` | `POST` | 否 | 公共问答 SSE |
| `/api/ai/chat/student/stream` | `POST` | 学生 token | 学生工具问答 SSE |
| `/api/ai/chat/stream` | `POST` | 否 | 兼容公共问答入口 |
| `/system/edu/api/score` | `GET` | 学生 token | 学生查自己成绩 |
| `/system/edu/api/card/balance` | `GET` | 学生 token | 学生查自己余额 |
| `/system/admin/edu/score` | `GET` | admin token | 管理员按 `studentId` 查成绩 |
| `/system/admin/edu/card/balance` | `GET` | admin token | 管理员按 `studentId` 查余额 |
| `/system/knowledge/import-file` | `POST` | 管理权限 token | 文档上传与入库 |

## 4. 当前推荐测试方案

### 4.1 公共 SSE 长文本问答

- 目标：观察 SSE 首包时间、总耗时、异常断连行为
- 接口：`/api/ai/chat/public/stream`

### 4.2 学生工具问答

- 目标：观察带 `CurrentStudentService`、工具调用、会话上下文时的耗时
- 接口：`/api/ai/chat/student/stream`

### 4.3 管理员教务接口

- 目标：验证管理员接口性能和权限边界
- 接口：
  - `/system/admin/edu/score`
  - `/system/admin/edu/card/balance`

### 4.4 学生自助接口

- 目标：验证学生查自己链路性能和越权拦截
- 接口：
  - `/system/edu/api/score`
  - `/system/edu/api/card/balance`

### 4.5 SSE 生命周期与主动取消

- 当前代码已补前端 `AbortController` + 后端统一 `StreamLifecycle` 管理。
- `public / legacy chat` 断连、超时、异常时会尝试 `dispose` 上游 `Flux` 订阅。
- `student chat` 因为是“单次工具分发 + SSE 单条回推”，断连时主要是 `cancel Future` 并阻止后续回推。
- 这是资源释放优化，不代表当前仓库已经有“节省多少模型资源”的量化压测数据。

## 5. 示例命令

### 5.1 公共聊天

```bash
curl -N \
  -H "Content-Type: application/json" \
  -X POST "http://127.0.0.1:8080/api/ai/chat/public/stream?conversationId=bench-public-1" \
  -d '{
    "prompt": "介绍一下奖学金申请流程",
    "query": "介绍一下奖学金申请流程",
    "conversationId": "bench-public-1"
  }'
```

### 5.2 学生自助成绩

```bash
curl -G \
  -H "Authorization: Bearer <TOKEN_STUDENT_A>" \
  --data-urlencode "subject=高等数学" \
  "http://127.0.0.1:8080/system/edu/api/score"
```

### 5.3 管理员成绩查询

```bash
curl -G \
  -H "Authorization: Bearer <TOKEN_ADMIN>" \
  --data-urlencode "studentId=202301010001" \
  "http://127.0.0.1:8080/system/admin/edu/score"
```

### 5.4 管理员余额查询

```bash
curl -G \
  -H "Authorization: Bearer <TOKEN_ADMIN>" \
  --data-urlencode "studentId=202301010001" \
  "http://127.0.0.1:8080/system/admin/edu/card/balance"
```

## 6. JVM 与 GC 口径

当前仓库未发现以下事实的真实证据：

1. `2G` 堆内存
2. `200+` SSE 长连接
3. 无 `Full GC`
4. 无 `OOM`

因此面试时建议这样说：

“代码层面已经做了 SSE、Redis 外置会话和权限边界治理，但具体并发上限和 GC 指标需要结合真实压测结果来讲，当前仓库只有脚本模板，没有最终报告。”

## 7. 指标风险说明

| 指标 | 当前仓库证据 | 面试建议 |
|---|---|---|
| 首字响应 `10s+ -> 500ms` | 未发现压测数据 | 只能讲 SSE 增量返回机制，不讲具体数值 |
| `200+` SSE 长连接 | 未发现压测数据 | 不要硬讲 |
| `2G` 堆内存 | 未发现启动参数证据 | 不要硬讲 |
| 无 `Full GC/OOM` | 未发现 GC 日志 | 不要硬讲 |
| 准确率 `35% -> 88%` | 未发现评测数据 | 只能讲 RAG 目标是降低幻觉 |

## 8. 当前可复现材料

1. 安全测试文档：`docs/security-test.md`
2. 安全测试脚本：`benchmark/security/`
3. 聊天与权限架构图：`docs/architecture.md`

## 9. 当前结论

1. 当前项目已经具备压测入口和安全测试脚本模板。
2. 当前仓库没有真实压测报告，不能把性能指标讲死。
3. 管理员全量查询接口已落地，但当前只补了接口和测试脚本模板，未补真实压测结果。
4. SSE 生命周期释放逻辑已补，并已经完成单连接真实断连验收；但当前仍没有高并发、多轮次量化压测结果。

## 10. SSE 断连运行时证据（2026-05-23）

本轮新增了运行时断连验收脚本：`benchmark/security/sse-abort-test.js`。

建议用法：

- 通过环境变量注入 `TEST_URL`
- 如需鉴权，再注入 `TEST_TOKEN`
- 用 `ABORT_MS` 控制多长时间后主动 `abort`
- 不在脚本里写死 token

本轮已确认的真实结果：

1. `POST /api/ai/chat/public/stream` 中途断连后，后端日志出现 `SSE lifecycle closed`，且 `disposableDisposed=true`
2. `POST /api/ai/chat/stream` 中途断连后，后端日志出现 `SSE lifecycle closed`，且 `disposableDisposed=true`
3. `POST /api/ai/chat/student/stream` 中途断连后，后端日志出现 `SSE lifecycle closed`，且 `futureCancelled=true`
4. 前端 `chat.vue` 的 `AbortController` 已做真实页面验收：点击“停止生成”后旧响应停止拼接，重复发送时旧请求被中止，新请求正常返回
5. 同轮回归确认 student/admin 权限链路未受影响

口径限制：

- 这些证据能证明“断连后资源释放逻辑真实生效”
- 这些证据还不能证明“在高并发场景下节省了多少模型资源”
- 因此当前可以讲“已实现并经过运行时验证”，但不能讲具体性能收益数值
## 11. SSE 断连运行时补充证据（2026-05-23 再验证）

本轮再次基于真实运行中实例补了 4 组验收证据：

1. `POST /api/ai/chat/public/stream`
   - 会话：`rt5-abort-public-001`
   - 客户端：`benchmark/security/sse-abort-test.js`
   - 日志摘要：`reason=client disconnected, disposableDisposed=true, futureCancelled=false`
2. `POST /api/ai/chat/stream`
   - 会话：`rt5-abort-legacy-001`
   - 客户端：`benchmark/security/sse-abort-test.js`
   - 日志摘要：`reason=client disconnected, disposableDisposed=true, futureCancelled=false`
3. `POST /api/ai/chat/student/stream`
   - 会话：`rt5-abort-student-001`
   - 客户端：`benchmark/security/sse-abort-test.js`
   - 日志摘要：`reason=error callback: IOException, disposableDisposed=false, futureCancelled=true`
4. 前端 `/ai/chat`
   - 真实页面点击“停止生成”后，assistant 气泡停在 `已停止生成`
   - 旧响应长度稳定不再增长
   - 随后连续发送两条新消息都能正常返回

同轮回归结论：

- `public chat` 正常返回
- `legacy chat` 正常返回
- `studentA` 查询“我的高数成绩”仍返回 `95`
- `admin` 查询 `studentA` 成绩仍成功
- `studentA` 访问 admin 接口仍返回 `403` 拒绝语义
## 12. RAG 删除链路说明（2026-05-23）

知识库删除现在不再只是数据库层面的删除，而是补成了最小闭环：

1. 根据 `knowledge_doc.fileUrl` 定位文档
2. 先删 Redis Vector Store 中该文档对应的 chunk
3. 再删 OSS 源文件
4. 最后删数据库记录

这部分不是 benchmark 扩展，不新增性能结论，只补一致性治理说明：

- 目标是避免 OSS 残留
- 目标是避免已删除文档仍被 RAG 检索到
- 目标是减少 DB / OSS / 向量库状态不一致
