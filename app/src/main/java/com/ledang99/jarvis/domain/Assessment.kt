package com.ledang99.jarvis.domain

import java.time.Instant

enum class CaseType(val label: String, val prompt: String) {
    VULNERABILITY("Vulnerability", "Assess a CVE, exposed service, or outdated component"),
    INCIDENT("Incident", "Triage suspicious activity or a possible compromise"),
    PHISHING("Phishing", "Review a suspicious message, link, or sender"),
    HARDENING("Hardening", "Improve the security posture of a system or application"),
}

enum class RiskLevel(val label: String) {
    LOW("Low"),
    MODERATE("Moderate"),
    HIGH("High"),
    CRITICAL("Critical"),
}

data class CaseInput(
    val title: String,
    val type: CaseType,
    val description: String,
    val environment: String,
)

data class Finding(
    val title: String,
    val detail: String,
    val severity: RiskLevel,
)

data class Remediation(
    val title: String,
    val action: String,
    val validation: String,
)

data class Reference(
    val publisher: String,
    val title: String,
    val url: String,
)

data class AssessmentReport(
    val case: CaseInput,
    val generatedAt: Instant,
    val risk: RiskLevel,
    val confidence: String,
    val engineMode: String,
    val summary: String,
    val findings: List<Finding>,
    val remediations: List<Remediation>,
    val references: List<Reference>,
    val limitations: String,
)
