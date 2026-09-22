package com.ledang99.jarvis.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.ledang99.jarvis.domain.AssessmentReport
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object PdfReportExporter {
    fun export(
        context: Context,
        destination: Uri,
        report: AssessmentReport,
    ): Result<Unit> = runCatching {
        val document = PdfDocument()
        try {
            val writer = ReportWriter(document)
            writer.write(report)
            context.contentResolver.openOutputStream(destination)?.use(document::writeTo)
                ?: error("Unable to open the selected destination.")
        } finally {
            document.close()
        }
    }

    private class ReportWriter(
        private val document: PdfDocument,
    ) {
        private val pageWidth = 595
        private val pageHeight = 842
        private val margin = 48f
        private val contentWidth = pageWidth - (margin * 2)

        private val titlePaint = textPaint(24f, Color.rgb(8, 17, 31), true)
        private val headingPaint = textPaint(15f, Color.rgb(15, 118, 110), true)
        private val bodyPaint = textPaint(10.5f, Color.rgb(30, 41, 59), false)
        private val labelPaint = textPaint(9f, Color.rgb(71, 85, 105), true)
        private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(94, 234, 212)
        }

        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var cursorY = margin

        fun write(report: AssessmentReport) {
            newPage()
            page?.canvas?.drawRect(margin, cursorY, margin + 56f, cursorY + 6f, accentPaint)
            cursorY += 30f
            drawWrapped("JARVIS SECURITY ASSESSMENT", titlePaint, 30f)
            drawWrapped(report.case.title, headingPaint, 22f)
            cursorY += 4f

            keyValue("Assessment type", report.case.type.label)
            keyValue("Preliminary risk", report.risk.label)
            keyValue("Confidence", report.confidence)
            keyValue(
                "Generated",
                DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm z")
                    .withZone(ZoneId.systemDefault())
                    .format(report.generatedAt),
            )
            if (report.case.environment.isNotBlank()) {
                keyValue("Environment", report.case.environment)
            }

            section("Executive summary")
            drawWrapped(report.summary)

            section("Scope supplied by user")
            drawWrapped(report.case.description)

            section("Findings")
            report.findings.forEachIndexed { index, finding ->
                ensureSpace(74f)
                drawWrapped("${index + 1}. ${finding.title}  [${finding.severity.label}]", headingPaint, 20f)
                drawWrapped(finding.detail)
                cursorY += 6f
            }

            section("Recommended remediation")
            report.remediations.forEachIndexed { index, remediation ->
                ensureSpace(105f)
                drawWrapped("${index + 1}. ${remediation.title}", headingPaint, 20f)
                drawWrapped(remediation.action)
                cursorY += 3f
                drawWrapped("Validation: ${remediation.validation}", labelPaint, 15f)
                cursorY += 8f
            }

            section("Reference starting points")
            report.references.forEach { reference ->
                ensureSpace(52f)
                drawWrapped("${reference.publisher} — ${reference.title}", headingPaint, 19f)
                drawWrapped(reference.url, labelPaint, 14f)
                cursorY += 5f
            }

            section("Limitations")
            drawWrapped(report.limitations)
            cursorY += 12f
            drawWrapped(
                "This report supports defensive decision-making and does not replace professional incident response, legal advice, or an authorized technical assessment.",
                labelPaint,
                15f,
            )

            finishPage()
        }

        private fun section(title: String) {
            ensureSpace(58f)
            cursorY += 18f
            page?.canvas?.drawRect(margin, cursorY - 10f, margin + 4f, cursorY + 8f, accentPaint)
            page?.canvas?.drawText(title, margin + 12f, cursorY + 5f, headingPaint)
            cursorY += 24f
        }

        private fun keyValue(label: String, value: String) {
            ensureSpace(19f)
            page?.canvas?.drawText(label.uppercase(), margin, cursorY, labelPaint)
            page?.canvas?.drawText(value, margin + 128f, cursorY, bodyPaint)
            cursorY += 18f
        }

        private fun drawWrapped(
            text: String,
            paint: Paint = bodyPaint,
            lineHeight: Float = 17f,
        ) {
            val words = text.replace("\n", " \n ").split(" ")
            var line = ""
            words.forEach { word ->
                if (word == "\n") {
                    drawLine(line, paint, lineHeight)
                    line = ""
                    return@forEach
                }
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) <= contentWidth) {
                    line = candidate
                } else {
                    if (line.isNotEmpty()) drawLine(line, paint, lineHeight)
                    line = word
                }
            }
            if (line.isNotEmpty()) drawLine(line, paint, lineHeight)
        }

        private fun drawLine(text: String, paint: Paint, lineHeight: Float) {
            ensureSpace(lineHeight + 3f)
            page?.canvas?.drawText(text, margin, cursorY, paint)
            cursorY += lineHeight
        }

        private fun ensureSpace(required: Float) {
            if (cursorY + required > pageHeight - margin) {
                finishPage()
                newPage()
            }
        }

        private fun newPage() {
            pageNumber += 1
            page = document.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create(),
            )
            cursorY = margin
            page?.canvas?.drawText("JARVIS  /  DEFENSIVE SECURITY", margin, 24f, labelPaint)
            page?.canvas?.drawText("PAGE $pageNumber", pageWidth - margin - 40f, 24f, labelPaint)
        }

        private fun finishPage() {
            page?.let(document::finishPage)
            page = null
        }

        private fun textPaint(size: Float, colorValue: Int, bold: Boolean) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                color = colorValue
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
    }
}
