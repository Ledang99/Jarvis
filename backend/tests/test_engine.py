from datetime import UTC, datetime

from fastapi.testclient import TestClient

from jarvis_backend.engine import AssessmentEngine
from jarvis_backend.main import app
from jarvis_backend.models import CaseRequest, CaseType, RiskLevel


def test_active_ransomware_incident_is_critical() -> None:
    engine = AssessmentEngine(
        now=lambda: datetime(2026, 9, 22, 10, 0, tzinfo=UTC),
    )
    report = engine.assess(
        CaseRequest(
            title="Active ransomware alert",
            type=CaseType.INCIDENT,
            description="A workstation is compromised and files are being encrypted.",
            environment="Finance endpoint",
        )
    )

    assert report.risk is RiskLevel.CRITICAL
    assert report.generated_at == datetime(2026, 9, 22, 10, 0, tzinfo=UTC)
    assert len(report.remediations) == 3
    assert report.metadata.engine == "policy-v1"


def test_assessment_endpoint_returns_mobile_contract() -> None:
    response = TestClient(app).post(
        "/v1/assessments",
        json={
            "title": "Suspicious email",
            "type": "phishing",
            "description": "A user received an unexpected password reset link.",
            "environment": "Microsoft 365",
        },
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["risk"] == "high"
    assert payload["metadata"]["mode"] == "Backend policy engine"
    assert len(payload["findings"]) == 3


def test_short_descriptions_are_rejected() -> None:
    response = TestClient(app).post(
        "/v1/assessments",
        json={
            "title": "Short input",
            "type": "incident",
            "description": "too short",
        },
    )

    assert response.status_code == 422
