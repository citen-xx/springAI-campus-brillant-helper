package com.ruoyi.web.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis 的会话记忆实现
 *
 * 使用 Redis List 按 conversationId 持久化消息历史，支持服务重启后上下文恢复。
 */
@Component
public class RedisChatMemory implements ChatMemory
{
    private static final String KEY_PREFIX = "ai:chat:memory:";
    private static final Duration TTL = Duration.ofDays(7);
    private static final int MAX_HISTORY_SIZE = 100;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public RedisChatMemory(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper)
    {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void add(String conversationId, List<Message> messages)
    {
        if (conversationId == null || conversationId.isBlank() || messages == null || messages.isEmpty())
        {
            return;
        }

        String key = buildKey(conversationId);
        List<String> payloads = new ArrayList<>();
        for (Message message : messages)
        {
            if (message == null)
            {
                continue;
            }
            payloads.add(serialize(message));
        }

        if (payloads.isEmpty())
        {
            return;
        }

        stringRedisTemplate.opsForList().rightPushAll(key, payloads);
        stringRedisTemplate.expire(key, TTL);

        Long size = stringRedisTemplate.opsForList().size(key);
        if (size != null && size > MAX_HISTORY_SIZE)
        {
            stringRedisTemplate.opsForList().trim(key, -MAX_HISTORY_SIZE, -1);
        }
    }

    @Override
    public List<Message> get(String conversationId, int lastN)
    {
        if (conversationId == null || conversationId.isBlank())
        {
            return List.of();
        }

        int fetchSize = lastN > 0 ? lastN : MAX_HISTORY_SIZE;
        String key = buildKey(conversationId);
        Long size = stringRedisTemplate.opsForList().size(key);
        if (size == null || size <= 0)
        {
            return List.of();
        }

        long start = Math.max(0, size - fetchSize);
        List<String> payloads = stringRedisTemplate.opsForList().range(key, start, -1);
        if (payloads == null || payloads.isEmpty())
        {
            return List.of();
        }

        List<Message> messages = new ArrayList<>();
        for (String payload : payloads)
        {
            Message message = deserialize(payload);
            if (message != null)
            {
                messages.add(message);
            }
        }
        return messages;
    }

    @Override
    public void clear(String conversationId)
    {
        if (conversationId == null || conversationId.isBlank())
        {
            return;
        }
        stringRedisTemplate.delete(buildKey(conversationId));
    }

    private String buildKey(String conversationId)
    {
        return KEY_PREFIX + conversationId;
    }

    private String serialize(Message message)
    {
        try
        {
            StoredMessage storedMessage = StoredMessage.from(message);
            return objectMapper.writeValueAsString(storedMessage);
        }
        catch (Exception e)
        {
            throw new RuntimeException("序列化聊天消息失败", e);
        }
    }

    private Message deserialize(String payload)
    {
        try
        {
            StoredMessage storedMessage = objectMapper.readValue(payload, StoredMessage.class);
            if (storedMessage == null || storedMessage.type == null)
            {
                return null;
            }

            MessageType type = MessageType.valueOf(storedMessage.type);
            String text = Objects.toString(storedMessage.text, "");
            return switch (type)
            {
                case USER -> new UserMessage(text);
                case ASSISTANT -> new AssistantMessage(text);
                case SYSTEM -> new SystemMessage(text);
                default -> null;
            };
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public static class StoredMessage
    {
        public String type;
        public String text;

        public StoredMessage()
        {
        }

        public static StoredMessage from(Message message)
        {
            StoredMessage storedMessage = new StoredMessage();
            storedMessage.type = message.getMessageType().name();
            if (message instanceof UserMessage userMessage)
            {
                storedMessage.text = userMessage.getText();
            }
            else if (message instanceof AssistantMessage assistantMessage)
            {
                storedMessage.text = assistantMessage.getText();
            }
            else if (message instanceof SystemMessage systemMessage)
            {
                storedMessage.text = systemMessage.getText();
            }
            else
            {
                storedMessage.text = message.toString();
            }
            return storedMessage;
        }

        public String getType()
        {
            return type;
        }

        public void setType(String type)
        {
            this.type = type;
        }

        public String getText()
        {
            return text;
        }

        public void setText(String text)
        {
            this.text = text;
        }
    }
}
