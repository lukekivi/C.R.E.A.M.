# Contributing

## Commits

Each commit adds only code it uses. Don't land a repository method, model, or helper in one commit for a later commit to call; ship it with its first caller. Split a commit only where each half stands on its own.

Review fixes amend the commit that introduced the issue (`git commit --fixup` + autosquash), not a new commit on top.

## Pull requests

Split work into PRs a reviewer can read in one sitting, and split by feature slice, not by layer. Each PR is a working increment: a screen it adds is reachable in the app, and everything it adds is used. A UI-only PR or a data-only PR that nothing calls yet doesn't count.
