"""The run-file contract shared by ci.py (writer) and report.py (reader)."""

import re
from datetime import datetime, timezone

LEDGER_BRANCH = "style-review-ledger"
TIMESTAMP_FORMAT = "%Y-%m-%dT%H:%M:%SZ"


def now() -> str:
    return datetime.now(timezone.utc).strftime(TIMESTAMP_FORMAT)


def parse_time(timestamp: str) -> datetime:
    return datetime.strptime(timestamp, TIMESTAMP_FORMAT).replace(tzinfo=timezone.utc)


def run_path(branch: str, reviewed_at: str) -> str:
    slug = re.sub(r"[^A-Za-z0-9._-]+", "-", branch).strip("-")
    return f"runs/{slug}/{reviewed_at.replace(':', '')}.json"
