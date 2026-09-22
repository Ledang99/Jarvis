package com.ledang99.jarvis.data

import com.ledang99.jarvis.domain.AssessmentReport
import com.ledang99.jarvis.domain.CaseInput
import com.ledang99.jarvis.domain.CaseType
import com.ledang99.jarvis.domain.Finding
import com.ledang99.jarvis.domain.Reference
import com.ledang99.jarvis.domain.Remediation
import com.ledang99.jarvis.domain.RiskLevel
import java.time.Clock
import java.time.Instant

/**
 * A deterministic local engine used while the live research service is being built.
 * It never claims that external systems or sources were inspected.
 */
class DemoAssessmentEngine(
    private val clock: Clock = Clock.systemUTC(),
) {
    fun analyze(input: CaseInput): AssessmentReport {
        require(input.title.isNotBlank()) { "A case title is required." }
        require(input.description.isNotBlank()) { "A case description is required." }

        val content = "${input.title} ${input.description}".lowercase()
        val risk = when {
            listOf("ransomware", "active exploit", "compromised", "data breach")
                .any(content::contains) -> RiskLevel.CRITICAL
            input.type == CaseType.INCIDENT || input.type == CaseType.PHISHING -> RiskLevel.HIGH
            listOf("critical", "remote code execution", "publicly exposed")
                .any(content::contains) -> RiskLevel.HIGH
            input.type == CaseType.VULNERABILITY -> RiskLevel.HIGH
            else -> RiskLevel.MODERATE
        }

        val findings = findingsFor(input.type, input)
        val remediations = remediationsFor(input.type)

        return AssessmentReport(
            case = input,
            generatedAt = Instant.now(clock),
            risk = risk,
            confidence = "Preliminary",
            engineMode = "Local fallback",
            summary = summaryFor(input, risk),
            findings = findings,
            remediations = remediations,
            references = referencesFor(input.type),
            limitations = "This assessment was generated locally from the information provided. " +
                "Jarvis did not inspect the environment, query live threat intelligence, or verify external sources.",
        )
    }

    private fun summaryFor(input: CaseInput, risk: RiskLevel): String {
        val target = input.environment.ifBlank { "the described environment" }
        return "The reported ${input.type.label.lowercase()} scenario presents a " +
            "${risk.label.lowercase()} preliminary risk to $target. Preserve evidence first, " +
            "confirm the scope, and apply the remediation steps in order. Escalate to a qualified " +
            "incident responder if active compromise is suspected."
    }

    private fun findingsFor(type: CaseType, input: CaseInput): List<Finding> = when (type) {
        CaseType.VULNERABILITY -> listOf(
            Finding(
                "Exposure is not yet verified",
                "The supplied description does not prove whether the affected component is reachable or exploitable.",
                RiskLevel.HIGH,
            ),
            Finding(
                "Asset and version evidence required",
                "Record the exact product, version, configuration, and network exposure before selecting a patch path.",
                RiskLevel.MODERATE,
            ),
            Finding(
                "Compensating controls are unknown",
                "No evidence was provided for segmentation, WAF rules, EDR coverage, or other mitigating controls.",
                RiskLevel.MODERATE,
            ),
        )

        CaseType.INCIDENT -> listOf(
            Finding(
                "Potential compromise requires containment",
                "The report should be treated as unverified but time-sensitive until indicators are validated.",
                RiskLevel.HIGH,
            ),
            Finding(
                "Evidence preservation is essential",
                "Volatile logs, timestamps, alerts, and affected account details may be lost during remediation.",
                RiskLevel.HIGH,
            ),
            Finding(
                "Impact scope is incomplete",
                "Adjacent identities, endpoints, and cloud sessions have not yet been evaluated.",
                RiskLevel.MODERATE,
            ),
        )

        CaseType.PHISHING -> listOf(
            Finding(
                "Sender identity is unverified",
                "Display names and message content alone cannot establish the sender's authenticity.",
                RiskLevel.HIGH,
            ),
            Finding(
                "Links and attachments may be hostile",
                "Do not open suspicious content on a production device; preserve the original message for analysis.",
                RiskLevel.HIGH,
            ),
            Finding(
                "Account exposure is unknown",
                "Confirm whether credentials were entered, an attachment was opened, or an MFA prompt was approved.",
                RiskLevel.MODERATE,
            ),
        )

        CaseType.HARDENING -> listOf(
            Finding(
                "Baseline is not established",
                "A secure configuration baseline and current exceptions have not been documented.",
                RiskLevel.MODERATE,
            ),
            Finding(
                "Control coverage needs verification",
                "Identity, patching, logging, backup, and endpoint controls should be tested rather than assumed.",
                RiskLevel.MODERATE,
            ),
            Finding(
                "Recovery readiness is unknown",
                "Backup restoration and incident communication procedures were not included in the supplied context.",
                RiskLevel.MODERATE,
            ),
        )
    }

    private fun remediationsFor(type: CaseType): List<Remediation> = when (type) {
        CaseType.VULNERABILITY -> listOf(
            Remediation(
                "Confirm affected assets",
                "Inventory exact versions, exposure paths, privileges, and existing mitigations.",
                "Attach version and reachability evidence to the case.",
            ),
            Remediation(
                "Reduce exposure",
                "Restrict access, segment the service, or disable the vulnerable feature while testing a permanent fix.",
                "Verify that unauthorized paths are blocked without disrupting required access.",
            ),
            Remediation(
                "Patch through change control",
                "Test and deploy the vendor-supported update with a documented rollback plan.",
                "Re-scan the version and review service health and security logs.",
            ),
        )

        CaseType.INCIDENT -> listOf(
            Remediation(
                "Preserve and timestamp evidence",
                "Collect relevant alerts, logs, account events, and system details before destructive actions.",
                "Store evidence read-only and record who collected it.",
            ),
            Remediation(
                "Contain confirmed affected assets",
                "Isolate compromised endpoints or sessions using approved incident-response procedures.",
                "Confirm malicious communication has stopped while monitoring adjacent assets.",
            ),
            Remediation(
                "Eradicate and recover",
                "Remove persistence, rotate exposed credentials, restore trusted systems, and increase monitoring.",
                "Validate with clean scans, authentication review, and post-recovery observation.",
            ),
        )

        CaseType.PHISHING -> listOf(
            Remediation(
                "Preserve the original message",
                "Retain full headers, sender details, URLs, and attachment hashes without opening content.",
                "Confirm the original message is available to the security team.",
            ),
            Remediation(
                "Contain potential account exposure",
                "If the user interacted, revoke sessions, reset credentials, and review MFA methods.",
                "Confirm old sessions fail and recent account activity is legitimate.",
            ),
            Remediation(
                "Block and search",
                "Block validated indicators and search mailboxes and telemetry for related messages.",
                "Verify matches are quarantined and document any impacted users.",
            ),
        )

        CaseType.HARDENING -> listOf(
            Remediation(
                "Select a recognized baseline",
                "Choose an applicable CIS Benchmark or vendor hardening standard and document exceptions.",
                "Record baseline coverage and approved deviations.",
            ),
            Remediation(
                "Prioritize identity and patching",
                "Enforce MFA, least privilege, supported versions, and a measurable patch process.",
                "Test privileged access and review overdue critical updates.",
            ),
            Remediation(
                "Validate detection and recovery",
                "Centralize useful logs, alert on high-risk behavior, and test restoration from protected backups.",
                "Run a tabletop scenario and complete one documented restore test.",
            ),
        )
    }

    private fun referencesFor(type: CaseType): List<Reference> {
        val common = Reference(
            "NIST",
            "Cybersecurity Framework 2.0",
            "https://www.nist.gov/cyberframework",
        )
        val specialized = when (type) {
            CaseType.VULNERABILITY -> Reference(
                "CISA",
                "Known Exploited Vulnerabilities Catalog",
                "https://www.cisa.gov/known-exploited-vulnerabilities-catalog",
            )
            CaseType.INCIDENT -> Reference(
                "NIST",
                "Incident Response Recommendations",
                "https://csrc.nist.gov/pubs/sp/800/61/r3/final",
            )
            CaseType.PHISHING -> Reference(
                "CISA",
                "Recognize and Report Phishing",
                "https://www.cisa.gov/secure-our-world/recognize-and-report-phishing",
            )
            CaseType.HARDENING -> Reference(
                "CIS",
                "CIS Benchmarks",
                "https://www.cisecurity.org/cis-benchmarks",
            )
        }
        return listOf(specialized, common)
    }
}
