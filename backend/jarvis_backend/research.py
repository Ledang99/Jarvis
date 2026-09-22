import asyncio
import os
import re
from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime
from email.utils import parsedate_to_datetime
from urllib.parse import urljoin, urlparse

import httpx

from .models import (
    CaseRequest,
    CaseType,
    Finding,
    Reference,
    RiskLevel,
    VerificationStatus,
)

ALLOWED_RESEARCH_HOSTS = frozenset(
    {
        "cisa.gov",
        "www.cisa.gov",
        "cisecurity.org",
        "www.cisecurity.org",
        "csrc.nist.gov",
        "nvd.nist.gov",
        "services.nvd.nist.gov",
        "www.nist.gov",
    }
)
CVE_PATTERN = re.compile(r"\bCVE-\d{4}-\d{4,7}\b", re.IGNORECASE)
MAX_REDIRECTS = 3
MAX_JSON_BYTES = 10 * 1024 * 1024


@dataclass(frozen=True)
class ResearchOutcome:
    findings: list[Finding]
    references: list[Reference]
    note: str
    risk_floor: RiskLevel | None = None
    verified_count: int = 0
    queried_count: int = 0


class AuthoritativeResearchService:
    """Queries only fixed, allowlisted defensive-security sources."""

    def __init__(
        self,
        *,
        transport: httpx.AsyncBaseTransport | None = None,
        now: Callable[[], datetime] | None = None,
    ) -> None:
        self._transport = transport
        self._now = now or (lambda: datetime.now(UTC))

    async def research(
        self,
        case: CaseRequest,
        references: list[Reference],
    ) -> ResearchOutcome:
        headers = {
            "Accept": "application/json, text/html;q=0.8, */*;q=0.5",
            "User-Agent": "Jarvis-Defensive-Research/0.3",
        }
        nvd_api_key = os.getenv("NVD_API_KEY")
        if nvd_api_key:
            headers["apiKey"] = nvd_api_key

        timeout = httpx.Timeout(10.0, connect=4.0)
        async with httpx.AsyncClient(
            timeout=timeout,
            headers=headers,
            transport=self._transport,
            follow_redirects=False,
        ) as client:
            verified_references_task = asyncio.gather(
                *(self._verify_reference(client, reference) for reference in references)
            )
            cve_ids = self._extract_cves(case)
            intelligence_task = self._query_intelligence(client, case, cve_ids)
            verified_references, intelligence = await asyncio.gather(
                verified_references_task,
                intelligence_task,
            )

        live_references = [*verified_references, *intelligence.references]
        deduplicated = list({reference.url: reference for reference in live_references}.values())
        verified_count = sum(
            reference.verification_status is VerificationStatus.VERIFIED
            for reference in deduplicated
        )
        note = (
            f"Queried approved authoritative sources; verified "
            f"{verified_count} of {len(deduplicated)} returned references."
        )
        return ResearchOutcome(
            findings=intelligence.findings,
            references=deduplicated,
            note=note,
            risk_floor=intelligence.risk_floor,
            verified_count=verified_count,
            queried_count=len(deduplicated),
        )

    async def _verify_reference(
        self,
        client: httpx.AsyncClient,
        reference: Reference,
    ) -> Reference:
        retrieved_at = self._now()
        try:
            response = await self._request(client, "HEAD", reference.url)
            if response.status_code in (403, 405) or response.status_code >= 500:
                response = await self._request(
                    client,
                    "GET",
                    reference.url,
                    headers={"Range": "bytes=0-4095"},
                )
            verified = 200 <= response.status_code < 400
            published_at = self._header_date(
                response.headers.get("last-modified"),
            )
            return reference.model_copy(
                update={
                    "verification_status": (
                        VerificationStatus.VERIFIED
                        if verified
                        else VerificationStatus.UNAVAILABLE
                    ),
                    "retrieved_at": retrieved_at,
                    "published_at": published_at,
                    "evidence": (
                        f"Allowlisted HTTPS source responded with "
                        f"HTTP {response.status_code}."
                    ),
                }
            )
        except (httpx.HTTPError, ValueError) as error:
            return reference.model_copy(
                update={
                    "verification_status": VerificationStatus.UNAVAILABLE,
                    "retrieved_at": retrieved_at,
                    "evidence": f"Verification failed: {type(error).__name__}.",
                }
            )

    async def _query_intelligence(
        self,
        client: httpx.AsyncClient,
        case: CaseRequest,
        cve_ids: list[str],
    ) -> ResearchOutcome:
        if cve_ids:
            nvd_tasks = [
                self._query_nvd(client, {"cveId": cve_id})
                for cve_id in cve_ids[:3]
            ]
            kev_task = self._query_cisa_kev(client, set(cve_ids[:3]))
            results = await asyncio.gather(*nvd_tasks, kev_task)
        elif case.type is CaseType.VULNERABILITY:
            keyword = self._keyword_query(case)
            results = [
                await self._query_nvd(
                    client,
                    {
                        "keywordSearch": keyword,
                        "resultsPerPage": "3",
                    },
                )
            ]
        else:
            return ResearchOutcome(findings=[], references=[], note="")

        findings: list[Finding] = []
        references: list[Reference] = []
        risk_floor: RiskLevel | None = None
        for result in results:
            findings.extend(result.findings)
            references.extend(result.references)
            risk_floor = self._higher_risk(risk_floor, result.risk_floor)
        return ResearchOutcome(
            findings=findings,
            references=references,
            note="",
            risk_floor=risk_floor,
        )

    async def _query_nvd(
        self,
        client: httpx.AsyncClient,
        params: dict[str, str],
    ) -> ResearchOutcome:
        retrieved_at = self._now()
        url = "https://services.nvd.nist.gov/rest/json/cves/2.0"
        try:
            response = await self._request(client, "GET", url, params=params)
            response.raise_for_status()
            self._check_json_size(response)
            payload = response.json()
        except (httpx.HTTPError, ValueError):
            return ResearchOutcome(findings=[], references=[], note="")

        findings: list[Finding] = []
        references: list[Reference] = []
        risk_floor: RiskLevel | None = None
        for item in payload.get("vulnerabilities", [])[:3]:
            cve = item.get("cve", {})
            cve_id = cve.get("id")
            if not cve_id or not CVE_PATTERN.fullmatch(cve_id):
                continue
            description = self._english_description(cve.get("descriptions", []))
            score, severity = self._cvss(cve.get("metrics", {}))
            risk = self._risk_from_cvss(score)
            risk_floor = self._higher_risk(risk_floor, risk)
            score_text = f"CVSS {score:.1f} {severity}".strip() if score else "CVSS unavailable"
            evidence = f"{score_text}. {description}".strip()[:700]

            findings.append(
                Finding(
                    title=f"NVD record verified: {cve_id}",
                    detail=evidence,
                    severity=risk,
                )
            )
            references.append(
                Reference(
                    publisher="NVD",
                    title=f"{cve_id} vulnerability record",
                    url=f"https://nvd.nist.gov/vuln/detail/{cve_id}",
                    verification_status=VerificationStatus.VERIFIED,
                    retrieved_at=retrieved_at,
                    published_at=self._iso_date(cve.get("published")),
                    evidence=evidence,
                )
            )

        return ResearchOutcome(
            findings=findings,
            references=references,
            note="",
            risk_floor=risk_floor,
        )

    async def _query_cisa_kev(
        self,
        client: httpx.AsyncClient,
        cve_ids: set[str],
    ) -> ResearchOutcome:
        retrieved_at = self._now()
        url = (
            "https://www.cisa.gov/sites/default/files/feeds/"
            "known_exploited_vulnerabilities.json"
        )
        try:
            response = await self._request(client, "GET", url)
            response.raise_for_status()
            self._check_json_size(response)
            payload = response.json()
        except (httpx.HTTPError, ValueError):
            return ResearchOutcome(findings=[], references=[], note="")

        findings: list[Finding] = []
        references: list[Reference] = []
        risk_floor: RiskLevel | None = None
        for record in payload.get("vulnerabilities", []):
            cve_id = str(record.get("cveID", "")).upper()
            if cve_id not in cve_ids:
                continue
            ransomware = record.get("knownRansomwareCampaignUse") == "Known"
            risk = RiskLevel.CRITICAL if ransomware else RiskLevel.HIGH
            risk_floor = self._higher_risk(risk_floor, risk)
            due_date = record.get("dueDate", "not specified")
            evidence = (
                f"CISA lists {cve_id} as known exploited; remediation due date "
                f"{due_date}; known ransomware use: "
                f"{record.get('knownRansomwareCampaignUse', 'Unknown')}."
            )
            findings.append(
                Finding(
                    title=f"CISA KEV match: {cve_id}",
                    detail=evidence,
                    severity=risk,
                )
            )
            references.append(
                Reference(
                    publisher="CISA",
                    title=f"Known Exploited Vulnerability: {cve_id}",
                    url="https://www.cisa.gov/known-exploited-vulnerabilities-catalog",
                    verification_status=VerificationStatus.VERIFIED,
                    retrieved_at=retrieved_at,
                    published_at=self._iso_date(record.get("dateAdded")),
                    evidence=evidence,
                )
            )

        return ResearchOutcome(
            findings=findings,
            references=references,
            note="",
            risk_floor=risk_floor,
        )

    async def _request(
        self,
        client: httpx.AsyncClient,
        method: str,
        url: str,
        *,
        params: dict[str, str] | None = None,
        headers: dict[str, str] | None = None,
    ) -> httpx.Response:
        current_url = url
        current_params = params
        for _ in range(MAX_REDIRECTS + 1):
            self._validate_url(current_url)
            response = await client.request(
                method,
                current_url,
                params=current_params,
                headers=headers,
            )
            current_params = None
            if response.status_code not in (301, 302, 303, 307, 308):
                return response
            location = response.headers.get("location")
            if not location:
                raise ValueError("Redirect response did not include a location.")
            current_url = urljoin(str(response.url), location)
        raise ValueError("Too many source redirects.")

    @staticmethod
    def _validate_url(url: str) -> None:
        parsed = urlparse(url)
        if (
            parsed.scheme != "https"
            or parsed.hostname not in ALLOWED_RESEARCH_HOSTS
            or parsed.username
            or parsed.password
            or parsed.port not in (None, 443)
        ):
            raise ValueError("Source URL is not on the approved HTTPS allowlist.")

    @staticmethod
    def _extract_cves(case: CaseRequest) -> list[str]:
        content = f"{case.title} {case.description}"
        return list(dict.fromkeys(match.upper() for match in CVE_PATTERN.findall(content)))

    @staticmethod
    def _keyword_query(case: CaseRequest) -> str:
        words = re.findall(r"[A-Za-z0-9_.-]+", f"{case.title} {case.description}")
        return " ".join(words[:12])[:120]

    @staticmethod
    def _english_description(descriptions: list[dict]) -> str:
        for description in descriptions:
            if description.get("lang") == "en":
                return str(description.get("value", ""))
        return ""

    @staticmethod
    def _cvss(metrics: dict) -> tuple[float | None, str]:
        for key in (
            "cvssMetricV40",
            "cvssMetricV31",
            "cvssMetricV30",
            "cvssMetricV2",
        ):
            candidates = metrics.get(key) or []
            if not candidates:
                continue
            data = candidates[0].get("cvssData", {})
            score = data.get("baseScore")
            severity = data.get("baseSeverity") or candidates[0].get("baseSeverity", "")
            if isinstance(score, int | float):
                return float(score), str(severity)
        return None, ""

    @staticmethod
    def _risk_from_cvss(score: float | None) -> RiskLevel:
        if score is None:
            return RiskLevel.MODERATE
        if score >= 9.0:
            return RiskLevel.CRITICAL
        if score >= 7.0:
            return RiskLevel.HIGH
        if score >= 4.0:
            return RiskLevel.MODERATE
        return RiskLevel.LOW

    @staticmethod
    def _higher_risk(
        current: RiskLevel | None,
        candidate: RiskLevel | None,
    ) -> RiskLevel | None:
        if candidate is None:
            return current
        if current is None:
            return candidate
        order = {
            RiskLevel.LOW: 0,
            RiskLevel.MODERATE: 1,
            RiskLevel.HIGH: 2,
            RiskLevel.CRITICAL: 3,
        }
        return candidate if order[candidate] > order[current] else current

    @staticmethod
    def _header_date(value: str | None) -> datetime | None:
        if not value:
            return None
        try:
            return parsedate_to_datetime(value).astimezone(UTC)
        except (TypeError, ValueError):
            return None

    @staticmethod
    def _iso_date(value: str | None) -> datetime | None:
        if not value:
            return None
        try:
            parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
            if parsed.tzinfo is None:
                parsed = parsed.replace(tzinfo=UTC)
            return parsed.astimezone(UTC)
        except ValueError:
            return None

    @staticmethod
    def _check_json_size(response: httpx.Response) -> None:
        if len(response.content) > MAX_JSON_BYTES:
            raise ValueError("Source response exceeded the research size limit.")
