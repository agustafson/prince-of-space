
## Technical foundations

| Topic | Decision |
|-------|-----------|
| **Runtime** | Library built for **Java 17+**. |
| **Parser** | **JavaParser** first; thin internal abstraction allows future swap. |
| **Nullability** | **JSpecify** on public API; **NullAway** + **Error Prone** on our sources. |
| **Logging** | **SLF4J** API (optional use by callers; internal logging where useful). |
| **Artifacts** | **`core`** — normal POM with minimal transitives; **`core-bundled`** — fat JAR with shaded third-party deps. |

## Release & repository

| Topic | Decision |
|-------|-----------|
| **Name** | **prince-of-space**; Maven coordinates under `io.princeofspace` (subject to Central registration). |
| **Hosting** | Prefer **org repo** (e.g. `prince-of-space/prince-of-space`) vs personal account. |
| **Versioning / changelog** | [**Nyx**](https://github.com/mooltiverse/nyx) for SemVer + changelog + release workflow. |
| **Commits** | [**Conventional Commits**](https://www.conventionalcommits.org/) enforced in CI (semantic PR / commitlint). |
| **Publishing** | **Maven Central** via Gradle `maven-publish` + signing; **GitHub Releases** explicitly (not implied by Central upload). |
| **Dependency hygiene** | [**dependency-analysis-gradle-plugin**](https://github.com/autonomousapps/dependency-analysis-gradle-plugin) (build-health). |
| **Updates** | **Dependabot** for Gradle + GitHub Actions. |

## Build plugins (this repo)

- Spotless, Error Prone (`net.ltgt.errorprone`), JaCoCo, SpotBugs, Checkstyle (coordinate with Spotless — see [../02-formatting-decisions.md](../02-formatting-decisions.md)), gradle-test-logger-plugin.

## Versioning policy

- **Semver:** patch = bugfix/determinism; minor = backward-compatible API/config additions; major = breaking public API or default output changes.
- Document **supported Java language levels for parsing** per release in release notes / matrix.
