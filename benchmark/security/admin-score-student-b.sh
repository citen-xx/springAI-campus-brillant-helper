#!/usr/bin/env bash
# Expected: admin can query studentB scores through /system/admin/edu/score.
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
TOKEN_ADMIN="${TOKEN_ADMIN:-REPLACE_ME}"
STUDENT_ID_B="${STUDENT_ID_B:-202301010002}"

curl -G -i \
  -H "Authorization: Bearer ${TOKEN_ADMIN}" \
  --data-urlencode "studentId=${STUDENT_ID_B}" \
  "${BASE_URL}/system/admin/edu/score"
