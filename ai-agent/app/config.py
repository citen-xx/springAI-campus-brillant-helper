import os

from pydantic import BaseModel, ConfigDict, Field


class Settings(BaseModel):
    model_config = ConfigDict(frozen=True)

    dashscope_api_key: str = Field(min_length=1, repr=False)
    dashscope_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode"
    chat_model: str = "qwen-plus"
    embedding_model: str = "text-embedding-v3"
    vector_redis_uri: str = "redis://localhost:6379"
    vector_index_name: str = "campus_knowledge_v2"
    vector_key_prefix: str = "campus_knowledge_v2"
    rag_top_k: int = Field(default=3, ge=1, le=20)
    similarity_threshold: float = Field(default=0.6, ge=0.0, le=1.0)
    redis_timeout_seconds: float = Field(default=10.0, gt=0.0)
    java_callback_base_url: str = "http://127.0.0.1:8080"
    host: str = "127.0.0.1"
    port: int = Field(default=8090, ge=1, le=65_535)

    @classmethod
    def from_env(cls) -> "Settings":
        api_key = os.getenv("DASHSCOPE_API_KEY", "").strip()
        if not api_key:
            raise RuntimeError("DASHSCOPE_API_KEY is required")

        return cls(
            dashscope_api_key=api_key,
            dashscope_base_url=_environment_value(
                "DASHSCOPE_BASE_URL",
                "https://dashscope.aliyuncs.com/compatible-mode",
            ),
            chat_model=_environment_value("DASHSCOPE_CHAT_MODEL", "qwen-plus"),
            embedding_model=_environment_value(
                "DASHSCOPE_EMBEDDING_MODEL", "text-embedding-v3"
            ),
            vector_redis_uri=_environment_value(
                "VECTOR_REDIS_URI", "redis://localhost:6379"
            ),
            vector_index_name=_environment_value(
                "VECTOR_INDEX_NAME", "campus_knowledge_v2"
            ),
            vector_key_prefix=_environment_value(
                "VECTOR_KEY_PREFIX", "campus_knowledge_v2"
            ),
            rag_top_k=int(_environment_value("RAG_TOP_K", "3")),
            similarity_threshold=float(
                _environment_value("RAG_SIMILARITY_THRESHOLD", "0.6")
            ),
            redis_timeout_seconds=float(
                _environment_value("AI_AGENT_REDIS_TIMEOUT_SECONDS", "10")
            ),
            java_callback_base_url=_environment_value(
                "JAVA_CALLBACK_BASE_URL", "http://127.0.0.1:8080"
            ).rstrip("/"),
            host=_environment_value("AI_AGENT_HOST", "127.0.0.1"),
            port=int(_environment_value("AI_AGENT_PORT", "8090")),
        )

    @property
    def openai_base_url(self) -> str:
        base_url = self.dashscope_base_url.rstrip("/")
        if base_url.endswith("/v1"):
            return base_url
        return f"{base_url}/v1"


def _environment_value(name: str, default: str) -> str:
    value = os.getenv(name)
    return value.strip() if value and value.strip() else default
