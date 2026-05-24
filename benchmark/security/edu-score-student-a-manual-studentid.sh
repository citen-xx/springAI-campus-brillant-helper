#!/usr/bin/env bash
# 预期：studentA 手工传 studentId=202301010002 也不会越权，后端应忽略该参数。

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
TOKEN_STUDENT_A="${TOKEN_STUDENT_A:-REPLACE_ME}"

curl -G -i \
  -H "Authorization: Bearer ${TOKEN_STUDENT_A}" \
  --data-urlencode "subject=高等数学" \
  --data-urlencode "studentId=202301010002" \
  "${BASE_URL}/system/edu/api/score"
