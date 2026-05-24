#!/usr/bin/env bash
# Expected: studentA must be rejected by /system/admin/edu/score.
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
TOKEN_STUDENT_A="${TOKEN_STUDENT_A:-REPLACE_ME}"
STUDENT_ID_A="${STUDENT_ID_A:-202301010001}"

curl -G -i \
  -H "Authorization: Bearer ${TOKEN_STUDENT_A}" \
  --data-urlencode "studentId=${STUDENT_ID_A}" \
  "${BASE_URL}/system/admin/edu/score"
