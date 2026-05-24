#!/usr/bin/env bash
# 预期：已登录但未绑定 student.user_id 的账号访问学生聊天时，工具层应返回未绑定学生身份。

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
TOKEN_COMMON="${TOKEN_COMMON:-REPLACE_ME}"

curl -N \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN_COMMON}" \
  -X POST "${BASE_URL}/api/ai/chat/student/stream?conversationId=security-unbound-user-1" \
  -d '{
    "prompt": "帮我查一下高等数学成绩",
    "query": "帮我查一下高等数学成绩",
    "conversationId": "security-unbound-user-1"
  }'
