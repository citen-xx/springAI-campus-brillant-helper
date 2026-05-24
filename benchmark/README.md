# Benchmark Templates

This directory contains benchmark templates for the current project.

Files:

- `wrk/sse_stream.lua`
- `wrk/legacy_chat.lua`
- `ab/sse_stream.json`
- `ab/legacy_chat.json`
- `ab/README.md`
- `curl/README.md`
- `jmeter/README.md`

Notes:

- Do not put real tokens, passwords, API keys, or file URLs into these templates.
- `/api/ai/chat/stream` is anonymous in code, but the frontend will pass `Authorization` if available.
- `/system/knowledge/import-file` requires `Authorization: Bearer <TOKEN>` and the `system:knowledge:add` permission.
- `ab` is only for smoke testing. For long-lived SSE, prefer `wrk` or JMeter.
