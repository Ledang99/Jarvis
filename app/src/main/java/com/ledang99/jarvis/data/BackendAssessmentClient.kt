package com.ledang99.jarvis.data

import com.ledang99.jarvis.domain.AssessmentReport
import com.ledang99.jarvis.domain.CaseInput
import com.ledang99.jarvis.domain.CaseType
import com.ledang99.jarvis.domain.Finding
import com.ledang99.jarvis.domain.Reference
import com.ledang99.jarvis.domain.Remediation
import com.ledang99.jarvis.domain.RiskLevel
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class BackendAssessmentClient(
    baseUrl: String,
) {
    private val assessmentUrl = "${baseUrl.trimEnd('/')}/v1/assessments"

    suspend fun assess(input: CaseInput): Result<AssessmentReport> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(assessmentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4_000
                readTimeout = 25_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            try {
                val request = JSONObject()
                    .put("title", input.title)
                    .put("type", input.type.name.lowercase())
                    .put("description", input.description)
                    .put("environment", input.environment)

                connection.outputStream.bufferedWriter().use { writer ->
                    writer.write(request.toString())
                }

                val status = connection.responseCode
                val body = (if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                })?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (status !in 200..299) {
                    error("Backend returned HTTP $status: ${body.take(240)}")
                }
                parseReport(JSONObject(body))
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parseReport(payload: JSONObject): AssessmentReport {
        val caseJson = payload.getJSONObject("case")
        val findingsJson = payload.getJSONArray("findings")
        val remediationsJson = payload.getJSONArray("remediations")
        val referencesJson = payload.getJSONArray("references")
        val metadata = payload.optJSONObject("metadata")

        return AssessmentReport(
            case = CaseInput(
                title = caseJson.getString("title"),
                type = CaseType.valueOf(caseJson.getString("type").uppercase()),
                description = caseJson.getString("description"),
                environment = caseJson.optString("environment"),
            ),
            generatedAt = Instant.parse(payload.getString("generated_at")),
            risk = parseRisk(payload.getString("risk")),
            confidence = payload.getString("confidence"),
            summary = payload.getString("summary"),
            findings = List(findingsJson.length()) { index ->
                findingsJson.getJSONObject(index).let { finding ->
                    Finding(
                        title = finding.getString("title"),
                        detail = finding.getString("detail"),
                        severity = parseRisk(finding.getString("severity")),
                    )
                }
            },
            remediations = List(remediationsJson.length()) { index ->
                remediationsJson.getJSONObject(index).let { remediation ->
                    Remediation(
                        title = remediation.getString("title"),
                        action = remediation.getString("action"),
                        validation = remediation.getString("validation"),
                    )
                }
            },
            references = List(referencesJson.length()) { index ->
                referencesJson.getJSONObject(index).let { reference ->
                    Reference(
                        publisher = reference.getString("publisher"),
                        title = reference.getString("title"),
                        url = reference.getString("url"),
                        verificationStatus = reference.optString(
                            "verification_status",
                            "not_checked",
                        ),
                        retrievedAt = reference.optString("retrieved_at")
                            .takeIf(String::isNotBlank)
                            ?.let { value -> runCatching { Instant.parse(value) }.getOrNull() },
                        evidence = reference.optString("evidence"),
                    )
                }
            },
            limitations = payload.getString("limitations"),
            engineMode = metadata?.optString("mode").orEmpty()
                .ifBlank { "Backend assessment" },
        )
    }

    private fun parseRisk(value: String): RiskLevel =
        RiskLevel.valueOf(value.uppercase())
}
