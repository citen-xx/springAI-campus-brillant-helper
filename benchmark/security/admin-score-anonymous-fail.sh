#!/usr/bin/env bash
# Expected: anonymous access to /system/admin/edu/score must be rejected.
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
STUDENT_ID_A="${STUDENT_ID_A:-202301010001}"

curl -G -i \
  --data-urlencode "studentId=${STUDENT_ID_A}" \
  "${BASE_URL}/system/admin/edu/score"
