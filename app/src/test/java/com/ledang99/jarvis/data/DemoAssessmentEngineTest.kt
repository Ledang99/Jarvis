package com.ledang99.jarvis.data

import com.ledang99.jarvis.domain.CaseInput
import com.ledang99.jarvis.domain.CaseType
import com.ledang99.jarvis.domain.RiskLevel
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAssessmentEngineTest {
    private val engine = DemoAssessmentEngine(
        Clock.fixed(Instant.parse("2026-09-22T09:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun `active ransomware incident is critical and actionable`() {
        val report = engine.analyze(
            CaseInput(
                title = "Active ransomware alert",
                type = CaseType.INCIDENT,
                description = "A workstation appears compromised and files are being encrypted.",
                environment = "Finance endpoint",
            ),
        )

        assertEquals(RiskLevel.CRITICAL, report.risk)
        assertEquals(3, report.remediations.size)
        assertTrue(report.summary.contains("finance endpoint", ignoreCase = true))
        assertEquals(Instant.parse("2026-09-22T09:00:00Z"), report.generatedAt)
    }

    @Test
    fun `hardening request is moderate and states limitations`() {
        val report = engine.analyze(
            CaseInput(
                title = "Android fleet baseline",
                type = CaseType.HARDENING,
                description = "Review our configuration approach.",
                environment = "Managed Android devices",
            ),
        )

        assertEquals(RiskLevel.MODERATE, report.risk)
        assertTrue(report.limitations.contains("did not inspect"))
        assertTrue(report.references.isNotEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank descriptions are rejected`() {
        engine.analyze(
            CaseInput(
                title = "Incomplete case",
                type = CaseType.VULNERABILITY,
                description = " ",
                environment = "",
            ),
        )
    }
}
