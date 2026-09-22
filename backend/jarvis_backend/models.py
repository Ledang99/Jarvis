from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field, field_validator


class CaseType(StrEnum):
    VULNERABILITY = "vulnerability"
    INCIDENT = "incident"
    PHISHING = "phishing"
    HARDENING = "hardening"


class RiskLevel(StrEnum):
    LOW = "low"
    MODERATE = "moderate"
    HIGH = "high"
    CRITICAL = "critical"


class CaseRequest(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)

    title: str = Field(min_length=3, max_length=160)
    type: CaseType
    description: str = Field(min_length=10, max_length=10_000)
    environment: str = Field(default="", max_length=500)

    @field_validator("title", "description", "environment")
    @classmethod
    def reject_control_characters(cls, value: str) -> str:
        if any(ord(character) < 32 and character not in "\n\t" for character in value):
            raise ValueError("Control characters are not allowed.")
        return value


class Finding(BaseModel):
    title: str
    detail: str
    severity: RiskLevel


class Remediation(BaseModel):
    title: str
    action: str
    validation: str


class Reference(BaseModel):
    publisher: str
    title: str
    url: str


class WorkflowStage(BaseModel):
    name: str
    status: str = "completed"
    note: str


class EngineMetadata(BaseModel):
    engine: str
    mode: str
    workflow: list[WorkflowStage]


class AssessmentResponse(BaseModel):
    case: CaseRequest
    generated_at: datetime
    risk: RiskLevel
    confidence: str
    summary: str
    findings: list[Finding]
    remediations: list[Remediation]
    references: list[Reference]
    limitations: str
    metadata: EngineMetadata
