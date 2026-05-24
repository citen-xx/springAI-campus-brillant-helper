#!/usr/bin/env bash
# 预期：旧 /api/ai/chat/stream 现在只做兼容公共问答，不再注册个人数据工具。
# 辅助验证：服务端日志中 studentTools 应为 false。

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"

curl -N \
  -H "Content-Type: application/json" \
  -X POST "${BASE_URL}/api/ai/chat/stream?conversationId=security-legacy-public-1" \
  -d '{
    "prompt": "帮我查一下我的高等数学成绩",
    "query": "帮我查一下我的高等数学成绩",
    "conversationId": "security-legacy-public-1"
  }'
