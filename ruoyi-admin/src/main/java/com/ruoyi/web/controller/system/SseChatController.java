package com.ruoyi.web.controller.system;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.web.config.CardBalanceRequest;
import com.ruoyi.web.config.StudentScoreRequest;
import com.ruoyi.system.service.CurrentStudentService;
import com.ruoyi.system.service.ChatIntentRouter;
import com.ruoyi.system.service.ChatIntentRouter.ChatIntent;
import com.ruoyi.system.service.ChatIntentRouter.RouteDecision;
import com.ruoyi.system.service.RagService;
import com.ruoyi.web.service.ChatSessionScopeService;
import com.ruoyi.web.service.ChatSessionScopeService.ChatChannel;
import com.ruoyi.web.service.RedisChatMemory;
import com.ruoyi.web.service.PublicKnowledgeCacheService;
import com.ruoyi.web.service.PublicKnowledgeCacheService.CachedPublicAnswer;
import com.ruoyi.web.service.PythonPublicRagClient;
import com.ruoyi.web.service.SseStreamLifecycle;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.ruoyi.web.service.PythonPublicRagClient.HistoryMessage;

@RestController
@RequestMapping("/api/ai/chat")
public class SseChatController
{
    private static final Logger log = LoggerFactory.getLogger(SseChatController.class);

    @Resource(name = "threadPoolTaskExecutor")
    private Executor taskExecutor;

    @Resource(name = "getStudentScore")
    private BiFunction<StudentScoreRequest, ToolContext, Map<String, Object>> getStudentScoreTool;

    @Resource(name = "getCardBalance")
    private BiFunction<CardBalanceRequest, ToolContext, Map<String, Object>> getCardBalanceTool;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final ChatMemory chatMemory;
    private final MessageChatMemoryAdvisor chatMemoryAdvisor;
    private final RagService ragService;
    private final CurrentStudentService currentStudentService;
    private final ChatSessionScopeService chatSessionScopeService;
    private final ChatIntentRouter chatIntentRouter;
    private final PublicKnowledgeCacheService publicKnowledgeCacheService;
    private final PythonPublicRagClient pythonPublicRagClient;
    private final MeterRegistry meterRegistry;

    public SseChatController(ChatClient.Builder chatClientBuilder, RedisChatMemory redisChatMemory,
        RagService ragService, ObjectMapper objectMapper, CurrentStudentService currentStudentService,
        ChatSessionScopeService chatSessionScopeService, ChatIntentRouter chatIntentRouter,
        PublicKnowledgeCacheService publicKnowledgeCacheService, PythonPublicRagClient pythonPublicRagClient,
        MeterRegistry meterRegistry)
    {
        this.objectMapper = objectMapper;
        this.chatMemory = redisChatMemory;
        this.chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        this.chatClient = chatClientBuilder.build();
        this.ragService = ragService;
        this.currentStudentService = currentStudentService;
        this.chatSessionScopeService = chatSessionScopeService;
        this.chatIntentRouter = chatIntentRouter;
        this.publicKnowledgeCacheService = publicKnowledgeCacheService;
        this.pythonPublicRagClient = pythonPublicRagClient;
        this.meterRegistry = meterRegistry;
    }

    @Anonymous
    @RateLimiter(key = "ai:public:", time = 60, count = 10, limitType = LimitType.USER_ID,
        message = "公共问答请求过于频繁，请稍后再试")
    @RequestMapping(value = { "/stream", "/public/stream" }, method = { RequestMethod.GET, RequestMethod.POST },
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter publicStreamChat(@RequestBody(required = false) Map<String, Object> body,
        @RequestParam(value = "prompt", required = false) String prompt,
        @RequestParam(value = "message", required = false) String message,
        @RequestParam(value = "conversationId", required = false) String conversationIdParam,
        HttpServletRequest request)
    {
        return streamChatInternal(body, prompt, message, conversationIdParam, request, false, Collections.emptyMap());
    }

    @RateLimiter(key = "ai:student:", time = 60, count = 20, limitType = LimitType.USER_ID,
        message = "个人问答请求过于频繁，请稍后再试")
    @RequestMapping(value = "/student/stream", method = { RequestMethod.GET, RequestMethod.POST },
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter studentStreamChat(@RequestBody(required = false) Map<String, Object> body,
        @RequestParam(value = "prompt", required = false) String prompt,
        @RequestParam(value = "message", required = false) String message,
        @RequestParam(value = "conversationId", required = false) String conversationIdParam,
        HttpServletRequest request)
    {
        ensureStudentAccess();
        Map<String, Object> toolContext = currentStudentService.buildToolContext();
        log.debug("Student chat authorized");
        return streamChatInternal(body, prompt, message, conversationIdParam, request, true, toolContext);
    }

    @Anonymous
    @DeleteMapping("/public/conversations/{conversationId}")
    public void clearPublicConversation(@org.springframework.web.bind.annotation.PathVariable String conversationId,
        HttpServletRequest request)
    {
        chatMemory.clear(chatSessionScopeService.scopedConversationId(conversationId, ChatChannel.PUBLIC, request));
    }

    @DeleteMapping("/student/conversations/{conversationId}")
    public void clearStudentConversation(@org.springframework.web.bind.annotation.PathVariable String conversationId,
        HttpServletRequest request)
    {
        ensureStudentAccess();
        chatMemory.clear(chatSessionScopeService.scopedConversationId(conversationId, ChatChannel.STUDENT, request));
    }

    private SseEmitter streamChatInternal(Map<String, Object> body, String prompt, String message,
        String conversationIdParam, HttpServletRequest request, boolean enableStudentTools,
        Map<String, Object> toolContext)
    {
        String userPrompt = resolvePrompt(body, prompt, message);
        long requestStartedNanos = System.nanoTime();
        String clientConversationId = resolveConversationId(body, conversationIdParam, request);
        ChatChannel channel = enableStudentTools ? ChatChannel.STUDENT : ChatChannel.PUBLIC;
        String conversationId = chatSessionScopeService.scopedConversationId(clientConversationId, channel, request);
        SseEmitter emitter = new SseEmitter(60_000L);
        SseStreamLifecycle lifecycle = new SseStreamLifecycle(conversationId, emitter);

        emitter.onTimeout(() -> {
            log.warn("SSE chat timeout, conversationId={}", conversationId);
            lifecycle.close("timeout");
            lifecycle.safeComplete();
        });
        emitter.onCompletion(() -> {
            log.debug("SSE chat completed callback, conversationId={}", conversationId);
            lifecycle.close("completion callback");
        });
        emitter.onError(throwable -> {
            log.error("SSE chat error callback, conversationId={}", conversationId, throwable);
            lifecycle.close("error callback: " + throwable.getClass().getSimpleName());
        });

        RouteDecision routeDecision = chatIntentRouter.route(userPrompt, enableStudentTools);
        if (!enableStudentTools)
        {
            startPublicPythonStream(lifecycle, conversationId, userPrompt, requestStartedNanos);
            return emitter;
        }
        if (enableStudentTools && handleDirectStudentToolCall(lifecycle, conversationId, userPrompt, toolContext,
            routeDecision))
        {
            return emitter;
        }

        List<Document> retrievedDocuments = ragService.retrieveRelevantDocuments(userPrompt);
        List<com.ruoyi.system.service.RagService.RagSource> sources = ragService.toSources(retrievedDocuments);
        log.info("RAG retrieved {} document chunks, conversationId={}, studentTools={}",
            retrievedDocuments.size(), conversationId, enableStudentTools);
        String systemPrompt = appendStudentToolInstructions(ragService.buildSystemPrompt(retrievedDocuments),
            enableStudentTools);

        ChatClient.ChatClientRequestSpec chatRequest = chatClient.prompt(userPrompt)
            .system(systemPrompt)
            .advisors(advisorSpec -> advisorSpec
                .param(MessageChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId)
                .param(MessageChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY, 20)
                .advisors(chatMemoryAdvisor));

        if (enableStudentTools)
        {
            log.info("Student tool chat enabled, conversationId={}", conversationId);
            chatRequest = chatRequest
                .toolContext(toolContext)
                .functions("getStudentScore", "getCardBalance");
            ChatClient.ChatClientRequestSpec finalChatRequest = chatRequest;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                long llmCallStartedNanos = System.nanoTime();
                String outcome = "complete";
                try
                {
                    ChatResponse chatResponse = finalChatRequest.call().chatResponse();
                    if (lifecycle.isClosed())
                    {
                        log.info("AI tool call response ignored because lifecycle already closed, conversationId={}",
                            conversationId);
                        return;
                    }
                    if (lifecycle.safeSend(toSse(extractContent(chatResponse))))
                    {
                        lifecycle.safeSend(toSse("sources", Map.of("sources", sources)));
                        lifecycle.safeComplete();
                    }
                }
                catch (Throwable throwable)
                {
                    outcome = "error";
                    if (lifecycle.isClosed())
                    {
                        log.info("AI tool call finished after lifecycle closed, conversationId={}", conversationId);
                        return;
                    }
                    log.error("AI tool call failed, conversationId={}", conversationId, throwable);
                    lifecycle.safeCompleteWithError(throwable);
                }
                finally
                {
                    Timer.builder("campus.ai.llm.call")
                        .tag("outcome", outcome)
                        .description("Non-streaming LLM tool-capable call duration")
                        .register(meterRegistry)
                        .record(System.nanoTime() - llmCallStartedNanos, TimeUnit.NANOSECONDS);
                }
            }, taskExecutor);
            lifecycle.setFuture(future);
            return emitter;
        }
        return emitter;
    }

    private void startPublicPythonStream(SseStreamLifecycle lifecycle, String conversationId, String userPrompt,
        long requestStartedNanos)
    {
        List<org.springframework.ai.chat.messages.Message> history = chatMemory.get(conversationId, 20);
        boolean cacheEligible = history.isEmpty();
        if (cacheEligible)
        {
            CachedPublicAnswer cached = publicKnowledgeCacheService.get(userPrompt);
            if (cached != null)
            {
                chatMemory.add(conversationId,
                    List.of(new org.springframework.ai.chat.messages.UserMessage(userPrompt),
                        new AssistantMessage(cached.answer())));
                if (lifecycle.safeSend(toSse("answer", Map.of("answer", cached.answer())))
                    && lifecycle.safeSend(toSse("sources", Map.of("sources", cached.sources()))))
                {
                    recordPublicChatComplete(requestStartedNanos);
                    lifecycle.safeComplete();
                }
                return;
            }
        }

        StringBuilder completeAnswer = new StringBuilder();
        AtomicReference<List<com.ruoyi.system.service.RagService.RagSource>> sources =
            new AtomicReference<>(List.of());
        AtomicBoolean sourcesReceived = new AtomicBoolean(false);

        // Retrieval, first-token, and LLM-stream metrics are owned by Python; align names in migration step 4.
        CompletableFuture<Void> future = pythonPublicRagClient.stream(userPrompt, toPythonHistory(history))
            .doOnNext(event -> {
                if (lifecycle.isClosed())
                {
                    return;
                }
                String eventType = event.event();
                if ("answer".equals(eventType))
                {
                    String delta = event.answer() == null ? "" : event.answer();
                    completeAnswer.append(delta);
                    lifecycle.safeSend(toSse("answer", Map.of("answer", delta)));
                    return;
                }
                if ("sources".equals(eventType))
                {
                    List<com.ruoyi.system.service.RagService.RagSource> eventSources = event.sources() == null
                        ? List.of()
                        : event.sources().stream().filter(Objects::nonNull).toList();
                    sources.set(eventSources);
                    sourcesReceived.set(true);
                    lifecycle.safeSend(toSse("sources", Map.of("sources", eventSources)));
                    return;
                }
                if ("error".equals(eventType))
                {
                    throw new IllegalStateException("Python RAG stream returned an error event");
                }
                throw new IllegalStateException("Unknown Python RAG SSE event: " + eventType);
            })
            .then()
            .toFuture();
        lifecycle.setFuture(future);
        future.whenComplete((ignored, throwable) -> {
            if (throwable != null)
            {
                handlePublicPythonFailure(lifecycle, conversationId, throwable);
                return;
            }
            if (!sourcesReceived.get())
            {
                handlePublicPythonFailure(lifecycle, conversationId,
                    new IllegalStateException("Python RAG stream completed without sources"));
                return;
            }
            if (lifecycle.isClosed())
            {
                return;
            }
            try
            {
                String answer = completeAnswer.toString();
                chatMemory.add(conversationId,
                    List.of(new org.springframework.ai.chat.messages.UserMessage(userPrompt),
                        new AssistantMessage(answer)));
                if (cacheEligible)
                {
                    publicKnowledgeCacheService.put(userPrompt, answer, sources.get());
                }
                recordPublicChatComplete(requestStartedNanos);
                lifecycle.safeComplete();
            }
            catch (Throwable ex)
            {
                log.error("Failed to persist public chat response, conversationId={}", conversationId, ex);
                lifecycle.safeCompleteWithError(ex);
            }
        });
    }

    private List<HistoryMessage> toPythonHistory(List<org.springframework.ai.chat.messages.Message> history)
    {
        if (history == null || history.isEmpty())
        {
            return List.of();
        }
        List<HistoryMessage> result = new ArrayList<>();
        for (org.springframework.ai.chat.messages.Message message : history)
        {
            if (message instanceof org.springframework.ai.chat.messages.UserMessage userMessage)
            {
                result.add(new HistoryMessage("user", userMessage.getText()));
            }
            else if (message instanceof AssistantMessage assistantMessage)
            {
                result.add(new HistoryMessage("assistant", assistantMessage.getText()));
            }
            else if (message instanceof org.springframework.ai.chat.messages.SystemMessage systemMessage)
            {
                result.add(new HistoryMessage("system", systemMessage.getText()));
            }
        }
        return result;
    }

    private void handlePublicPythonFailure(SseStreamLifecycle lifecycle, String conversationId, Throwable throwable)
    {
        if (lifecycle.isClosed())
        {
            log.info("Python public RAG stream ended after lifecycle closed, conversationId={}", conversationId);
            return;
        }
        Throwable cause = unwrapCompletionException(throwable);
        log.error("Python public RAG stream failed, conversationId={}", conversationId, cause);
        lifecycle.safeSend(toSse("error", Map.of("message", "Python 服务不可用，请稍后重试")));
        lifecycle.safeCompleteWithError(cause);
    }

    private Throwable unwrapCompletionException(Throwable throwable)
    {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null)
        {
            current = current.getCause();
        }
        return current;
    }

    private void recordPublicChatComplete(long requestStartedNanos)
    {
        Timer.builder("campus.ai.chat.complete")
            .description("Complete public RAG response latency")
            .register(meterRegistry)
            .record(System.nanoTime() - requestStartedNanos, TimeUnit.NANOSECONDS);
    }

    private String resolvePrompt(Map<String, Object> body, String prompt, String message)
    {
        if (StringUtils.isNotEmpty(prompt))
        {
            return prompt;
        }
        if (StringUtils.isNotEmpty(message))
        {
            return message;
        }
        if (body != null)
        {
            Object bodyPrompt = body.get("prompt");
            if (bodyPrompt != null && StringUtils.isNotEmpty(bodyPrompt.toString()))
            {
                return bodyPrompt.toString();
            }

            Object bodyMessage = body.get("message");
            if (bodyMessage != null && StringUtils.isNotEmpty(bodyMessage.toString()))
            {
                return bodyMessage.toString();
            }

            Object bodyQuery = body.get("query");
            if (bodyQuery != null && StringUtils.isNotEmpty(bodyQuery.toString()))
            {
                return bodyQuery.toString();
            }
        }
        return "";
    }

    private String resolveConversationId(Map<String, Object> body, String conversationIdParam, HttpServletRequest request)
    {
        if (StringUtils.isNotEmpty(conversationIdParam))
        {
            return conversationIdParam;
        }
        if (body != null)
        {
            Object conversationId = body.get("conversationId");
            if (conversationId != null && StringUtils.isNotEmpty(conversationId.toString()))
            {
                return conversationId.toString();
            }
        }

        String sessionId = request.getSession(true).getId();
        return StringUtils.isNotEmpty(sessionId) ? sessionId : UUID.randomUUID().toString();
    }

    private String extractContent(ChatResponse chatResponse)
    {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null)
        {
            return "";
        }
        String text = chatResponse.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private String appendStudentToolInstructions(String systemPrompt, boolean enableStudentTools)
    {
        if (!enableStudentTools)
        {
            return systemPrompt;
        }
        return systemPrompt + System.lineSeparator() + System.lineSeparator() + """
            你还可以调用学生个人业务工具。
            1. 当用户询问成绩、分数、课程成绩、高数、高等数学、英语或 Java 成绩时，必须调用 getStudentScore 工具，不能直接按知识库兜底回答。
            2. 当用户询问一卡通余额、校园卡余额或饭卡余额时，必须调用 getCardBalance 工具。
            3. 工具只能查询当前登录学生本人的数据，不能根据用户自然语言里的学号、studentId、姓名查询其他学生。
            4. 如果用户要求查询其他同学的数据，仍然只按当前登录学生身份执行，并明确说明只能查询本人数据。
            5. 不要因为知识库没有相关片段就放弃调用工具；成绩和一卡通问题优先使用工具。
            """;
    }

    private boolean handleDirectStudentToolCall(SseStreamLifecycle lifecycle, String conversationId, String userPrompt,
        Map<String, Object> toolContext, RouteDecision routeDecision)
    {
        if (routeDecision.intent() != ChatIntent.STUDENT_SCORE
            && routeDecision.intent() != ChatIntent.CARD_BALANCE)
        {
            return false;
        }
        String toolName = routeDecision.intent() == ChatIntent.STUDENT_SCORE ? "getStudentScore" : "getCardBalance";
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            long toolStartedNanos = System.nanoTime();
            try
            {
                Map<String, Object> result;
                if ("getStudentScore".equals(toolName))
                {
                    String subject = routeDecision.subject();
                    if (StringUtils.isEmpty(subject))
                    {
                        String clarification = "请补充要查询的课程，例如高等数学、大学英语或 Java程序设计。";
                        chatMemory.add(conversationId,
                            List.of(new org.springframework.ai.chat.messages.UserMessage(userPrompt),
                                new org.springframework.ai.chat.messages.AssistantMessage(clarification)));
                        lifecycle.safeSend(toSse(clarification));
                        lifecycle.safeComplete();
                        return;
                    }
                    result = getStudentScoreTool.apply(new StudentScoreRequest(subject), new ToolContext(toolContext));
                }
                else
                {
                    result = getCardBalanceTool.apply(new CardBalanceRequest(), new ToolContext(toolContext));
                }
                log.info("Student chat direct tool dispatch completed, conversationId={}, tool={}, status={}",
                    conversationId, toolName, result == null ? null : result.get("status"));
                if (lifecycle.isClosed())
                {
                    log.info("Student chat direct tool result dropped because lifecycle already closed, conversationId={}, tool={}",
                        conversationId, toolName);
                    return;
                }
                String answer = renderStudentToolAnswer(toolName, result);
                chatMemory.add(conversationId,
                    List.of(new org.springframework.ai.chat.messages.UserMessage(userPrompt),
                        new org.springframework.ai.chat.messages.AssistantMessage(answer)));
                Timer.builder("campus.ai.tool.query")
                    .tag("tool", toolName)
                    .description("Student business tool latency")
                    .register(meterRegistry)
                    .record(System.nanoTime() - toolStartedNanos, TimeUnit.NANOSECONDS);
                if (lifecycle.safeSend(toSse(answer)))
                {
                    lifecycle.safeComplete();
                }
            }
            catch (Throwable throwable)
            {
                if (lifecycle.isClosed())
                {
                    log.info("Student chat direct tool task finished after lifecycle closed, conversationId={}, tool={}",
                        conversationId, toolName);
                    return;
                }
                log.error("Student chat direct tool dispatch failed, conversationId={}", conversationId, throwable);
                lifecycle.safeCompleteWithError(throwable);
            }
        }, taskExecutor);
        lifecycle.setFuture(future);
        return true;
    }

    private String renderStudentToolAnswer(String toolName, Map<String, Object> result)
    {
        if (result == null || result.isEmpty())
        {
            return "个人数据查询失败，请稍后重试";
        }
        String status = String.valueOf(result.getOrDefault("status", ""));
        String message = String.valueOf(result.getOrDefault("message", "个人数据查询失败"));
        Object dataObject = result.get("data");
        if (!"SUCCESS".equals(status) || !(dataObject instanceof Map<?, ?> data))
        {
            return message;
        }
        if ("getStudentScore".equals(toolName))
        {
            Object subject = data.get("subject");
            Object score = data.get("score");
            return String.format("当前登录学生的%s成绩为%s分。", subject, score);
        }
        if ("getCardBalance".equals(toolName))
        {
            Object balance = data.get("balance");
            return String.format("当前登录学生的一卡通余额为%s元。", balance);
        }
        return message;
    }

    private void ensureStudentAccess()
    {
        try
        {
            if (!SecurityUtils.hasRole("student"))
            {
                throw new ServiceException("当前账号没有学生角色权限", HttpStatus.FORBIDDEN);
            }
        }
        catch (ServiceException ex)
        {
            if (ex.getCode() != null && ex.getCode() == HttpStatus.UNAUTHORIZED)
            {
                throw new ServiceException("当前未登录，无法访问学生聊天", HttpStatus.UNAUTHORIZED);
            }
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("当前未登录，无法访问学生聊天", HttpStatus.UNAUTHORIZED);
        }
    }

    private String toSse(String answer)
    {
        return toSse("answer", Map.of("answer", answer));
    }

    private String toSse(String event, Map<String, ?> data)
    {
        try
        {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", event);
            payload.putAll(data);
            return objectMapper.writeValueAsString(payload);
        }
        catch (JsonProcessingException e)
        {
            throw new RuntimeException("SSE 消息序列化失败", e);
        }
    }

}
