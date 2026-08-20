from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


class HistoryMessage(BaseModel):
    role: Literal["user", "assistant", "system"]
    content: str


class RagRequest(BaseModel):
    question: str = Field(min_length=1, max_length=4_000)
    history: list[HistoryMessage] = Field(default_factory=list, max_length=100)

    @field_validator("question")
    @classmethod
    def validate_question(cls, value: str) -> str:
        question = value.strip()
        if not question:
            raise ValueError("question must not be blank")
        return question


class RetrievalRequest(BaseModel):
    question: str = Field(min_length=1, max_length=4_000)
    topK: int = Field(default=5, ge=1, le=20)

    @field_validator("question")
    @classmethod
    def validate_question(cls, value: str) -> str:
        question = value.strip()
        if not question:
            raise ValueError("question must not be blank")
        return question


class StudentScoreToolInput(BaseModel):
    """The model may choose a course, but never an identity."""

    model_config = ConfigDict(extra="forbid")
    subject: str = Field(min_length=1, max_length=100)


class CardBalanceToolInput(BaseModel):
    """Empty schema intentionally prevents identity parameters."""

    model_config = ConfigDict(extra="forbid")
    pass


class RagSource(BaseModel):
    model_config = ConfigDict(extra="ignore")

    docId: int | None = None
    fileName: str | None = None
    section: str | None = None
    chunkIndex: int | None = None
    sourceUrl: str | None = None
    score: float
