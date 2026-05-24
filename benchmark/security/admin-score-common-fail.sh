#!/usr/bin/env bash
# Expected: userCommon must be rejected by /system/admin/edu/score.
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
TOKEN_COMMON="${TOKEN_COMMON:-REPLACE_ME}"
STUDENT_ID_A="${STUDENT_ID_A:-202301010001}"

curl -G -i \
  -H "Authorization: Bearer ${TOKEN_COMMON}" \
  --data-urlencode "studentId=${STUDENT_ID_A}" \
  "${BASE_URL}/system/admin/edu/score"
