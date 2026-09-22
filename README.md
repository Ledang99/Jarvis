# Jarvis

Jarvis is a defensive cybersecurity assistant for Android. This first APK turns a
user-described security concern into a preliminary risk assessment, prioritized
remediation checklist, reference starting points, and an exportable PDF report.

## MVP capabilities

- Guided intake for incidents, vulnerabilities, phishing, and hardening
- FastAPI backend with an explicit assessment workflow
- Transparent local fallback that works without a backend connection
- Risk classification, findings, and ordered remediation steps
- Validation guidance for every remediation
- Official reference starting points from NIST, CISA, and CIS
- User-controlled remediation checklist
- PDF export through Android's secure document picker
- Explicit limitations and human-approval boundaries

The current backend structures and triages the supplied information. It does
**not** browse the web, inspect devices, scan networks, or execute remediation.
It preserves an auditable boundary for the cited research provider added next.

## Technology

- Kotlin
- Jetpack Compose and Material 3
- Android Gradle Plugin 9.4
- Minimum Android version: Android 8.0 (API 26)
- Target Android version: API 37
- Python 3.12, FastAPI, and Pydantic

## Run the backend

```bash
python3 -m venv backend/.venv
backend/.venv/bin/pip install -r backend/requirements-dev.txt
PYTHONPATH=backend backend/.venv/bin/python -m uvicorn \
  jarvis_backend.main:app --host 0.0.0.0 --port 8787
```

The Android emulator uses `http://10.0.2.2:8787` by default. For a deployed
HTTPS API, override the URL at build time:

```bash
./gradlew assembleDebug -PjarvisApiBaseUrl=https://api.example.com
```

Run backend tests:

```bash
PYTHONPATH=backend backend/.venv/bin/pytest backend/tests
```

## Build

Install JDK 17 or newer and Android SDK Platform 37, then set `ANDROID_HOME`.

```bash
./gradlew test
./gradlew assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For convenient download, the current test build is also tracked at:

```text
releases/Jarvis-v0.2.0-debug.apk
```

Install it on a connected device:

```bash
adb install -r releases/Jarvis-v0.2.0-debug.apk
```

## Project structure

```text
app/src/main/java/com/ledang99/jarvis/
├── MainActivity.kt              # Compose application and MVP screens
├── data/BackendAssessmentClient.kt
├── data/DemoAssessmentEngine.kt # Transparent local assessment logic
├── domain/Assessment.kt         # Case and report models
├── report/PdfReportExporter.kt  # On-device PDF generation
└── ui/theme/Theme.kt            # Jarvis visual system

backend/
├── jarvis_backend/main.py        # FastAPI routes
├── jarvis_backend/engine.py      # Auditable assessment workflow
├── jarvis_backend/models.py      # Validated API contract
└── tests/test_engine.py
```

## Next phase

The live research backend should use an explicit, auditable workflow:

1. Validate scope and authorization.
2. Search an allowlist of authoritative sources.
3. Extract evidence with source dates and citations.
4. Detect conflicting or stale claims.
5. Draft findings and remediation.
6. Require user review before report generation.

Automatic scanning and remediation should remain out of scope until strong
authorization, approval, audit, and rollback controls are in place.
