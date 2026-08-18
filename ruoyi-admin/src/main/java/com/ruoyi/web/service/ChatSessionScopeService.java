package com.ruoyi.web.service;

import java.util.Locale;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;

@Service
public class ChatSessionScopeService
{
    private static final Pattern CONVERSATION_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    public String scopedConversationId(String conversationId, ChatChannel channel, HttpServletRequest request)
    {
        if (conversationId == null || !CONVERSATION_ID_PATTERN.matcher(conversationId).matches())
        {
            throw new ServiceException("conversationId 格式不合法", HttpStatus.BAD_REQUEST);
        }
        return channel.name().toLowerCase(Locale.ROOT) + ":" + resolveUserScope(request) + ":" + conversationId;
    }

    private String resolveUserScope(HttpServletRequest request)
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
            && authentication.getPrincipal() instanceof LoginUser loginUser && loginUser.getUserId() != null)
        {
            return "user:" + loginUser.getUserId();
        }
        return "anonymous:" + request.getSession(true).getId();
    }

    public enum ChatChannel
    {
        PUBLIC,
        STUDENT
    }
}
