# Campus AI Assistant Admin

> 基于 **RuoYi + Spring AI + 通义千问 + Redis Vector Store + OSS + SSE** 打造的校园智能问答与知识库管理平台

![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M6-orange)
![Vue](https://img.shields.io/badge/Vue-2.6.12-42b883)
![Redis](https://img.shields.io/badge/Redis-VectorStore-red)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

---

## 项目简介

这是一个基于若依后台框架二次开发的 **校园 AI 问答平台**。  
项目目标不是做一个单纯的聊天 Demo，而是把：

- 后台权限与用户管理
- 标准问答库运营
- OSS 文档上传
- 文档解析与向量化入库
- RAG 检索增强
- Function Calling 工具调用
- SSE 流式对话
- 会话记忆与限流保护

整合成一个可维护、可扩展、可继续落地的校园智能助手后台系统。

一句话概括：

> 这是一个把 **后台管理、知识库运营、流式对话、向量检索、工具调用、缓存治理、权限控制** 融合在一起的 AI 应用型项目。

---

## 当前版本的核心变化

相较于早期版本，这个项目已经完成以下底层升级：

- 将底层 AI 引擎从 **Dify 直连模式** 迁移到 **Spring AI**
- 使用 **阿里云通义千问** 的 OpenAI Compatible Endpoint
- 使用 **Redis Vector Store** 存储知识片段向量
- 增加 **OSS -> 文档解析 -> 切片 -> 向量化入库** 的闭环
- 在流式对话中加入：
  - **RAG 检索增强**
  - **Function Calling**
  - **会话级 ChatMemory**
  - **Redis + Lua 限流**

---

## 为什么这个项目有价值

校园问答有几个典型特点：

- 高频问题多：奖学金、成绩、选课、请假、毕业流程、校园卡余额
- 文档内容多：规章制度、办事说明、通知公告、政策文件
- 答案需要稳定：不能完全依赖大模型自由发挥
- 成本需要控制：不能所有问题都无脑打大模型
- 场景需要追溯：需要知道回答依据、文件来源和上下文

所以本项目的设计思路不是“把所有问题都扔给模型”，而是做了一层业务编排：

1. 标准、高频、确定性问题优先走问答库
2. 制度和长文档类问题走向量检索增强
3. 成绩、余额等实时数据走 Function Calling 工具查询
4. 模型负责理解问题、融合知识、生成最终回答

这个思路更贴近真实业务系统，也更适合作为后端 / 全栈 / AI 应用开发项目展示。

---

## 核心亮点

### 1. 基于若依做 AI 二开，而不是重新造后台

项目复用了若依成熟的：

- 用户管理
- 角色权限
- 菜单权限
- 登录认证
- 日志审计
- 参数配置
- 系统监控

这样做的好处是：

- 后台基础设施稳定
- 可以快速接入业务能力
- 更接近企业项目真实开发方式

### 2. Spring AI 驱动的通义千问接入

项目不再直接依赖 Dify 对话能力，而是改为：

- Spring AI 统一抽象
- OpenAI Compatible API 对接阿里云通义千问
- 后续可继续平滑替换模型供应商

优点：

- 模型调用方式统一
- 更容易做 RAG、Tools、Memory 组合
- 更方便代码级控制和二次开发

### 3. RAG 知识库闭环

现在已经具备完整的知识入库链路：

1. 前端上传文档
2. 后端上传到阿里云 OSS
3. 从 OSS 读取流
4. 使用 `TikaDocumentReader` 解析文本
5. 使用 `TokenTextSplitter` 切片
6. 写入 Redis Vector Store
7. 对话时执行 `similaritySearch`
8. 将检索结果拼接成 `System Prompt`
9. 再交给模型生成答案

### 4. SSE 流式对话 + 会话记忆

流式聊天接口支持：

- `SseEmitter` 推流
- 前端 `fetch + ReadableStream` 打字机效果
- `conversationId` 多轮对话
- Redis 持久化 `ChatMemory`
- 服务重启后仍可恢复历史上下文

### 5. Function Calling 工具调用

项目已经支持为模型暴露工具函数：

- `getStudentScore`
- `getCardBalance`

当前功能已接入真实数据库查询，不再是纯模拟数据。

### 6. 限流与稳定性治理

针对 AI 对话接口，项目增加了：

- Redis + Lua 滑动窗口限流
- 会话级并发保护
- SSE 超时处理
- 错误兜底响应

---

## 功能地图

### 通用后台能力

- 登录与认证
- 用户、角色、菜单、部门、岗位
- 配置管理
- 日志管理
- Swagger 接口文档
- Redis 监控
- 服务监控

### 自定义 AI 业务能力

- AI 标准问答库管理
- 校园知识文档管理
- OSS 文档上传
- 自动向量化入库
- Redis 向量检索
- 通义千问流式对话
- 会话记忆
- 工具调用（成绩 / 一卡通）
- SSE 聊天页面

---

## 技术栈

### 后端

- Spring Boot 4.0.3
- Spring Security
- JWT
- MyBatis
- PageHelper
- Druid
- Redis
- Redisson
- Spring AI 1.0.0-M6
- 通义千问 OpenAI Compatible API
- Redis Vector Store
- TikaDocumentReader
- TokenTextSplitter
- ChatClient
- ChatMemory
- SseEmitter
- `@Async` + `ThreadPoolTaskExecutor`
- Swagger / springdoc
- 阿里云 OSS SDK

### 前端

- Vue 2
- Vue Router 3
- Vuex
- Element UI
- Axios
- 原生 `fetch + ReadableStream`

### 外部组件

- MySQL
- Redis
- 阿里云 OSS
- 阿里云 DashScope / 通义千问

---

## 项目结构

```text
RuoYi-Vue
├─ ruoyi-admin
│  ├─ Web 入口层
│  ├─ AI 对话接口
│  ├─ SSE 控制器
│  ├─ RAG 上传控制器
│  └─ Function Calling 配置
├─ ruoyi-framework
│  ├─ Security / JWT
│  ├─ Redis 配置
│  ├─ 限流切面
│  ├─ 线程池
│  └─ 全局异常处理
├─ ruoyi-system
│  ├─ 业务实体
│  ├─ Mapper / XML
│  ├─ OSS 服务
│  ├─ RAG 服务
│  └─ 教务统一查询服务
├─ ruoyi-common
│  ├─ 通用常量
│  ├─ AjaxResult
│  ├─ 工具类
│  └─ 注解
├─ ruoyi-ui
│  ├─ AI 聊天页
│  ├─ 知识文档页
│  └─ 后台管理页面
└─ sql
   └─ 初始化 SQL 与业务表
```

---

## 关键业务模块

## 1. AI 热点问答库

字段包括：

- `question`
- `answer`
- `category`
- `keywords`
- `hitCount`
- `isHot`
- `cacheTtl`
- `status`

用途：

- 存储高频标准问答
- 降低模型调用成本
- 提高响应速度
- 增强答案可控性

对应代码：

- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/AiCommonQaController.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/impl/AiCommonQaServiceImpl.java`

## 2. 校园知识文档

字段包括：

- `docId`
- `docName`
- `fileUrl`
- `status`
- `remark`

当前支持：

- 新增 / 修改 / 删除 / 导出
- 上传文件到 OSS
- 自动向量化入库
- 同步状态标记

接口：

- `/system/knowledge/import-file`

对应代码：

- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/KnowledgeDocController.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/RagService.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/Oss/AliOssService.java`

## 3. 学生管理

学生基础数据字段：

- `studentId`
- `password`
- `studentName`
- `majorCode`

这部分既是后台基础业务数据，也为 AI 工具调用与个性化回答提供上下文。

## 4. 成绩与一卡通查询

已从模拟逻辑升级为真实数据库查询。

当前数据库表：

- `student_score`
- `campus_card_account`

统一服务层：

- `IEduQueryService`
- `EduQueryServiceImpl`

这套服务同时给：

- `EduApiController`
- `EduAiFunctionConfig`

复用，保证接口与 Function Calling 数据来源一致。

## 5. AI 对话

当前存在两类 AI 接口：

### `/system/ai/chat`

- 基于 Spring AI 的流式问答接口
- 保留 Redis 缓存和 Lua 限流
- 通过 `QuestionAnswerAdvisor` 做 RAG

### `/api/ai/chat/stream`

- 基于 `SseEmitter`
- 支持：
  - 会话记忆
  - RAG 检索
  - Function Calling
  - `conversationId`
  - SSE 流式输出

前端默认优先调用：

- `/api/ai/chat/stream`

找不到时回退到：

- `/system/ai/chat`

---

## RAG 工作流

### 文档入库流程

1. 前端选择知识文件
2. 后端上传阿里云 OSS
3. 根据 OSS URL 读取文件流
4. `TikaDocumentReader` 解析文本
5. `TokenTextSplitter` 切片
6. 手动追加 overlap，增强跨段语义连续性
7. 写入 Redis Vector Store

### 问答检索流程

1. 用户提问
2. `VectorStore.similaritySearch(query)` 召回 Top-K 相关片段
3. 生成系统提示词 `System Prompt`
4. `ChatClient` 结合 Prompt、Memory、Functions 一起生成答案
5. 通过 SSE 流式返回给前端

---

## Function Calling 工作流

当前注册的工具函数：

- `getStudentScore`
- `getCardBalance`

注册位置：

- `ruoyi-admin/src/main/java/com/ruoyi/web/config/EduAiFunctionConfig.java`

调用方式：

```java
chatClient.prompt(userPrompt)
    .functions("getStudentScore", "getCardBalance")
    .system(systemPrompt)
    .stream()
    .chatResponse();
```

统一数据来源：

- `EduApiController`
- Spring AI Function Bean

都走 `IEduQueryService`，避免一边查库、一边 mock 的问题。

---

## 会话记忆设计

当前实现了基于 Redis 的 `ChatMemory`：

- conversationId 作为会话主键
- 会话历史持久化到 Redis List
- 浏览器端自动生成并保存 `conversationId`
- 断线重连或刷新页面后可继续上下文

前端行为：

- 首次进入页面自动生成 `conversationId`
- 存入 `localStorage`
- 每次请求自动带上
- 支持“新会话”按钮主动切换上下文

---

## 限流机制

项目已从简单固定窗口升级为：

- **Redis ZSET 滑动窗口限流**

核心思路：

1. `ZREMRANGEBYSCORE` 清理窗口外旧请求
2. `ZCARD` 统计当前窗口请求数
3. `ZADD` 写入新请求
4. `PEXPIRE` 设置 key 生命周期

优势：

- 高并发下原子执行
- 精度优于固定窗口
- 更适合 AI 类高成本接口

---

## 关键接口

### AI 对话

- `POST /system/ai/chat`
- `POST /api/ai/chat/stream`

### 知识文档

- `GET /system/knowledge/list`
- `POST /system/knowledge`
- `PUT /system/knowledge`
- `DELETE /system/knowledge/{docIds}`
- `POST /system/knowledge/import-file`

### 教务工具接口

- `GET /system/edu/api/score`
- `GET /system/edu/api/card/balance`

### RAG 导入

- `POST /system/rag/upload-and-import`

---

## 当前工程能力

- Spring Security + JWT 无状态认证
- Redis 登录态管理
- Redis + Lua 限流
- Redis Vector Store
- OSS 文件上传
- 文档自动向量化
- SSE 流式输出
- 多轮会话记忆
- Function Calling
- RAG 检索增强
- 后台文档管理与导入

---

## 本地启动

## 1. 环境要求

- JDK 17
- Maven 3.9+
- Node.js / npm
- MySQL 8.x
- Redis

## 2. 配置文件

主要配置：

- `ruoyi-admin/src/main/resources/application.yml`
- `ruoyi-admin/src/main/resources/application-druid.yml`

重点配置项：

- `spring.ai.openai.*`
- `spring.ai.vectorstore.redis.*`
- `aliyun.oss.*`
- `spring.data.redis.*`

## 3. 后端启动

```bash
mvn clean install
mvn -pl ruoyi-admin -am spring-boot:run
```

## 4. 前端启动

```bash
cd ruoyi-ui
npm install
npm run dev
```

---

## 后续可扩展方向

- 批量上传多个知识文档并并行向量化
- 把 Redis ChatMemory 升级成更完整的历史压缩与摘要记忆
- 增加知识文档来源分类、权限隔离、标签过滤
- 支持多租户知识库索引
- 引入 embedding 模型独立配置
- 增加知识检索命中可视化
- 增加导入进度、失败重试和后台任务中心

---

## 适合写进简历的关键词

`RuoYi` `Spring Boot` `Spring AI` `Tongyi Qianwen` `JWT` `Redis` `Redis Vector Store` `RAG` `Function Calling` `SSE` `OSS` `MyBatis` `Redisson`

---

## 仓库说明

这个仓库首页 README 已经不再是若依默认模板说明，而是当前项目本身的完整介绍。  
如果你是面试官、招聘方或协作者，希望你通过这个 README 能快速看清楚：

- 它是一个真实的 AI 应用型后台项目
- 它具备完整的知识入库与检索增强链路
- 它不是纯聊天 Demo，而是后台、知识库、工具调用、会话记忆、流式输出的整合实践
