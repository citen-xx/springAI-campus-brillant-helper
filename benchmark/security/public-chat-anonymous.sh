#!/usr/bin/env bash
# 预期：匿名可访问公共聊天；只做公共 RAG 问答。

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"

curl -N \
  -H "Content-Type: application/json" \
  -X POST "${BASE_URL}/api/ai/chat/public/stream?conversationId=security-public-anon-1" \
  -d '{
    "prompt": "介绍一下奖学金申请流程",
    "query": "介绍一下奖学金申请流程",
    "conversationId": "security-public-anon-1"
  }'
