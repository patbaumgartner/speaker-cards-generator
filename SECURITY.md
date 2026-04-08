# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 1.x     | :white_check_mark: |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

If you discover a security vulnerability in this project, please report it
responsibly by using one of the following methods:

1. **GitHub Private Vulnerability Reporting** – Go to the repository's
   [Security tab](https://github.com/patbaumgartner/speaker-cards-generator/security/advisories/new)
   and click **"Report a vulnerability"**.

2. **Email** – Send details to the maintainer. You can find contact information
   in the GitHub profile of [@patbaumgartner](https://github.com/patbaumgartner).

Please include as much of the following information as possible:

- Type of vulnerability (e.g. injection, SSRF, insecure deserialization)
- Full path of the affected source file(s)
- Steps to reproduce
- Proof of concept or exploit code (if available)
- Impact assessment

## Response Timeline

| Step                  | Target timeframe |
|-----------------------|-----------------|
| Initial acknowledgement | 48 hours       |
| Severity assessment   | 5 business days  |
| Fix / patch release   | 30 days (critical), 90 days (others) |

## Disclosure Policy

We follow a coordinated disclosure model. Once a fix is available we will:

1. Release a patched version.
2. Publish a security advisory on GitHub.
3. Credit the reporter (unless they prefer to remain anonymous).

## Dependency Vulnerabilities

Dependencies are monitored automatically via
[Dependabot](https://github.com/patbaumgartner/speaker-cards-generator/security/dependabot)
and [CodeQL](.github/workflows/codeql.yml).
