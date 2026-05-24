#!/usr/bin/env bash
# Expected: studentB must be rejected by /system/admin/edu/card/balance.
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
TOKEN_STUDENT_B="${TOKEN_STUDENT_B:-REPLACE_ME}"
STUDENT_ID_A="${STUDENT_ID_A:-202301010001}"

curl -G -i \
  -H "Authorization: Bearer ${TOKEN_STUDENT_B}" \
  --data-urlencode "studentId=${STUDENT_ID_A}" \
  "${BASE_URL}/system/admin/edu/card/balance"
