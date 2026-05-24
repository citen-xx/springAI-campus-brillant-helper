# JMeter Plan Outline

Use JMeter when you need:

- more than 20 concurrent SSE connections
- different `conversationId` values per virtual user
- CSV-driven request bodies
- result files for interview evidence

Recommended plan:

1. Test Plan
2. User Defined Variables
3. HTTP Request Defaults
4. HTTP Header Manager
5. CSV Data Set Config
6. Thread Group
7. HTTP Request Sampler for `/api/ai/chat/stream`
8. View Results Tree
9. Summary Report
10. Aggregate Report

Suggested variables:

- `baseUrl=http://127.0.0.1:8080`
- `token=<TOKEN>`
- `conversationId=<CSV column>`
- `prompt=<CSV column>`

Headers:

- `Content-Type: application/json`
- `Authorization: Bearer ${token}` only when testing protected endpoints

Body for `/api/ai/chat/stream`:

```json
{
  "prompt": "${prompt}",
  "query": "${prompt}",
  "conversationId": "${conversationId}"
}
```

Body for `/system/ai/chat`:

```json
{
  "query": "${prompt}",
  "user": "${conversationId}"
}
```

Important:

- Save the `.jmx`, HTML report, and raw results as interview evidence.
- For SSE tests, prefer steady-state runs of 1 to 3 minutes instead of short burst tests.
- If you want to claim `200+` SSE connections, keep screenshots of JMeter thread count, application heap, and GC logs.
