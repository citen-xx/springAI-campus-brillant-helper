#!/usr/bin/env bash
# 预期：studentA 登录后通过学生聊天查询自己的成绩，结果应对应 studentA。

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
TOKEN_STUDENT_A="${TOKEN_STUDENT_A:-REPLACE_ME}"

curl -N \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN_STUDENT_A}" \
  -X POST "${BASE_URL}/api/ai/chat/student/stream?conversationId=security-student-a-self" \
  -d '{
    "prompt": "帮我查一下高等数学成绩",
    "query": "帮我查一下高等数学成绩",
    "conversationId": "security-student-a-self"
  }'
