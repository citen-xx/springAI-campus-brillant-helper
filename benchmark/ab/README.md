# ab Examples

`ab` is not a good fit for long-lived SSE streams. Use it only for smoke testing.

Examples:

```bash
ab -n 50 -c 5 \
  -p benchmark/ab/legacy_chat.json \
  -T application/json \
  http://127.0.0.1:8080/system/ai/chat
```

```bash
ab -n 20 -c 2 \
  -p benchmark/ab/sse_stream.json \
  -T application/json \
  http://127.0.0.1:8080/api/ai/chat/stream
```

If you need first-byte timing, use `curl -N -w` instead of `ab`.
