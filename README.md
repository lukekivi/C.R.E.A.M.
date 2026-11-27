# C.R.E.A.M.
A budget app, because cash rules everything around me.

## Contributing
See [STYLE.md](STYLE.md) for code conventions (documentation, formatting, module boundaries). All contributions follow it.

See [docs/work-tracking.md](docs/work-tracking.md) for how work is tracked (epics and tickets live in [docs/specs/](docs/specs/)).

Commit messages are a subject line plus a 3-line haiku body. Enable the hook that checks this once per clone: `git config core.hooksPath .githooks`.

## Architecture
Three layers — UI (`:app`), Data (`:data`), and Datasource (`:remote-datasource`, `:local-datasource`) — with manual dependency injection: no Hilt or Koin. `AppContainer` builds the repositories, and each ViewModel's companion factory pulls what it needs from it.

See [docs/architecture.md](docs/architecture.md) for modules, the dependency graph, and how dependencies are wired.
