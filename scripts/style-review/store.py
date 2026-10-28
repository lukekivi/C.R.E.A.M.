"""The style-review category database in Firestore, written by ci.py and read by report.py.

Database `style-review` in the Firebase project:
  categories/{id}                      description, source, mechanical, firstSeen, lastSeen
  categories/{id}/occurrences/{key}    pr, head, file, line, endLine, message, code, reviewedAt
  prs/{number}                         branch, lastReviewedAt, notes

An occurrence's key is derived from the PR and the flagged code, so re-reviewing a PR after a push
overwrites its occurrences instead of counting them again. Counts are computed from occurrences,
never stored, so concurrent runs can't make them drift.

Credentials are Application Default Credentials: google-github-actions/auth in CI,
`gcloud auth application-default login` locally. FIRESTORE_EMULATOR_HOST targets the emulator.
"""

import os
import sys

PROJECT = os.environ.get("GOOGLE_CLOUD_PROJECT", "cream-5eeda")
DATABASE = "style-review"


def client():
    from google.cloud import firestore

    return firestore.Client(project=PROJECT, database=DATABASE)


def load_categories(db) -> list[dict]:
    return [doc.to_dict() | {"id": doc.id} for doc in db.collection("categories").stream()]


def try_load_categories() -> list[dict]:
    """The known categories, or none (with a warning) when the database isn't reachable, e.g. locally without credentials."""
    try:
        return load_categories(client())
    except Exception as error:
        print(f"warning: reviewing without known categories ({type(error).__name__}: {error})", file=sys.stderr)
        return []


def record(db, run: dict, categories: dict[str, dict], occurrences: dict[str, dict]) -> None:
    """Records one review: creates categories seen for the first time and upserts each occurrence by key."""
    from google.api_core.exceptions import AlreadyExists
    from google.cloud import firestore

    reviewed_at = run["reviewedAt"]
    used = {occurrence["category"] for occurrence in occurrences.values()}
    for category_id in used:
        category = categories[category_id]
        fields = {key: category[key] for key in ("description", "source", "mechanical")}
        try:
            db.collection("categories").document(category_id).create(fields | {"firstSeen": reviewed_at, "firstPr": run["pr"]})
        except AlreadyExists:
            pass

    batch = db.batch()
    for key, occurrence in occurrences.items():
        category_ref = db.collection("categories").document(occurrence["category"])
        fields = {name: value for name, value in occurrence.items() if name != "category"}
        batch.set(category_ref.collection("occurrences").document(key), fields | {"reviewedAt": reviewed_at})
    for category_id in used:
        batch.update(db.collection("categories").document(category_id), {"lastSeen": reviewed_at})
    pr_fields = {"branch": run["branch"], "lastReviewedAt": reviewed_at}
    if run["notes"]:
        pr_fields["notes"] = firestore.ArrayUnion(run["notes"])
    batch.set(db.collection("prs").document(str(run["pr"])), pr_fields, merge=True)
    batch.commit()
