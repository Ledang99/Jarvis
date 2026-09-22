from fastapi import FastAPI

from .engine import LiveAssessmentEngine
from .models import AssessmentResponse, CaseRequest

app = FastAPI(
    title="Jarvis Defensive Assessment API",
    summary="Auditable backend engine for the Jarvis Android client.",
    version="0.2.0",
)
engine = LiveAssessmentEngine()


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
async def create_assessment(case: CaseRequest) -> AssessmentResponse:
    return await engine.assess(case)
