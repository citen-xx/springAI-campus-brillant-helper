import json
import logging
from contextlib import asynccontextmanager
from typing import Any

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import StreamingResponse
from redis.asyncio import Redis

from app.config import Settings
from app.metrics import MetricsRegistry
from app.models import RagRequest, RetrievalRequest
from app.service import PublicRagService
from app.vector_store import VectorStoreError


logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    yield
    service = getattr(app.state, "rag_service", None)
    if service is not None:
        await service.close()


def create_app(rag_service: PublicRagService | None = None) -> FastAPI:
    application = FastAPI(title="Campus AI RAG Agent", lifespan=lifespan)
    application.state.rag_service = rag_service
    application.state.metrics = (
        rag_service.metrics if rag_service is not None and hasattr(rag_service, "metrics")
        else MetricsRegistry()
    )

    @application.get("/metrics")
    async def metrics(request: Request) -> dict[str, object]:
        return request.app.state.metrics.snapshot()

    @application.post("/rag/retrieve")
    async def retrieve(body: RetrievalRequest, request: Request) -> dict[str, object]:
        service = _get_service(request.app)
        try:
            chunks = await service.retrieve(body.question, body.topK)
        except VectorStoreError as exc:
            logger.exception("RAG evaluation retrieval failed")
            raise HTTPException(
                status_code=503, detail="RAG retrieval is temporarily unavailable"
            ) from exc
        return {
            "topK": body.topK,
            "similarityThreshold": service.similarity_threshold,
            "sources": [
                chunk.to_source().model_dump(mode="json", exclude_none=False)
                for chunk in chunks
            ],
        }

    @application.post("/rag/public/stream")
    async def public_rag_stream(body: RagRequest, request: Request) -> StreamingResponse:
        service = _get_service(request.app)
        try:
            chunks = await service.retrieve(body.question)
        except VectorStoreError as exc:
            logger.exception("Public RAG retrieval failed")
            raise HTTPException(
                status_code=503, detail="RAG retrieval is temporarily unavailable"
            ) from exc

        sources = [
            chunk.to_source().model_dump(mode="json", exclude_none=False)
            for chunk in chunks
        ]

        async def event_stream():
            async for delta in service.stream_answer(body.question, chunks, body.history):
                yield format_sse("answer", {"answer": delta})
            yield format_sse("sources", {"sources": sources})

        return StreamingResponse(
            event_stream(),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "X-Accel-Buffering": "no",
            },
        )

    @application.post("/rag/student/stream")
    async def student_rag_stream(body: RagRequest, request: Request) -> StreamingResponse:
        callback_token = request.headers.get("X-AI-Callback-Token", "").strip()
        callback_conversation_id = request.headers.get("X-AI-Callback-Conversation-Id", "").strip()
        if not callback_token or not callback_conversation_id:
            raise HTTPException(status_code=401, detail="student callback credentials are required")
        service = _get_service(request.app)
        try:
            chunks = await service.retrieve(body.question)
        except VectorStoreError as exc:
            logger.exception("Student RAG retrieval failed")
            raise HTTPException(
                status_code=503, detail="RAG retrieval is temporarily unavailable"
            ) from exc

        sources = [
            chunk.to_source().model_dump(mode="json", exclude_none=False)
            for chunk in chunks
        ]

        async def event_stream():
            async for delta in service.stream_student_answer(
                body.question,
                chunks,
                body.history,
                callback_token,
                callback_conversation_id,
            ):
                yield format_sse("answer", {"answer": delta})
            yield format_sse("sources", {"sources": sources})

        return StreamingResponse(
            event_stream(),
            media_type="text/event-stream",
            headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
        )

    return application


def _get_service(application: FastAPI) -> PublicRagService:
    service = getattr(application.state, "rag_service", None)
    if service is not None:
        return service

    settings = Settings.from_env()
    redis_client = Redis.from_url(
        settings.vector_redis_uri,
        decode_responses=False,
        protocol=2,
        socket_connect_timeout=settings.redis_timeout_seconds,
        socket_timeout=settings.redis_timeout_seconds,
    )
    service = PublicRagService(settings, redis_client, metrics=application.state.metrics)
    application.state.rag_service = service
    return service


def format_sse(event: str, data: dict[str, Any]) -> str:
    payload = {"event": event, **data}
    encoded = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    return f"data: {encoded}\n\n"


app = create_app()
