#!/usr/bin/env bash
# Expected: admin can query studentA card balance through /system/admin/edu/card/balance.
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
TOKEN_ADMIN="${TOKEN_ADMIN:-REPLACE_ME}"
STUDENT_ID_A="${STUDENT_ID_A:-202301010001}"

curl -G -i \
  -H "Authorization: Bearer ${TOKEN_ADMIN}" \
  --data-urlencode "studentId=${STUDENT_ID_A}" \
  "${BASE_URL}/system/admin/edu/card/balance"
