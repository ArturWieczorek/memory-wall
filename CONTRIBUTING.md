# Contributing to Memory Wall

Thanks for your interest. Memory Wall is a small, test-driven teaching project - a public
append-only message wall on Cardano. Please keep changes focused and in the existing style.

## Ground rules
- **Plain ASCII only** in all tracked files and commit messages (no smart quotes, em/en dashes,
  arrows, emoji). Use `-`, `'`, `"`, `->`, `...`.
- **Conventional commits**: `feat|fix|docs|chore|test|refactor(scope): summary`. No `Co-Authored-By`
  trailer.
- **Test-driven**: write or update tests with every code change; keep the build green.
- **Minimal dependencies**: adding one needs a clear reason in the PR description.
- **Testnet-first**: never hardcode a network, URL, or key - read them from config/env.
- **No secrets**: the Blockfrost key and any real keys are env-only, never committed.

## Local checks (must pass before a PR)
Backend (repo root):
```bash
./gradlew spotlessApply test        # format + unit tests (+ JaCoCo coverage report)
./gradlew integrationTest           # live-chain tests; self-skip without WALL_IT_BACKEND_URL
```
UI (`ui/`):
```bash
npm ci
npm run lint && npm run format:check && npm run typecheck && npm test && npm run build
```
CI (`.github/workflows/ci.yml`) runs the same on every push and pull request to `main`, alongside
CodeQL scanning.

## Project orientation
See **[AGENT.md](AGENT.md)** for architecture, decisions, config, and status, and
**[docs/BACKLOG.md](docs/BACKLOG.md)** for the roadmap (including what is deliberately out of scope).

## Reporting security issues
See **[SECURITY.md](SECURITY.md)** - please do not open a public issue for vulnerabilities.
