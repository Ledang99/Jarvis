from fastapi import FastAPI

from .engine import AssessmentEngine
from .models import AssessmentResponse, CaseRequest

app = FastAPI(
    title="Jarvis Defensive Assessment API",
    summary="Auditable backend engine for the Jarvis Android client.",
    version="0.1.0",
)
engine = AssessmentEngine()


@app.get("/health")
def health() -> dict[str, str]:
    return {
        "status": "ok",
        "engine": engine.version,
    }


@app.post(
    "/v1/assessments",
    response_model=AssessmentResponse,
    response_model_exclude_none=True,
)
def create_assessment(case: CaseRequest) -> AssessmentResponse:
    return engine.assess(case)
