# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| Latest release | Yes |
| Older releases | Best-effort security fixes only |

## Reporting a Vulnerability

If you discover a security vulnerability, please report it responsibly:

1. **Do not open a public GitHub issue.**
2. Use [GitHub's private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability) — go to the repository's **Security** tab and click **Report a vulnerability**.
3. Include a description of the issue, steps to reproduce, and any relevant logs or proof-of-concept code.

We will acknowledge your report within 5 business days and aim to release a fix within 30 days of confirmation.

## Automated Vulnerability Scanning

This project uses GitHub's built-in security features for automated vulnerability detection:

- **Dependabot alerts** — monitors dependencies for known CVEs and opens alerts automatically.
- **Dependabot security updates** — automatically creates pull requests to update vulnerable dependencies.
- **Dependabot version updates** — configured in `.github/dependabot.yml` to keep dependencies current, reducing exposure to newly disclosed vulnerabilities.
- **GitHub code scanning (CodeQL)** — static analysis runs on every push and pull request via `.github/workflows/codeql.yml`, scanning for common vulnerability patterns in Java code.

### Enabling These Features

Repository administrators should ensure the following are enabled in **Settings > Code security and analysis**:

- [x] Dependency graph
- [x] Dependabot alerts
- [x] Dependabot security updates
- [x] Code scanning (via the CodeQL workflow checked into this repository)
- [x] Secret scanning

## Scope

Prince of Space is a code formatter — it parses Java source code and emits reformatted text. It does not execute the code it formats, make network connections, or access databases. The primary attack surface is maliciously crafted Java source files that could cause excessive resource consumption during parsing or formatting.
