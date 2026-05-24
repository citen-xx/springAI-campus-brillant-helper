#!/usr/bin/env bash
# 预期：studentA 通过学生自助接口查询自己的高等数学成绩成功。

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
TOKEN_STUDENT_A="${TOKEN_STUDENT_A:-REPLACE_ME}"

curl -G -i \
  -H "Authorization: Bearer ${TOKEN_STUDENT_A}" \
  --data-urlencode "subject=高等数学" \
  "${BASE_URL}/system/edu/api/score"
