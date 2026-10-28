"""Runs the style-reviewer agent headlessly and parses its findings."""

import json
import os
import shutil
import subprocess
import tempfile
import threading
import urllib.parse
import urllib.request
from contextlib import contextmanager
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
FEDERATION_AUDIENCE = "https://api.anthropic.com"
ALLOWED_TOOLS = "Read,Glob,Grep,Bash(git diff:*),Bash(git show:*),Bash(gh pr diff:*),Bash(grep:*)"
SCHEMA = (Path(__file__).parent / "findings.schema.json").read_text()
# Concurrent `git worktree` calls race on the repo's lock files.
WORKTREE_LOCK = threading.Lock()
REQUIRED_KEYS = tuple(json.loads(SCHEMA)["properties"]["findings"]["items"]["required"])


class ReviewError(Exception):
    """The reviewer failed to run or returned output that isn't a valid review."""


def sh(*args: str, cwd: Path = REPO_ROOT, input: str | None = None, env: dict | None = None, check: bool = True) -> subprocess.CompletedProcess:
    result = subprocess.run(args, cwd=cwd, input=input, env=env, capture_output=True, text=True)
    if check and result.returncode != 0:
        raise RuntimeError(f"{' '.join(args)} failed:\n{result.stderr}")
    return result


@contextmanager
def temp_worktree(prefix: str, ref: str = "HEAD"):
    """Yields a throwaway detached worktree of `ref`, removed afterwards even if setup fails."""
    path = Path(tempfile.mkdtemp(prefix=prefix))
    try:
        with WORKTREE_LOCK:
            sh("git", "worktree", "add", "--detach", str(path), ref)
        yield path
    finally:
        with WORKTREE_LOCK:
            sh("git", "worktree", "remove", "--force", str(path), check=False)
        shutil.rmtree(path, ignore_errors=True)


def run_review(
    diff: str,
    cwd: Path = REPO_ROOT,
    model: str | None = None,
    max_turns: int = 40,
) -> dict:
    """Reviews the output of the `diff` command (e.g. "git diff HEAD") and returns {"findings": [...], "notes": [...]}."""
    cmd = [
        "claude", "-p", f"Review the changes in `{diff}`.",
        "--agent", "style-reviewer",
        "--allowedTools", ALLOWED_TOOLS,
        "--max-turns", str(max_turns),
        "--json-schema", SCHEMA,
        "--output-format", "json",
    ]
    if model:
        cmd += ["--model", model]
    with identity_token_env() as env:
        result = subprocess.run(cmd, cwd=cwd, env=env, capture_output=True, text=True, stdin=subprocess.DEVNULL)
    if result.returncode != 0:
        raise ReviewError(f"claude exited {result.returncode}:\n{result.stderr or result.stdout}")
    return parse_output(result.stdout)


@contextmanager
def identity_token_env():
    """Yields the environment for one claude process.

    In GitHub Actions with workload identity federation configured, adds a freshly fetched
    OIDC token. Each token can be exchanged only once, so concurrent or successive claude
    processes can't share one. Elsewhere, yields the environment unchanged.
    """
    # Unset repository variables arrive as empty strings; drop them so they don't count as set.
    env = {key: value for key, value in os.environ.items() if value or not key.startswith("ANTHROPIC_")}
    request_url = env.get("ACTIONS_ID_TOKEN_REQUEST_URL")
    if not (request_url and env.get("ANTHROPIC_FEDERATION_RULE_ID")):
        yield env
        return
    request = urllib.request.Request(
        f"{request_url}&audience={urllib.parse.quote(FEDERATION_AUDIENCE, safe='')}",
        headers={"Authorization": f"Bearer {env['ACTIONS_ID_TOKEN_REQUEST_TOKEN']}"},
    )
    with urllib.request.urlopen(request) as response:
        token = json.load(response)["value"]
    fd, path = tempfile.mkstemp(prefix="gha-jwt-")
    try:
        with os.fdopen(fd, "w") as token_file:
            token_file.write(token)
        yield env | {"ANTHROPIC_IDENTITY_TOKEN_FILE": path}
    finally:
        os.remove(path)


def parse_output(text: str) -> dict:
    """Extracts the review from `claude --output-format json`; the CLI has validated it against SCHEMA."""
    try:
        review = json.loads(text).get("structured_output")
    except json.JSONDecodeError as error:
        raise ReviewError(f"claude output isn't JSON ({error}):\n{text}") from error
    if review is None:
        raise ReviewError(f"No structured output from the reviewer:\n{text}")
    return review


def span(finding: dict) -> tuple[int, int]:
    return finding["line"], finding.get("endLine") or finding["line"]


def line_range(finding: dict) -> str:
    start, end = span(finding)
    return f"{start}-{end}" if end != start else str(start)
