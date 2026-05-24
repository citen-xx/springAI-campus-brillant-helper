#!/usr/bin/env bash
# 预期：旧教育接口不再允许匿名按 studentId 或 subject 访问，应被拒绝。

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"

curl -G -i \
  --data-urlencode "subject=高等数学" \
  --data-urlencode "studentId=202301010002" \
  "${BASE_URL}/system/edu/api/score"
