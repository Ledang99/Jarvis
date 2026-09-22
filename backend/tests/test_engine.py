import asyncio
from datetime import UTC, datetime

import httpx
from fastapi.testclient import TestClient

import jarvis_backend.main as main_module
from jarvis_backend.engine import AssessmentEngine, LiveAssessmentEngine
from jarvis_backend.models import (
    CaseRequest,
    CaseType,
    Reference,
    RiskLevel,
    VerificationStatus,
)
from jarvis_backend.research import AuthoritativeResearchService


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


def test_assessment_endpoint_returns_verified_mobile_contract() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.scheme == "https"
        return httpx.Response(
            200,
            headers={"Last-Modified": "Tue, 22 Sep 2026 09:00:00 GMT"},
            request=request,
        )

    main_module.engine = LiveAssessmentEngine(
        research_service=AuthoritativeResearchService(
            transport=httpx.MockTransport(handler),
            now=lambda: datetime(2026, 9, 22, 10, 0, tzinfo=UTC),
        )
    )
    response = TestClient(main_module.app).post(
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
    assert payload["metadata"]["mode"] == "Live verified research"
    assert payload["references"][0]["verification_status"] == "verified"
    assert len(payload["findings"]) == 3


def test_short_descriptions_are_rejected() -> None:
    response = TestClient(main_module.app).post(
        "/v1/assessments",
        json={
            "title": "Short input",
            "type": "incident",
            "description": "too short",
        },
    )

    assert response.status_code == 422


def test_cve_research_combines_nvd_and_cisa_evidence() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.host == "services.nvd.nist.gov":
            return httpx.Response(
                200,
                json={
                    "vulnerabilities": [
                        {
                            "cve": {
                                "id": "CVE-2021-44228",
                                "published": "2021-12-10T10:15:09.143",
                                "descriptions": [
                                    {
                                        "lang": "en",
                                        "value": "Remote code execution in Log4j.",
                                    }
                                ],
                                "metrics": {
                                    "cvssMetricV31": [
                                        {
                                            "cvssData": {
                                                "baseScore": 10.0,
                                                "baseSeverity": "CRITICAL",
                                            }
                                        }
                                    ]
                                },
                            }
                        }
                    ]
                },
                request=request,
            )
        if "known_exploited_vulnerabilities.json" in request.url.path:
            return httpx.Response(
                200,
                json={
                    "vulnerabilities": [
                        {
                            "cveID": "CVE-2021-44228",
                            "dateAdded": "2021-12-10",
                            "dueDate": "2021-12-24",
                            "knownRansomwareCampaignUse": "Known",
                        }
                    ]
                },
                request=request,
            )
        return httpx.Response(200, request=request)

    service = AuthoritativeResearchService(
        transport=httpx.MockTransport(handler),
        now=lambda: datetime(2026, 9, 22, 10, 0, tzinfo=UTC),
    )
    report = asyncio.run(
        LiveAssessmentEngine(research_service=service).assess(
            CaseRequest(
                title="Review CVE-2021-44228",
                type=CaseType.VULNERABILITY,
                description="Determine whether CVE-2021-44228 needs urgent action.",
                environment="Internet-facing Java service",
            )
        )
    )

    assert report.risk is RiskLevel.CRITICAL
    assert report.metadata.mode == "Live verified research"
    assert any("CISA KEV match" in finding.title for finding in report.findings)
    assert any(reference.publisher == "NVD" for reference in report.references)


def test_unapproved_source_is_never_requested() -> None:
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(200, request=request)

    service = AuthoritativeResearchService(
        transport=httpx.MockTransport(handler),
    )
    result = asyncio.run(
        service.research(
            CaseRequest(
                title="Review suspicious message",
                type=CaseType.PHISHING,
                description="A user received an unexpected attachment by email.",
            ),
            [
                Reference(
                    publisher="Untrusted",
                    title="Untrusted source",
                    url="https://example.com/security",
                )
            ],
        )
    )

    assert requests == []
    assert result.references[0].verification_status is VerificationStatus.UNAVAILABLE
