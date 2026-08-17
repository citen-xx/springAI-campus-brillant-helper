package com.ruoyi.web.controller.system;

import java.util.ArrayList;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.CurrentStudentService;
import com.ruoyi.system.service.ChatIntentRouter;
import com.ruoyi.system.service.ChatIntentRouter.ChatIntent;
import com.ruoyi.system.service.ChatIntentRouter.RouteDecision;
import com.ruoyi.system.service.StudentBusinessToolService;
import com.ruoyi.web.service.ChatSessionScopeService;
import com.ruoyi.web.service.ChatSessionScopeService.ChatChannel;
import com.ruoyi.web.service.RedisChatMemory;
import com.ruoyi.web.service.RedisChatMemory.ChatMessage;
import com.ruoyi.web.service.PublicKnowledgeCacheService;
import com.ruoyi.web.service.PublicKnowledgeCacheService.CachedPublicAnswer;
import com.ruoyi.web.service.PythonPublicRagClient;
import com.ruoyi.web.service.PythonPublicRagClient.PythonRagSource;
import com.ruoyi.web.service.SseStreamLifecycle;
import com.ruoyi.framework.security.service.AiCallbackTokenService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final ObjectMapper objectMapper;
    private final RedisChatMemory chatMemory;
    private final CurrentStudentService currentStudentService;
    private final StudentBusinessToolService studentBusinessToolService;
    private final ChatSessionScopeService chatSessionScopeService;
    private final ChatIntentRouter chatIntentRouter;
    private final PublicKnowledgeCacheService publicKnowledgeCacheService;
    private final PythonPublicRagClient pythonPublicRagClient;
    private final MeterRegistry meterRegistry;
    private final AiCallbackTokenService callbackTokenService;

    @org.springframework.beans.factory.annotation.Autowired
    public SseChatController(RedisChatMemory redisChatMemory, ObjectMapper objectMapper,
        CurrentStudentService currentStudentService, StudentBusinessToolService studentBusinessToolService,
        ChatSessionScopeService chatSessionScopeService, ChatIntentRouter chatIntentRouter,
        PublicKnowledgeCacheService publicKnowledgeCacheService, PythonPublicRagClient pythonPublicRagClient,
        MeterRegistry meterRegistry, AiCallbackTokenService callbackTokenService)
    {
        this.objectMapper = objectMapper;
        this.chatMemory = redisChatMemory;
        this.currentStudentService = currentStudentService;
        this.studentBusinessToolService = studentBusinessToolService;
        this.chatSessionScopeService = chatSessionScopeService;
        this.chatIntentRouter = chatIntentRouter;
        this.publicKnowledgeCacheService = publicKnowledgeCacheService;
        this.pythonPublicRagClient = pythonPublicRagClient;
        this.meterRegistry = meterRegistry;
        this.callbackTokenService = callbackTokenService;
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
        return streamChatInternal(body, prompt, message, conversationIdParam, request, false, null);
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
        String studentId = currentStudentService.requireCurrentStudentId();
        log.debug("Student chat authorized");
        return streamChatInternal(body, prompt, message, conversationIdParam, request, true, studentId);
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
        String studentId)
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
        if (enableStudentTools && handleDirectStudentToolCall(lifecycle, conversationId, userPrompt, studentId,
            routeDecision))
        {
            return emitter;
        }
        if (enableStudentTools)
        {
            startStudentPythonStream(lifecycle, conversationId, userPrompt, studentId);
            return emitter;
        }
        return emitter;
    }

    private void startStudentPythonStream(SseStreamLifecycle lifecycle, String conversationId, String userPrompt,
        String studentId)
    {
        if (callbackTokenService == null)
        {
            lifecycle.safeCompleteWithError(new IllegalStateException("AI callback token service is unavailable"));
            return;
        }
        String callbackToken = callbackTokenService.issue(SecurityUtils.getUserId(), studentId, conversationId);
        long llmCallStartedNanos = System.nanoTime();
        List<ChatMessage> history = chatMemory.get(conversationId, 20);
        StringBuilder completeAnswer = new StringBuilder();
        AtomicReference<List<PythonRagSource>> sources =
            new AtomicReference<>(List.of());
        AtomicBoolean sourcesReceived = new AtomicBoolean(false);

        CompletableFuture<Void> future = pythonPublicRagClient.streamStudent(userPrompt, toPythonHistory(history),
            callbackToken, conversationId)
            .doOnNext(event -> {
                if (lifecycle.isClosed())
                {
                    return;
                }
                if ("answer".equals(event.event()))
                {
                    String delta = event.answer() == null ? "" : event.answer();
                    completeAnswer.append(delta);
                    lifecycle.safeSend(toSse("answer", Map.of("answer", delta)));
                    return;
                }
                if ("sources".equals(event.event()))
                {
                    List<PythonRagSource> eventSources = event.sources() == null
                        ? List.of()
                        : event.sources().stream().filter(Objects::nonNull).toList();
                    sources.set(eventSources);
                    sourcesReceived.set(true);
                    lifecycle.safeSend(toSse("sources", Map.of("sources", eventSources)));
                    return;
                }
                if ("error".equals(event.event()))
                {
                    throw new IllegalStateException("Python student stream returned an error event");
                }
                throw new IllegalStateException("Unknown Python student SSE event: " + event.event());
            })
            .then()
            .toFuture();
        lifecycle.setFuture(future);
        future.whenComplete((ignored, throwable) -> {
            Timer.builder("campus.ai.llm.call")
                .tag("outcome", throwable == null ? "complete" : "error")
                .description("Student Python tool-capable model call duration")
                .register(meterRegistry)
                .record(System.nanoTime() - llmCallStartedNanos, TimeUnit.NANOSECONDS);
            if (throwable != null)
            {
                handleStudentPythonFailure(lifecycle, conversationId, throwable);
                return;
            }
            if (!sourcesReceived.get())
            {
                handleStudentPythonFailure(lifecycle, conversationId,
                    new IllegalStateException("Python student stream completed without sources"));
                return;
            }
            if (lifecycle.isClosed())
            {
                return;
            }
            String answer = completeAnswer.toString();
            chatMemory.add(conversationId,
                List.of(ChatMessage.user(userPrompt), ChatMessage.assistant(answer)));
            lifecycle.safeComplete();
        });
    }

    private void handleStudentPythonFailure(SseStreamLifecycle lifecycle, String conversationId, Throwable throwable)
    {
        if (lifecycle.isClosed())
        {
            return;
        }
        log.error("Python student stream failed, conversationId={}", conversationId,
            unwrapCompletionException(throwable));
        lifecycle.safeSend(toSse("error", Map.of("message", "学生问答服务暂时不可用，请稍后重试")));
        lifecycle.safeCompleteWithError(throwable);
    }

    private void startPublicPythonStream(SseStreamLifecycle lifecycle, String conversationId, String userPrompt,
        long requestStartedNanos)
    {
        List<ChatMessage> history = chatMemory.get(conversationId, 20);
        boolean cacheEligible = history.isEmpty();
        if (cacheEligible)
        {
            CachedPublicAnswer cached = publicKnowledgeCacheService.get(userPrompt);
            if (cached != null)
            {
                chatMemory.add(conversationId,
                    List.of(ChatMessage.user(userPrompt), ChatMessage.assistant(cached.answer())));
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
        AtomicReference<List<PythonRagSource>> sources =
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
                    List<PythonRagSource> eventSources = event.sources() == null
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
                    List.of(ChatMessage.user(userPrompt), ChatMessage.assistant(answer)));
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

    private List<HistoryMessage> toPythonHistory(List<ChatMessage> history)
    {
        if (history == null || history.isEmpty())
        {
            return List.of();
        }
        List<HistoryMessage> result = new ArrayList<>();
        for (ChatMessage message : history)
        {
            if (message == null)
            {
                continue;
            }
            switch (message.role())
            {
                case USER -> result.add(new HistoryMessage("user", message.content()));
                case ASSISTANT -> result.add(new HistoryMessage("assistant", message.content()));
                case SYSTEM -> result.add(new HistoryMessage("system", message.content()));
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

    private boolean handleDirectStudentToolCall(SseStreamLifecycle lifecycle, String conversationId, String userPrompt,
        String studentId, RouteDecision routeDecision)
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
                            List.of(ChatMessage.user(userPrompt), ChatMessage.assistant(clarification)));
                        lifecycle.safeSend(toSse(clarification));
                        lifecycle.safeComplete();
                        return;
                    }
                    result = studentBusinessToolService.queryScore(studentId, subject);
                }
                else
                {
                    result = studentBusinessToolService.queryBalance(studentId);
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
                    List.of(ChatMessage.user(userPrompt), ChatMessage.assistant(answer)));
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
