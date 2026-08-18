package com.ruoyi.system.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.StringUtils;

/**
 * Deterministic routing for the small set of supported campus intents.
 * This is a rule router, not a machine-learning classifier.
 */
@Service
public class ChatIntentRouter
{
    private static final Map<String, String> SUBJECT_ALIASES = new LinkedHashMap<>();

    static
    {
        SUBJECT_ALIASES.put("java程序设计", "Java程序设计");
        SUBJECT_ALIASES.put("java", "Java程序设计");
        SUBJECT_ALIASES.put("高等数学", "高等数学");
        SUBJECT_ALIASES.put("高数", "高等数学");
        SUBJECT_ALIASES.put("大学英语", "大学英语");
        SUBJECT_ALIASES.put("英语", "大学英语");
    }

    public RouteDecision route(String question, boolean studentChannel)
    {
        if (StringUtils.isBlank(question))
        {
            return new RouteDecision(ChatIntent.UNKNOWN, null);
        }
        String normalized = question.trim().toLowerCase(Locale.ROOT);
        if (!studentChannel)
        {
            return new RouteDecision(ChatIntent.PUBLIC_KNOWLEDGE, null);
        }
        if (containsAny(normalized, "一卡通", "校园卡", "饭卡")
            && containsAny(normalized, "余额", "还有多少", "剩余"))
        {
            return new RouteDecision(ChatIntent.CARD_BALANCE, null);
        }
        if (containsAny(normalized, "成绩", "分数", "考了多少", "多少分"))
        {
            return new RouteDecision(ChatIntent.STUDENT_SCORE, resolveSubject(normalized));
        }
        return new RouteDecision(ChatIntent.PUBLIC_KNOWLEDGE, null);
    }

    private String resolveSubject(String normalizedQuestion)
    {
        for (Map.Entry<String, String> entry : SUBJECT_ALIASES.entrySet())
        {
            if (normalizedQuestion.contains(entry.getKey()))
            {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean containsAny(String value, String... candidates)
    {
        for (String candidate : candidates)
        {
            if (value.contains(candidate))
            {
                return true;
            }
        }
        return false;
    }

    public enum ChatIntent
    {
        PUBLIC_KNOWLEDGE,
        STUDENT_SCORE,
        CARD_BALANCE,
        UNKNOWN
    }

    public record RouteDecision(ChatIntent intent, String subject)
    {
    }
}
