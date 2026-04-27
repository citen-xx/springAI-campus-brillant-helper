package com.ruoyi.web.controller.system;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.common.utils.StringUtils;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai/chat")
public class SseChatController
{
    private static final Logger log = LoggerFactory.getLogger(SseChatController.class);

    @Resource(name = "threadPoolTaskExecutor")
    private Executor taskExecutor;

    @RateLimiter(time = 60, count = 3, limitType = LimitType.USER_ID, message = "大模型额度已耗尽，请稍后再试")
    @RequestMapping(value = "/stream", method = { RequestMethod.GET, RequestMethod.POST }, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody(required = false) Map<String, Object> body,
        @RequestParam(value = "prompt", required = false) String prompt,
        @RequestParam(value = "message", required = false) String message)
    {
        String userPrompt = resolvePrompt(body, prompt, message);
        SseEmitter emitter = new SseEmitter(60_000L);

        emitter.onTimeout(() -> {
            log.warn("SSE chat timeout, prompt={}", userPrompt);
            emitter.complete();
        });
        emitter.onCompletion(() -> log.debug("SSE chat completed, prompt={}", userPrompt));

        CompletableFuture.runAsync(() -> pushMockResponse(emitter, userPrompt), taskExecutor);
        return emitter;
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
        return "Please introduce how SSE streaming chat works.";
    }

    private void pushMockResponse(SseEmitter emitter, String prompt)
    {
        String mockResponse = String.join(" ",
            "Thinking",
            "about",
            "your",
            "question:",
            prompt,
            "Now",
            "I",
            "am",
            "simulating",
            "a",
            "large",
            "model",
            "streaming",
            "answer",
            "with",
            "SSE,",
            "sending",
            "one",
            "word",
            "every",
            "100ms,",
            "and",
            "the",
            "frontend",
            "will",
            "append",
            "them",
            "into",
            "a",
            "typing",
            "effect.");

        try
        {
            for (String word : mockResponse.split("\\s+"))
            {
                emitter.send(SseEmitter.event().name("message").data(word + " "));
                Thread.sleep(100L);
            }
            emitter.complete();
        }
        catch (IOException e)
        {
            log.error("SSE chat send failed, prompt={}", prompt, e);
            emitter.completeWithError(e);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            log.error("SSE chat interrupted, prompt={}", prompt, e);
            emitter.completeWithError(e);
        }
        catch (Exception e)
        {
            log.error("SSE chat failed, prompt={}", prompt, e);
            emitter.completeWithError(e);
        }
    }
}
