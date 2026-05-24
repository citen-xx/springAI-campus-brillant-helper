#!/usr/bin/env bash
# 预期：未登录访问学生聊天应被拒绝，不应返回个人数据。
# 结果通常是 401/403 之一，以当前安全异常处理为准。

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"

curl -i -N \
  -H "Content-Type: application/json" \
  -X POST "${BASE_URL}/api/ai/chat/student/stream?conversationId=security-student-anon-1" \
  -d '{
    "prompt": "帮我查一下高等数学成绩",
    "query": "帮我查一下高等数学成绩",
    "conversationId": "security-student-anon-1"
  }'
