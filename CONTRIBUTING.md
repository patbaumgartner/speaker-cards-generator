# Contributing to Speaker Cards Generator

Thank you for your interest in contributing! This guide explains how to get started.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Code Style](#code-style)
- [Commit Messages](#commit-messages)
- [Pull Request Process](#pull-request-process)

---

## Code of Conduct

This project adheres to the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md).
By participating you agree to abide by its terms.

---

## Getting Started

1. **Fork** the repository on GitHub.
2. **Clone** your fork locally:
   ```shell
   git clone https://github.com/<your-username>/speaker-cards-generator.git
   cd speaker-cards-generator
   ```
3. Create a **feature branch**:
   ```shell
   git checkout -b feat/my-awesome-feature
   ```

---

## Development Setup

### Prerequisites

| Tool       | Version |
|------------|---------|
| Java       | 21+     |
| Maven      | 3.9+    |
| PostgreSQL | 16+     |
| Docker     | 24+     |

### Start the full stack with Docker Compose

```shell
# From the repository root
docker compose up -d

# Then run the app against the Docker PostgreSQL
./mvnw spring-boot:run -Dspring-boot.run.profiles=vdz
```

### Run tests (H2 in-memory – no database required)

```shell
./mvnw test
```

### Check code style

```shell
./mvnw io.spring.javaformat:spring-javaformat-maven-plugin:validate
```

To auto-fix formatting:

```shell
./mvnw io.spring.javaformat:spring-javaformat-maven-plugin:apply
```

---

## How to Contribute

### Bug Reports

Use the [Bug Report issue template](.github/ISSUE_TEMPLATE/bug_report.yml).
Please include steps to reproduce, the Java and Spring Boot version, and
relevant log output.

### Feature Requests

Use the [Feature Request issue template](.github/ISSUE_TEMPLATE/feature_request.yml).

### Pull Requests

- Keep PRs focused: one feature or fix per PR.
- Link the related issue with `Closes #NNN`.
- Add or update tests.
- Update the README / Javadoc if relevant.
- Make sure `./mvnw verify` passes before opening the PR.

---

## Code Style

This project uses **Spring Java Format** enforced by the
[spring-javaformat-maven-plugin](https://github.com/spring-io/spring-javaformat).

Run `./mvnw io.spring.javaformat:spring-javaformat-maven-plugin:apply` before committing to auto-format your code.

Key style notes:

- Indentation: 4 spaces (enforced by Spring Java Format)
- Line length: 120 characters
- Javadoc on all `public` classes and methods
- `final` fields and parameters where possible
- Prefer `List.of(...)` / `Map.of(...)` over mutable collections where immutability is sufficient

---

## Commit Messages

Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>(<scope>): <short summary>

[optional body]

[optional footer: Closes #NNN]
```

Common types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `ci`.

Examples:
```
feat(import): add Devoxx API pagination support
fix(banner): correct PNG dimensions for social cards
docs: update README with Docker Compose instructions
ci: add weekly dependency update schedule to Dependabot
```

---

## Pull Request Process

1. Ensure all CI checks pass (build, tests, CodeQL).
2. Request a review from a maintainer.
3. Address review feedback promptly.
4. A maintainer will merge the PR once it is approved.

We aim to review PRs within **5 business days**.

---

Thank you for helping make Speaker Cards Generator better! 🎤
