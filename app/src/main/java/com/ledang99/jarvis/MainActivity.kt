package com.ledang99.jarvis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledang99.jarvis.data.DemoAssessmentEngine
import com.ledang99.jarvis.domain.AssessmentReport
import com.ledang99.jarvis.domain.CaseInput
import com.ledang99.jarvis.domain.CaseType
import com.ledang99.jarvis.domain.Finding
import com.ledang99.jarvis.domain.Remediation
import com.ledang99.jarvis.domain.RiskLevel
import com.ledang99.jarvis.report.PdfReportExporter
import com.ledang99.jarvis.ui.theme.Amber400
import com.ledang99.jarvis.ui.theme.Blue400
import com.ledang99.jarvis.ui.theme.JarvisTheme
import com.ledang99.jarvis.ui.theme.Navy800
import com.ledang99.jarvis.ui.theme.Navy900
import com.ledang99.jarvis.ui.theme.Navy950
import com.ledang99.jarvis.ui.theme.Red400
import com.ledang99.jarvis.ui.theme.Slate300
import com.ledang99.jarvis.ui.theme.Slate400
import com.ledang99.jarvis.ui.theme.Teal300
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JarvisTheme {
                JarvisApp()
            }
        }
    }
}

private enum class AppScreen {
    HOME,
    NEW_CASE,
    REPORT,
}

@Composable
private fun JarvisApp() {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val engine = remember { DemoAssessmentEngine() }
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var selectedType by remember { mutableStateOf(CaseType.INCIDENT) }
    var report by remember { mutableStateOf<AssessmentReport?>(null) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null) {
            report?.let { currentReport ->
                PdfReportExporter.export(context, uri, currentReport)
                    .onSuccess {
                        scope.launch { snackbar.showSnackbar("PDF report saved") }
                    }
                    .onFailure { error ->
                        scope.launch {
                            snackbar.showSnackbar(error.message ?: "Unable to save PDF")
                        }
                    }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Navy950,
    ) { outerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(outerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Navy950, Color(0xFF0A1627), Navy950),
                    ),
                ),
        ) {
            when (screen) {
                AppScreen.HOME -> HomeScreen(
                    onStart = { type ->
                        selectedType = type
                        screen = AppScreen.NEW_CASE
                    },
                )

                AppScreen.NEW_CASE -> NewCaseScreen(
                    initialType = selectedType,
                    onBack = { screen = AppScreen.HOME },
                    onAnalyze = { input ->
                        report = engine.analyze(input)
                        screen = AppScreen.REPORT
                    },
                )

                AppScreen.REPORT -> report?.let { currentReport ->
                    ReportScreen(
                        report = currentReport,
                        onBack = { screen = AppScreen.NEW_CASE },
                        onNewCase = {
                            report = null
                            screen = AppScreen.HOME
                        },
                        onExport = {
                            val safeName = currentReport.case.title
                                .lowercase()
                                .replace(Regex("[^a-z0-9]+"), "-")
                                .trim('-')
                                .ifBlank { "assessment" }
                            pdfLauncher.launch("jarvis-$safeName.pdf")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onStart: (CaseType) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Teal300),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = null,
                        tint = Navy950,
                    )
                }
                Column {
                    Text(
                        "JARVIS",
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        "Defensive security assistant",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate400,
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text("LOCAL DEMO MODE") },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Science,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = Teal300,
                        leadingIconContentColor = Teal300,
                    ),
                )
                Text(
                    "Turn uncertainty into an actionable security plan.",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    "Describe a concern. Jarvis will structure the risk, prioritize defensive actions, and prepare a report you can share.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Slate300,
                )
            }
        }

        item {
            Button(
                onClick = { onStart(CaseType.INCIDENT) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start an assessment")
            }
        }

        item {
            Text(
                "QUICK START",
                style = MaterialTheme.typography.labelLarge,
                color = Slate400,
                letterSpacing = 1.4.sp,
            )
        }

        items(CaseType.entries) { type ->
            CaseTypeCard(type = type, onClick = { onStart(type) })
        }

        item {
            SafetyCard()
        }
    }
}

@Composable
private fun CaseTypeCard(type: CaseType, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Navy900),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Navy800),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (type) {
                        CaseType.VULNERABILITY -> Icons.Rounded.Security
                        CaseType.INCIDENT -> Icons.Rounded.WarningAmber
                        CaseType.PHISHING -> Icons.Rounded.Lock
                        CaseType.HARDENING -> Icons.Rounded.VerifiedUser
                    },
                    contentDescription = null,
                    tint = Teal300,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(type.label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(
                    type.prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate400,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Slate400,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun NewCaseScreen(
    initialType: CaseType,
    onBack: () -> Unit,
    onAnalyze: (CaseInput) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var environment by remember { mutableStateOf("") }
    var selectedType by remember(initialType) { mutableStateOf(initialType) }
    val ready = title.isNotBlank() && description.isNotBlank()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("New assessment") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    onAnalyze(
                        CaseInput(
                            title = title.trim(),
                            type = selectedType,
                            description = description.trim(),
                            environment = environment.trim(),
                        ),
                    )
                },
                enabled = ready,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.Security, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Generate assessment")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Give Jarvis the facts you have.",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        "Avoid passwords, private keys, and unnecessary personal data.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate400,
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FieldLabel("Assessment type")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CaseType.entries.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type.label) },
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Case title") },
                    placeholder = { Text("Example: Suspicious sign-in alerts") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
            }

            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("What happened?") },
                    placeholder = {
                        Text("Include observed behavior, dates, affected assets, and actions already taken.")
                    },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
            }

            item {
                OutlinedTextField(
                    value = environment,
                    onValueChange = { environment = it },
                    label = { Text("Environment (optional)") },
                    placeholder = { Text("Example: Android fleet, Microsoft 365") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Navy900),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Science,
                            contentDescription = null,
                            tint = Blue400,
                        )
                        Text(
                            "This first APK uses a transparent local rules engine. Live, cited research will be connected through the secure backend in the next phase.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate300,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportScreen(
    report: AssessmentReport,
    onBack: () -> Unit,
    onNewCase: () -> Unit,
    onExport: () -> Unit,
) {
    val completed = remember(report) { mutableStateListOf<Boolean>().apply {
        repeat(report.remediations.size) { add(false) }
    } }
    val uriHandler = LocalUriHandler.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            report.case.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Preliminary assessment",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate400,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onExport) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Export PDF")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                RiskOverview(report)
            }

            item {
                ReportSection(
                    eyebrow = "EXECUTIVE SUMMARY",
                    title = "What needs attention",
                ) {
                    Text(
                        report.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Slate300,
                    )
                }
            }

            item {
                ReportSection(
                    eyebrow = "FINDINGS",
                    title = "${report.findings.size} observations",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        report.findings.forEach { finding ->
                            FindingRow(finding)
                        }
                    }
                }
            }

            item {
                ReportSection(
                    eyebrow = "REMEDIATION PLAN",
                    title = "Work in this order",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        report.remediations.forEachIndexed { index, remediation ->
                            RemediationRow(
                                index = index,
                                remediation = remediation,
                                checked = completed[index],
                                onChecked = { completed[index] = it },
                            )
                        }
                    }
                }
            }

            item {
                ReportSection(
                    eyebrow = "REFERENCE STARTING POINTS",
                    title = "Verify before acting",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        report.references.forEach { reference ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { uriHandler.openUri(reference.url) }
                                    .background(Navy800)
                                    .padding(14.dp),
                            ) {
                                Text(
                                    reference.publisher,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Teal300,
                                )
                                Text(
                                    reference.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF201D16)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = Amber400,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Know the limits",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                report.limitations,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate300,
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = onExport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Export PDF report")
                }
            }

            item {
                OutlinedButton(
                    onClick = onNewCase,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Start another assessment")
                }
            }
        }
    }
}

@Composable
private fun RiskOverview(report: AssessmentReport) {
    val riskColor = riskColor(report.risk)
    Card(
        colors = CardDefaults.cardColors(containerColor = Navy900),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "PRELIMINARY RISK",
                        style = MaterialTheme.typography.labelLarge,
                        color = Slate400,
                        letterSpacing = 1.2.sp,
                    )
                    Text(
                        report.risk.label,
                        style = MaterialTheme.typography.headlineLarge,
                        color = riskColor,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(riskColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = null,
                        tint = riskColor,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            HorizontalDivider(color = Navy800)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatusItem("Mode", "Local demo")
                StatusItem("Confidence", report.confidence)
                StatusItem("Type", report.case.type.label)
            }
        }
    }
}

@Composable
private fun StatusItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Slate400)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ReportSection(
    eyebrow: String,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(
                eyebrow,
                style = MaterialTheme.typography.labelLarge,
                color = Teal300,
                letterSpacing = 1.2.sp,
            )
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
        content()
    }
}

@Composable
private fun FindingRow(finding: Finding) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Navy900)
            .padding(15.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(riskColor(finding.severity)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(finding.title, style = MaterialTheme.typography.titleMedium)
            Text(
                finding.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Slate400,
            )
        }
    }
}

@Composable
private fun RemediationRow(
    index: Int,
    remediation: Remediation,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Navy900),
        shape = RoundedCornerShape(15.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = checked, onCheckedChange = onChecked)
            Column(
                modifier = Modifier.padding(top = 10.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    "${index + 1}. ${remediation.title}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    remediation.action,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate300,
                )
                Text(
                    "Validate: ${remediation.validation}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Teal300,
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = Slate400,
        letterSpacing = 1.1.sp,
    )
}

@Composable
private fun SafetyCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10221F)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = Teal300,
            )
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Human approval stays in control", fontWeight = FontWeight.SemiBold)
                Text(
                    "Jarvis recommends defensive actions but does not scan systems or execute remediation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate300,
                )
            }
        }
    }
}

private fun riskColor(risk: RiskLevel): Color = when (risk) {
    RiskLevel.LOW -> Teal300
    RiskLevel.MODERATE -> Blue400
    RiskLevel.HIGH -> Amber400
    RiskLevel.CRITICAL -> Red400
}
