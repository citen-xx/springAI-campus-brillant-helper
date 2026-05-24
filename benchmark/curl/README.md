# curl Examples

## 1. SSE stream chat

```bash
curl -N -w "\nstart=%{time_starttransfer}s total=%{time_total}s\n" \
  -H "Content-Type: application/json" \
  -X POST "http://127.0.0.1:8080/api/ai/chat/stream?conversationId=bench-curl-1" \
  -d '{
    "prompt": "Please explain the scholarship application process.",
    "query": "Please explain the scholarship application process.",
    "conversationId": "bench-curl-1"
  }'
```

## 2. Legacy chat

```bash
curl -N -w "\nstart=%{time_starttransfer}s total=%{time_total}s\n" \
  -H "Content-Type: application/json" \
  -X POST "http://127.0.0.1:8080/system/ai/chat" \
  -d '{
    "query": "Please explain the campus leave process.",
    "user": "bench-user-1"
  }'
```

## 3. Login to get a token

```bash
curl \
  -H "Content-Type: application/json" \
  -X POST "http://127.0.0.1:8080/login" \
  -d '{
    "username": "<USERNAME>",
    "password": "<PASSWORD>",
    "code": "<CAPTCHA_CODE>",
    "uuid": "<CAPTCHA_UUID>"
  }'
```

## 4. Upload a knowledge document

```bash
curl \
  -H "Authorization: Bearer <TOKEN>" \
  -F "file=@/abs/path/demo.pdf" \
  -F "docName=benchmark-demo" \
  -F "remark=benchmark upload" \
  "http://127.0.0.1:8080/system/knowledge/import-file"
```

## 5. Optional RAG upload endpoint

```bash
curl \
  -H "Authorization: Bearer <TOKEN>" \
  -F "file=@/abs/path/demo.pdf" \
  "http://127.0.0.1:8080/system/rag/upload-and-import"
```
