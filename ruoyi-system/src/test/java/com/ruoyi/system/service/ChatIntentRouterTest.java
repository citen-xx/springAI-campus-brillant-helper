package com.ruoyi.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ChatIntentRouterTest
{
    private final ChatIntentRouter router = new ChatIntentRouter();

    @Test
    void javaKnowledgeQuestionIsNotMistakenForScoreQuery()
    {
        assertEquals(ChatIntentRouter.ChatIntent.PUBLIC_KNOWLEDGE,
            router.route("Java 是什么？", true).intent());
    }

    @Test
    void scoreWithoutSubjectRequestsClarificationRoute()
    {
        ChatIntentRouter.RouteDecision decision = router.route("我的成绩是多少？", true);
        assertEquals(ChatIntentRouter.ChatIntent.STUDENT_SCORE, decision.intent());
        assertNull(decision.subject());
    }

    @Test
    void supportedSubjectAndCardBalanceAreNormalized()
    {
        assertEquals("高等数学", router.route("查一下我的高数成绩", true).subject());
        assertEquals(ChatIntentRouter.ChatIntent.CARD_BALANCE,
            router.route("我的校园卡余额还有多少？", true).intent());
    }

    @Test
    void publicChannelNeverRoutesToPersonalTools()
    {
        assertEquals(ChatIntentRouter.ChatIntent.PUBLIC_KNOWLEDGE,
            router.route("查询我的一卡通余额", false).intent());
    }
}
