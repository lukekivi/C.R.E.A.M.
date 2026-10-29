# Style review

Every non-draft PR is reviewed by a Claude agent for style and consistency. It infers the conventions from the repo's docs (e.g. [STYLE.md](../STYLE.md), [CLAUDE.md](../CLAUDE.md)), relevant skills, and similar existing code. Its findings are recorded so that conventions caught over and over can be fixed at the source rather than in review.

Correctness, design, and security are out of scope for this reviewer.

## Pieces

| Piece | Role |
|---|---|
| [`.claude/agents/style-reviewer.md`](../.claude/agents/style-reviewer.md) | The reviewer. Read-only, and says nothing about output format, so it also works conversationally. |
| [`findings.schema.json`](../scripts/style-review/findings.schema.json) | The output contract. The scripts pass it to `claude --json-schema`, which makes the reviewer return validated JSON. Field descriptions carry the rules for line numbers, suggestions, and notes. |
| Finding categories | Every finding names a `convention` and its `source`: the doc or skill stating it, or `codebase` when only existing code shows it. Documented conventions use the source's label, e.g. a STYLE.md rule ID such as `[KDOC-MULTILINE]`. This is what makes findings countable. |
| [`scripts/style-review/review.py`](../scripts/style-review/review.py) | Reviews local changes. Nothing is posted or recorded. |
| [`.github/workflows/style-review.yml`](../.github/workflows/style-review.yml) + [`ci.py`](../scripts/style-review/ci.py) | Reviews each PR on open and on every push, skipping `evals/`. Posts inline comments (only ones not already on the PR) and keeps one summary comment up to date. Records the run on the `style-review-ledger` branch. |
| [`scripts/style-review/report.py`](../scripts/style-review/report.py) | Tallies the ledger and suggests a fix for each recurring convention. |
| [`evals/style-reviewer/`](../evals/style-reviewer/) + [`eval.py`](../scripts/style-review/eval.py) | Scores the reviewer's recall and false positives against checked-in cases. Run locally; not run in CI. |

## Running locally

```sh
python3 scripts/style-review/review.py                # this branch vs main
python3 scripts/style-review/review.py --uncommitted
python3 scripts/style-review/eval.py                  # all cases, 3 runs each
python3 scripts/style-review/report.py --fetch        # tally the ledger
```

Or ask Claude Code directly: `@agent-style-reviewer review this branch`.

## The feedback loop

1. **Record.** Each CI run commits `runs/<branch>/<timestamp>.json` to the `style-review-ledger` branch. The file holds the findings (convention, source, file, line, message), reviewer notes, and how many of the PR's commits have a Claude `Co-Authored-By` trailer. The ledger lives on its own branch so recording never adds commits to a PR branch, and each run gets its own file so concurrent PRs never conflict.
2. **Report.** `report.py` merges each PR's runs, so a finding that survives several pushes counts once. It then groups findings by convention and source and ranks the groups by how many PRs they were caught in. A group caught in `--threshold` or more PRs within `--days` is recurring.
3. **Remediate.** For each recurring group the report suggests one fix:
   - **Document:** the source is `codebase`, so write the convention down in STYLE.md or a skill.
   - **Lint:** the convention is mechanical, so add a ktlint/detekt rule.
   - **Skill:** it's mostly caught in Claude co-authored PRs, so fix the skill or CLAUDE.md guidance that Claude followed, or add one.
   - **Clarify:** it's mostly caught in human-written code, so make the source clearer or add an example.

   Reviewer notes flag conventions that conflicted or were unclear for a specific piece of code. Notes that keep recurring on the same convention point at docs or skills that need fixing.
4. **Act.** File each accepted fix as a ticket. When a convention is changed or retired, add or update an eval case so the reviewer is scored against the new behavior.

The suggested fix is a heuristic. The authorship signal is only as good as the commit trailers, so treat it as a starting point.

## Changing the reviewer

- **Adding a STYLE.md rule:** give it a new ID. IDs are permanent: retire them, never rename or reuse them, or the ledger history for that rule splits. Add an eval case or extend an existing one.
- **Changing STYLE.md, skills, the agent prompt, the evals, or these scripts:** run `eval.py` before and after, and mention the results in the PR. Nothing runs them in CI.
- **Adding an eval case:** create `evals/style-reviewer/cases/<name>/` with these parts:
  - `files/`: laid over the repo at the same paths.
  - `expected.json`: `expected` findings the reviewer must report, and `allowed` judgment calls it may report. Each entry is `{convention, file, lines: [first, last]}`. For conventions a skill or the code implies, match on `source` instead of `convention`, since the reviewer names those freely.

## Setup

The workflows authenticate to the Claude API with [workload identity federation](https://platform.claude.com/docs/en/manage-claude/wif-providers/github-actions): each run trades GitHub's short-lived OIDC token for a short-lived Anthropic token, so the repo stores no API key. `scripts/style-review/agent.py` fetches a fresh OIDC token for every `claude` process, because each token can be exchanged only once.

Configured in the Claude Console under **Workload identity**:

- **Issuer:**
  - `iss` and discovery base URL: `https://token.actions.githubusercontent.com`
  - JWKS source: discovery
  - CA certificate: none
- **Federation rule:** scope `workspace:inference` in the `CREAM` workspace, subject `repo:lukekivi/C.R.E.A.M.:pull_request`, with claims `repository_owner: lukekivi`, `actor: lukekivi`, and `workflow: Style review`. The repo is public, so the `actor` claim keeps PRs triggered by anyone else from getting a token.

Repository variables (Settings → Secrets and variables → Actions → Variables): `ANTHROPIC_ORGANIZATION_ID`, `ANTHROPIC_SERVICE_ACCOUNT_ID`, and `ANTHROPIC_FEDERATION_RULE_ID`, plus `ANTHROPIC_WORKSPACE_ID` when the rule covers more than one workspace. These are identifiers, not secrets.

Locally, `review.py` and `eval.py` use your normal Claude Code login.
