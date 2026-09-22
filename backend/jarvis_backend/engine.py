from collections.abc import Callable
from datetime import UTC, datetime

from .models import (
    AssessmentResponse,
    CaseRequest,
    CaseType,
    EngineMetadata,
    Finding,
    Reference,
    Remediation,
    RiskLevel,
    WorkflowStage,
)


class AssessmentEngine:
    """Auditable defensive workflow used by the mobile client.

    This engine is deliberately deterministic. It provides a safe backend
    boundary now and can later delegate the research stage to a citation-aware
    model without changing the Android API contract.
    """

    version = "policy-v1"

    def __init__(self, now: Callable[[], datetime] | None = None) -> None:
        self._now = now or (lambda: datetime.now(UTC))

    def assess(self, case: CaseRequest) -> AssessmentResponse:
        risk = self._classify_risk(case)
        findings = self._findings(case.type)
        remediations = self._remediations(case.type)
        references = self._references(case.type)
        target = case.environment or "the described environment"

        return AssessmentResponse(
            case=case,
            generated_at=self._now(),
            risk=risk,
            confidence="Preliminary",
            summary=(
                f"The reported {case.type.value} scenario presents a "
                f"{risk.value} preliminary risk to {target}. Preserve evidence "
                "first, confirm the scope, and apply the remediation steps in "
                "order. Escalate to a qualified incident responder if active "
                "compromise is suspected."
            ),
            findings=findings,
            remediations=remediations,
            references=references,
            limitations=(
                "The backend structured the information provided but did not "
                "inspect the environment or perform live threat-intelligence "
                "research. References are starting points and must be verified."
            ),
            metadata=EngineMetadata(
                engine=self.version,
                mode="Backend policy engine",
                workflow=[
                    WorkflowStage(
                        name="scope_validation",
                        note="Validated required case context and input boundaries.",
                    ),
                    WorkflowStage(
                        name="risk_triage",
                        note="Classified urgency using transparent defensive rules.",
                    ),
                    WorkflowStage(
                        name="control_mapping",
                        note="Selected scenario-specific findings and mitigations.",
                    ),
                    WorkflowStage(
                        name="report_drafting",
                        note="Prepared a structured response for review and PDF export.",
                    ),
                ],
            ),
        )

    @staticmethod
    def _classify_risk(case: CaseRequest) -> RiskLevel:
        content = f"{case.title} {case.description}".lower()
        critical_indicators = (
            "ransomware",
            "active exploit",
            "compromised",
            "data breach",
            "files are being encrypted",
        )
        high_indicators = (
            "critical",
            "remote code execution",
            "publicly exposed",
            "credentials entered",
        )

        if any(indicator in content for indicator in critical_indicators):
            return RiskLevel.CRITICAL
        if case.type in (CaseType.INCIDENT, CaseType.PHISHING):
            return RiskLevel.HIGH
        if any(indicator in content for indicator in high_indicators):
            return RiskLevel.HIGH
        if case.type is CaseType.VULNERABILITY:
            return RiskLevel.HIGH
        return RiskLevel.MODERATE

    @staticmethod
    def _findings(case_type: CaseType) -> list[Finding]:
        profiles = {
            CaseType.VULNERABILITY: [
                Finding(
                    title="Exposure is not yet verified",
                    detail=(
                        "Confirm whether the affected component is reachable and "
                        "whether the reported condition applies to its exact version."
                    ),
                    severity=RiskLevel.HIGH,
                ),
                Finding(
                    title="Asset and version evidence is required",
                    detail=(
                        "Record the product, version, configuration, privileges, "
                        "and network exposure before selecting a patch path."
                    ),
                    severity=RiskLevel.MODERATE,
                ),
                Finding(
                    title="Compensating controls are unknown",
                    detail=(
                        "Segmentation, WAF rules, EDR coverage, and other mitigating "
                        "controls were not established in the supplied context."
                    ),
                    severity=RiskLevel.MODERATE,
                ),
            ],
            CaseType.INCIDENT: [
                Finding(
                    title="Potential compromise requires containment",
                    detail=(
                        "Treat the report as unverified but time-sensitive until "
                        "indicators are validated."
                    ),
                    severity=RiskLevel.HIGH,
                ),
                Finding(
                    title="Evidence preservation is essential",
                    detail=(
                        "Volatile logs, timestamps, alerts, and affected account "
                        "details may be lost during remediation."
                    ),
                    severity=RiskLevel.HIGH,
                ),
                Finding(
                    title="Impact scope is incomplete",
                    detail=(
                        "Adjacent identities, endpoints, and cloud sessions have "
                        "not yet been evaluated."
                    ),
                    severity=RiskLevel.MODERATE,
                ),
            ],
            CaseType.PHISHING: [
                Finding(
                    title="Sender identity is unverified",
                    detail=(
                        "Display names and message content alone cannot establish "
                        "the sender's authenticity."
                    ),
                    severity=RiskLevel.HIGH,
                ),
                Finding(
                    title="Links and attachments may be hostile",
                    detail=(
                        "Preserve suspicious content without opening it on a "
                        "production device."
                    ),
                    severity=RiskLevel.HIGH,
                ),
                Finding(
                    title="Account exposure is unknown",
                    detail=(
                        "Confirm whether credentials were entered, content was "
                        "opened, or an MFA prompt was approved."
                    ),
                    severity=RiskLevel.MODERATE,
                ),
            ],
            CaseType.HARDENING: [
                Finding(
                    title="Baseline is not established",
                    detail=(
                        "A secure configuration baseline and current exceptions "
                        "have not been documented."
                    ),
                    severity=RiskLevel.MODERATE,
                ),
                Finding(
                    title="Control coverage needs verification",
                    detail=(
                        "Identity, patching, logging, backup, and endpoint controls "
                        "should be tested rather than assumed."
                    ),
                    severity=RiskLevel.MODERATE,
                ),
                Finding(
                    title="Recovery readiness is unknown",
                    detail=(
                        "Backup restoration and incident communication procedures "
                        "were not included in the supplied context."
                    ),
                    severity=RiskLevel.MODERATE,
                ),
            ],
        }
        return profiles[case_type]

    @staticmethod
    def _remediations(case_type: CaseType) -> list[Remediation]:
        profiles = {
            CaseType.VULNERABILITY: [
                Remediation(
                    title="Confirm affected assets",
                    action=(
                        "Inventory exact versions, exposure paths, privileges, "
                        "and existing mitigations."
                    ),
                    validation="Attach version and reachability evidence to the case.",
                ),
                Remediation(
                    title="Reduce exposure",
                    action=(
                        "Restrict access, segment the service, or disable the "
                        "vulnerable feature while testing a permanent fix."
                    ),
                    validation=(
                        "Verify unauthorized paths are blocked without disrupting "
                        "required access."
                    ),
                ),
                Remediation(
                    title="Patch through change control",
                    action=(
                        "Test and deploy the vendor-supported update with a "
                        "documented rollback plan."
                    ),
                    validation=(
                        "Re-scan the version and review service health and security logs."
                    ),
                ),
            ],
            CaseType.INCIDENT: [
                Remediation(
                    title="Preserve and timestamp evidence",
                    action=(
                        "Collect relevant alerts, logs, account events, and system "
                        "details before destructive actions."
                    ),
                    validation="Store evidence read-only and record who collected it.",
                ),
                Remediation(
                    title="Contain confirmed affected assets",
                    action=(
                        "Isolate compromised endpoints or sessions using approved "
                        "incident-response procedures."
                    ),
                    validation=(
                        "Confirm malicious communication stopped while monitoring "
                        "adjacent assets."
                    ),
                ),
                Remediation(
                    title="Eradicate and recover",
                    action=(
                        "Remove persistence, rotate exposed credentials, restore "
                        "trusted systems, and increase monitoring."
                    ),
                    validation=(
                        "Validate with clean scans, authentication review, and "
                        "post-recovery observation."
                    ),
                ),
            ],
            CaseType.PHISHING: [
                Remediation(
                    title="Preserve the original message",
                    action=(
                        "Retain full headers, sender details, URLs, and attachment "
                        "hashes without opening content."
                    ),
                    validation=(
                        "Confirm the original message is available to the security team."
                    ),
                ),
                Remediation(
                    title="Contain potential account exposure",
                    action=(
                        "If the user interacted, revoke sessions, reset credentials, "
                        "and review MFA methods."
                    ),
                    validation=(
                        "Confirm old sessions fail and recent account activity is legitimate."
                    ),
                ),
                Remediation(
                    title="Block and search",
                    action=(
                        "Block validated indicators and search mailboxes and "
                        "telemetry for related messages."
                    ),
                    validation=(
                        "Verify matches are quarantined and document impacted users."
                    ),
                ),
            ],
            CaseType.HARDENING: [
                Remediation(
                    title="Select a recognized baseline",
                    action=(
                        "Choose an applicable CIS Benchmark or vendor hardening "
                        "standard and document exceptions."
                    ),
                    validation="Record baseline coverage and approved deviations.",
                ),
                Remediation(
                    title="Prioritize identity and patching",
                    action=(
                        "Enforce MFA, least privilege, supported versions, and a "
                        "measurable patch process."
                    ),
                    validation=(
                        "Test privileged access and review overdue critical updates."
                    ),
                ),
                Remediation(
                    title="Validate detection and recovery",
                    action=(
                        "Centralize useful logs, alert on high-risk behavior, and "
                        "test restoration from protected backups."
                    ),
                    validation=(
                        "Run a tabletop scenario and complete one documented restore test."
                    ),
                ),
            ],
        }
        return profiles[case_type]

    @staticmethod
    def _references(case_type: CaseType) -> list[Reference]:
        common = Reference(
            publisher="NIST",
            title="Cybersecurity Framework 2.0",
            url="https://www.nist.gov/cyberframework",
        )
        specialized = {
            CaseType.VULNERABILITY: Reference(
                publisher="CISA",
                title="Known Exploited Vulnerabilities Catalog",
                url="https://www.cisa.gov/known-exploited-vulnerabilities-catalog",
            ),
            CaseType.INCIDENT: Reference(
                publisher="NIST",
                title="Incident Response Recommendations",
                url="https://csrc.nist.gov/pubs/sp/800/61/r3/final",
            ),
            CaseType.PHISHING: Reference(
                publisher="CISA",
                title="Recognize and Report Phishing",
                url=(
                    "https://www.cisa.gov/secure-our-world/"
                    "recognize-and-report-phishing"
                ),
            ),
            CaseType.HARDENING: Reference(
                publisher="CIS",
                title="CIS Benchmarks",
                url="https://www.cisecurity.org/cis-benchmarks",
            ),
        }
        return [specialized[case_type], common]
