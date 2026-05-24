#!/usr/bin/env bash
# 预期：匿名可访问公共聊天，但不应返回真实成绩或一卡通数据。
# 辅助验证：服务端日志中 studentTools 应为 false。

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"

curl -N \
  -H "Content-Type: application/json" \
  -X POST "${BASE_URL}/api/ai/chat/public/stream?conversationId=security-public-anon-2" \
  -d '{
    "prompt": "帮我查一下我的高等数学成绩和一卡通余额",
    "query": "帮我查一下我的高等数学成绩和一卡通余额",
    "conversationId": "security-public-anon-2"
  }'
