# Style review

Every PR is reviewed by a Claude agent for style and consistency. It infers the conventions from the
repo. Each finding is filed under a category, and categories are kept in a database so that the ones
caught over and over can be fixed at the source rather than in review.

Ideally consistent infractions can turn into lint rules or other deterministic processes that have
no tie to a LLM.

Correctness, design, and security are out of scope for this reviewer. So are docs of any kind, 
tickets, ADRs, agent config, and generated or lock files (`EXCLUDED_PATHS` in `agent.py`).

## Pieces

| Piece | Role |
|---|---|
| [`.claude/agents/style-reviewer.md`](../.claude/agents/style-reviewer.md) | The reviewer. Read-only, and says nothing about output format, so it also works conversationally. |
| [`findings.schema.json`](../scripts/style-review/findings.schema.json) | The output contract. The scripts pass it to `claude --json-schema`, which makes the reviewer return validated JSON: findings ordered by importance, any new categories, and notes. |
| Category database | Firestore database `style-review` in the Firebase project. Each category has an id, a general description of the issue, its source (the doc or skill stating the convention, or `codebase`), and whether it's mechanical enough to lint. Every finding is stored under its category as an occurrence, with the flagged code. See [`store.py`](../scripts/style-review/store.py). |
| [`.github/workflows/style-review.yml`](../.github/workflows/style-review.yml) + [`ci.py`](../scripts/style-review/ci.py) | Reviews each PR on open and on every push. Comments inline on the 5 most important findings (only ones not already on the PR), keeps one summary comment listing every finding, and records every finding in the category database. |
| [`scripts/style-review/report.py`](../scripts/style-review/report.py) | Ranks the categories and suggests a fix for each recurring one, with examples of the flagged code. |
| [`evals/style-reviewer/`](../evals/style-reviewer/) + [`eval.py`](../scripts/style-review/eval.py) | Scores the reviewer's recall and false positives against checked-in cases. Run locally; not run in CI. |

## How it works

### On every PR

1. **A PR changes code.** Each time a PR is opened or pushed to, a review starts.
2. **The reviewer finds problems.** A Claude agent compares the changed code with how the repo says things should be done (STYLE.md, skills) and how its existing code does them. Every place the change departs from those is a problem.
3. **Each problem is filed under a category.** Along with the change, the reviewer gets the list of categories past reviews have filed problems under: named kinds of mistake, like "missing KDoc". It files each problem it found under the matching one, so the same mistake is counted together across PRs. When no category fits, the reviewer creates one: a name, a description general enough to recur, and where the convention comes from. It's added to the list, so later reviews file the same mistake under it.
4. **The author hears about it.** The 5 most important problems become inline comments; a summary comment lists them all.
5. **The problem is remembered.** Every problem is saved under its category, so over time the database shows which mistakes keep happening.
### Follow-up

Review comments fix one PR. The point of categorizing and collecting every problem is to find the mistakes that keep happening and stop them for good, so each one is caught by documentation or tooling instead of by an LLM, PR after PR.

1. **Recurring mistakes get fixed at the source.** A report ranks the categories. For the ones that keep coming back, the fix isn't more review comments: write down a rule that was never written, clarify one that keeps being misread, or replace the review with a lint rule.
2. **The reviewer has less to catch.** Once a source fix lands, its category stops recurring, and mechanical ones leave the LLM's hands entirely.

## In detail

### 1. Trigger

[`style-review.yml`](../.github/workflows/style-review.yml) runs on `opened`, `reopened`, `synchronize`, and `ready_for_review`, for non-draft PRs from this repo (fork PRs get no OIDC token). A newer push cancels the PR's in-progress run. The job checks out the head commit, installs Claude Code and the Python requirements, authenticates to Google Cloud with `google-github-actions/auth`, and runs [`ci.py`](../scripts/style-review/ci.py).

### 2. Review

`ci.py` `main` loads the known categories (`store.load_categories`) and calls `agent.run_review`, which runs:

- `claude -p` with `--agent style-reviewer`, the read-only `ALLOWED_TOOLS`, and `--json-schema` [`findings.schema.json`](../scripts/style-review/findings.schema.json).
- A prompt naming the diff to review, `diff_command("BASE...HEAD")`, which leaves out `EXCLUDED_PATHS`, followed by the known categories as `id: description` lines.
- A fresh GitHub OIDC token for each `claude` process (`identity_token_env`), exchanged for Claude API access.

The agent's prompt doesn't name what to read. It starts with CLAUDE.md in context, which points to STYLE.md, and has read-only tools to look through docs, skills, and existing code; what it actually opens is up to the model. It returns `findings`, `newCategories`, and `notes`. The CLI validates the shape against the schema before `parse_output` returns it; the order of `findings` (most important first) is only requested by the prompt, not enforced.

### 3. Categorize

Every finding carries a `category` id, which is either a known category or one the same review defines in `newCategories`: a kebab-case `id`, a general `description`, a `source` (the doc or skill path stating the convention, or `codebase`), and `mechanical` (whether a linter could catch it). `agent.categories_by_id` merges known and new categories into one lookup; a finding whose category was never defined gets a stand-in with source `unknown` rather than failing the run.

### 4. Comment

- `post_findings` takes the first `MAX_COMMENTS` (5) findings. Each gets a `finding_key`: a hash of category, file, the flagged line's text, and how many identical lines precede it. Line numbers aren't used, because they shift between pushes.
- The key is embedded in the comment as a hidden marker. Findings whose key is already on the PR are skipped, so re-reviews after a push don't repeat comments.
- New comments are posted as one GitHub review. If GitHub rejects it (a line outside the diff), each comment is retried alone, and the ones that fail are listed as unanchored.
- `upsert_summary` creates or edits a single bot comment: a count per category with its source, findings past the first 5, unanchored findings, and reviewer notes.

### 5. Record

`record_run` builds one occurrence per finding (all of them, not just the 5 commented) and `store.record` writes them to Firestore database `style-review`:

| Document | Contents | Written |
|---|---|---|
| `categories/{id}` | `description`, `source`, `mechanical`, `firstSeen`, `firstPr`, `lastSeen` | Created on first use and never overwritten, so a definition stays stable; `lastSeen` updated each use. |
| `categories/{id}/occurrences/{pr}-{finding_key}` | `pr`, `head`, `file`, `line`, `endLine`, `message`, `code` (the flagged lines), `reviewedAt` | Overwritten on re-review, so a finding counts once per PR however many pushes it survives. |
| `prs/{number}` | `branch`, `lastReviewedAt`, `notes` | Merged; notes appended. |

Counts are never stored; `report.py` computes them from occurrences, so concurrent runs can't make them drift.

### 6. Report

Run by hand: `python3 scripts/style-review/report.py --days 90 --threshold 3 --examples 3` (the defaults). For each category it counts occurrences with `reviewedAt` inside `--days`, and the distinct PRs they came from, then ranks by PRs, then occurrences. A category caught in `--threshold` or more PRs is recurring, and is printed with its description, source, and the `--examples` most recent flagged snippets, which is what a fix is written from. It also prints every category's counts and all reviewer notes in the window.

### 7. Remediate

`suggested_fix` picks one fix per recurring category, checked in this order:

- **Document:** the source is `codebase`, so the convention only exists in code; write it down in STYLE.md or a skill.
- **Lint:** the issue is mechanical, so add a ktlint/detekt rule.
- **Fix skill:** the source is a skill that isn't steering code toward its own convention.
- **Clarify:** the source is a doc that's still being broken; make it clearer or add an example.

The suggestion is a heuristic, not a verdict. Reviewer notes that keep recurring on the same convention also point at a doc or skill to fix. File each accepted fix as a ticket. When a convention changes or is retired, add or update an eval case so the reviewer is scored against the new behavior. Category definitions can be edited or merged by hand in the Firebase console.

## Changing the reviewer

- **Changing STYLE.md, skills, the agent prompt, the evals, or these scripts:** run `eval.py` before and after, and mention the results in the PR. Nothing runs them in CI.
- **Adding an eval case:** create `evals/style-reviewer/cases/<name>/` with these parts:
  - `files/`: laid over the repo at the same paths.
  - `expected.json`: `expected` findings the reviewer must report, and `allowed` judgment calls it may report. Each entry is `{what, file, lines: [first, last]}`, plus `source` to also require the convention's source. Categories aren't matched, since the reviewer names them freely; `what` only labels the entry.

## Setup

Both credentials use workload identity federation: each run trades GitHub's short-lived OIDC token for short-lived tokens, so the repo stores no keys.

### Claude API

`scripts/style-review/agent.py` fetches a fresh OIDC token for every `claude` process, because each token can be exchanged only once.

Configured in the Claude Console under **Workload identity**:

- **Issuer:**
  - `iss` and discovery base URL: `https://token.actions.githubusercontent.com`
  - JWKS source: discovery
  - CA certificate: none
- **Federation rule:** scope `workspace:inference` in the `CREAM` workspace, subject `repo:lukekivi/C.R.E.A.M.:pull_request`, with claims `repository_owner: lukekivi`, `actor: lukekivi`, and `workflow: Style review`. The repo is public, so the `actor` claim keeps PRs triggered by anyone else from getting a token.

Repository variables: `ANTHROPIC_ORGANIZATION_ID`, `ANTHROPIC_SERVICE_ACCOUNT_ID`, and `ANTHROPIC_FEDERATION_RULE_ID`, plus `ANTHROPIC_WORKSPACE_ID` when the rule covers more than one workspace.

### Google Cloud (category database)

In the `cream-5eeda` project:

1. **Database:** in the Google Cloud console (the Firebase console names a project's first database `(default)`), create Firestore database `style-review`, Native mode, in `nam5`, with production rules, so client access is denied. CI writes through the server SDK, which security rules don't apply to.
2. **Service account:** IAM → create `style-review-ci` and grant it `Cloud Datastore User` (`roles/datastore.user`).
3. **APIs:** enable the IAM Service Account Credentials API (`iamcredentials.googleapis.com`) and the Security Token Service API (`sts.googleapis.com`).
4. **Workload identity pool:** IAM → Workload Identity Federation → create pool `github` with an OIDC provider `cream`:
   - Issuer: `https://token.actions.githubusercontent.com`, default audience
   - Attribute mapping: `google.subject=assertion.sub`, `attribute.actor=assertion.actor`, `attribute.workflow=assertion.workflow`
   - Attribute condition: `assertion.repository == 'lukekivi/C.R.E.A.M.' && assertion.actor == 'lukekivi' && assertion.workflow == 'Style review'`
5. **Binding:** on the pool, **Grant access** → service account impersonation → `style-review-ci`, for identities whose `subject` is `repo:lukekivi/C.R.E.A.M.:pull_request`.

Repository variables: `GCP_WORKLOAD_IDENTITY_PROVIDER` (`projects/71299485395/locations/global/workloadIdentityPools/github/providers/cream`; the provider's default audience minus `https://iam.googleapis.com/`) and `GCP_SERVICE_ACCOUNT` (`style-review-ci@cream-5eeda.iam.gserviceaccount.com`).

Repository variables live under Settings → Secrets and variables → Actions → Variables. They're identifiers, not secrets.

Locally, `eval.py` uses your normal Claude Code login, and `report.py` uses your `gcloud` application-default credentials.

## Running locally

```sh
pip install -r scripts/style-review/requirements.txt
gcloud auth application-default login                 # for report.py

python3 scripts/style-review/eval.py                  # all cases, 3 runs each
python3 scripts/style-review/report.py                # rank the categories
```

To review a branch before pushing, ask Claude Code: `@agent-style-reviewer review this branch`.
