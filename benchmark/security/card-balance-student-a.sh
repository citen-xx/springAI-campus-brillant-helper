#!/usr/bin/env bash
# 预期：studentA 查询自己的一卡通余额成功。

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
TOKEN_STUDENT_A="${TOKEN_STUDENT_A:-REPLACE_ME}"

curl -i \
  -H "Authorization: Bearer ${TOKEN_STUDENT_A}" \
  "${BASE_URL}/system/edu/api/card/balance"
