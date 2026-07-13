# Chapter 09 - Keeping the repo healthy (CI + free GitHub security)

> Goal: put **automated safety nets** around the now-public repo so it stays healthy without you
> remembering to do anything - every change is built and tested, dependencies are watched for updates
> and known vulnerabilities, the code is scanned for security bugs, and secrets are blocked before
> they can be committed. Plus a LICENSE so people know how they may use it.
>
> Written for a beginner - each tool is introduced with a plain-language analogy. Everything here is
> **free on public repositories**.

---

## 1. Why automate this

So far you have run `./gradlew test` and `npm test` by hand, and you eyeballed the code for secrets.
That works until you forget once. On a public repo, "forget once" can mean a broken deploy or a
leaked key. The fix is to hire robots: small automated checks that run on every change and never get
tired.

*Analogy:* smoke detectors and door locks for the house you just opened to the public. You do not
want to stand guard - you want devices that watch continuously and shout when something is wrong.

We add five, all free on public repos.

## 2. CI - build and test every change (`.github/workflows/ci.yml`)

**Continuous Integration (CI)** runs your build and tests automatically on every push and pull
request. If something is red, you see it immediately - not after you deploy.

*Analogy:* a robot that repeats your pre-commit checklist perfectly, every single time.

Two jobs (the house style is one per language):
- **Backend (Java 21):** `./gradlew spotlessCheck test`.
- **UI (Next.js):** `npm ci` -> `typecheck` -> `test` -> `build`.

This is separate from `deploy-ui.yml`: CI **checks** every change; the deploy workflow **publishes**
the UI only when `ui/` changes. The green check next to a commit is CI saying "this still works".

> Note: this chapter's "tests" are the pipelines themselves. There is no new application code to
> unit-test - the verification is that CI (and CodeQL) run green on the pull request/commit.

## 3. Dependabot - watch dependencies (`.github/dependabot.yml`)

Your app stands on many third-party libraries; they release fixes and security patches constantly.
**Dependabot** opens pull requests when a dependency has a newer version, and (separately) alerts you
when a dependency you use has a **known vulnerability** and opens a fix PR.

*Analogy:* a diligent assistant who reads every supplier's recall notice and drafts the paperwork to
upgrade.

We watch all three ecosystems this repo uses: **gradle** (backend), **npm** (`ui/`), and
**github-actions** (the workflows themselves), weekly. Alerts and automatic security-fix PRs are
turned on in the repo's security settings (see section 6).

## 4. CodeQL - scan the code for security bugs (`.github/workflows/codeql.yml`)

**CodeQL** is GitHub's static analysis engine: it reads your source like a security reviewer and flags
dangerous patterns (injection, unsafe deserialization, etc.). Findings appear under the repo's
**Security -> Code scanning** tab.

*Analogy:* a tireless proofreader who only cares about security mistakes.

We scan both languages: **java-kotlin** (with `autobuild`, which runs Gradle to compile) and
**javascript-typescript** (no build needed), on push/PR and weekly. This is "advanced setup" (a
workflow file you can read and version), which is why we confirmed GitHub's "default setup" was not
already enabled - the two are mutually exclusive.

## 5. Secret scanning + push protection

Two related safety nets, enabled in settings:
- **Secret scanning** watches the repo for things that look like credentials (API keys, tokens) and
  alerts you.
- **Push protection** goes further: it **blocks a push** that contains a recognised secret *before* it
  ever lands in history.

*Analogy:* a metal detector at the door. Secret scanning notices a key that got inside; push
protection stops it at the entrance.

This is the automated backstop for the rule we have followed by hand all along: the Blockfrost key and
any admin token live in **environment variables, never in the repo** (Chapters 05/06 and the
`run-backend.sh` helper).

## 6. Turning the security features on

CI, Dependabot config, and CodeQL are **files** in the repo. The alert/scanning features are **repo
settings**, enabled once (here, via the API):
- Dependabot **vulnerability alerts** + **automatic security-fix PRs**
- **Secret scanning** + **push protection**

You can also toggle these under **Settings -> Code security** in the GitHub UI.

## 7. Two housekeeping items

- **Action versions bumped.** The deploy workflow used older action versions that GitHub now runs on a
  newer Node, producing a deprecation warning. We bumped `checkout`, `setup-node`, `configure-pages`,
  `upload-pages-artifact`, and `deploy-pages` to current majors. (Dependabot's `github-actions`
  ecosystem will keep these fresh from now on.)
- **LICENSE added (MIT).** A public repo with no licence is "all rights reserved" by default - nobody
  may legally reuse it. MIT is a simple, permissive choice for a learning project. Swap it if you
  prefer a different one.

## 8. A hosting note that belongs here: per-visitor rate limiting

The rate limiter (Chapter 06) counts requests per client IP. Behind a tunnel that presents one shared
IP, the limit is coarse (it can over-limit, never under-limit - acceptable). If you host behind
**Cloudflare**, set `WALL_CLIENT_IP_HEADER=CF-Connecting-IP` so the limiter uses each visitor's real
IP. See `infra/HOSTING.md`.

## 9. How to see it working
- **Actions tab:** the `CI` and `CodeQL` runs on your latest commit should be green.
- **Security tab:** Code scanning (CodeQL), Dependabot alerts, and Secret scanning each have a page.
- **Pull requests:** Dependabot will open dependency PRs on its schedule; CI runs on each of them, so
  you can merge with confidence.

## 10. What to notice / common mistakes
- **CI is a safety net, not a substitute for local checks.** Still run tests locally; CI catches what
  you forget and proves it to others.
- **Default vs advanced CodeQL are mutually exclusive.** If "default setup" is on, an advanced
  workflow will fail until you disable it (we checked - ours was not configured).
- **Push protection can block a real commit.** That is the point. If it fires, remove the secret and
  rotate it - do not bypass it to "fix later".
- **Dependabot PRs need attention, not blind merging.** CI on each PR helps, but read major-version
  bumps.
- **Pin actions to a major version** (e.g. `@v7`) so they do not change under you; let Dependabot
  propose the bumps.

## Glossary (Chapter 09)
- **CI (Continuous Integration)** - automatically building and testing every change.
- **Workflow / job / step** - a GitHub Actions pipeline (`.yml`), its parallel jobs, and the commands
  inside each.
- **Dependabot** - GitHub's bot for dependency update PRs and vulnerability alerts/fixes.
- **CodeQL** - GitHub's static analysis engine for finding security bugs in source code.
- **Static analysis** - inspecting code without running it.
- **Secret scanning** - detecting committed credentials; **push protection** blocks them before they
  are committed.
- **CVE** - a public identifier for a known security vulnerability.
- **License (MIT)** - the terms under which others may use your code; MIT is permissive.
