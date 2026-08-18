package com.ruoyi.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.web.service.ChatSessionScopeService.ChatChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ChatSessionScopeServiceTest
{
    private final ChatSessionScopeService service = new ChatSessionScopeService();

    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sameClientConversationIdIsIsolatedBetweenUsers()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        authenticate(101L);
        String userA = service.scopedConversationId("123", ChatChannel.PUBLIC, request);
        authenticate(202L);
        String userB = service.scopedConversationId("123", ChatChannel.PUBLIC, request);

        assertEquals("public:user:101:123", userA);
        assertEquals("public:user:202:123", userB);
        assertNotEquals(userA, userB);
    }

    @Test
    void channelAndConversationAreBothPartOfScope()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        authenticate(101L);

        String publicChat = service.scopedConversationId("123", ChatChannel.PUBLIC, request);
        String studentChat = service.scopedConversationId("123", ChatChannel.STUDENT, request);
        String anotherConversation = service.scopedConversationId("456", ChatChannel.PUBLIC, request);

        assertNotEquals(publicChat, studentChat);
        assertNotEquals(publicChat, anotherConversation);
    }

    @Test
    void anonymousScopeUsesServerSessionInsteadOfConversationIdAsIdentity()
    {
        MockHttpServletRequest browserA = new MockHttpServletRequest();
        MockHttpServletRequest browserB = new MockHttpServletRequest();

        String scopedA = service.scopedConversationId("123", ChatChannel.PUBLIC, browserA);
        String scopedB = service.scopedConversationId("123", ChatChannel.PUBLIC, browserB);

        assertNotEquals(scopedA, scopedB);
    }

    @Test
    void rejectsConversationIdsThatCouldAlterRedisKeyShape()
    {
        assertThrows(ServiceException.class, () -> service.scopedConversationId(
            "../../other-user", ChatChannel.PUBLIC, new MockHttpServletRequest()));
    }

    private void authenticate(Long userId)
    {
        SysUser user = new SysUser(userId);
        user.setUserName("user-" + userId);
        user.setPassword("not-used");
        LoginUser loginUser = new LoginUser(userId, 1L, user, Set.of());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
    }
}
