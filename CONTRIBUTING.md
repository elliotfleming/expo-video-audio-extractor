# Contributing to Expo Video Audio Extractor

Thank you for your interest in improving **expo-video-audio-extractor**. Bug reports, feature ideas and pull requests are all welcome!

## Table of Contents

- [Contributing to Expo Video Audio Extractor](#contributing-to-expo-video-audio-extractor)
  - [Table of Contents](#table-of-contents)
  - [Code of Conduct](#code-of-conduct)
  - [Report a Bug](#report-a-bug)
  - [Request a Feature](#request-a-feature)
  - [Submit a Pull Request](#submit-a-pull-request)
  - [Local Development Setup](#local-development-setup)
  - [Branching \& Workflow](#branching--workflow)
  - [Commit Message Guidelines](#commit-message-guidelines)
  - [License](#license)

---

## Code of Conduct

This project follows the [Contributor Covenant](https://www.contributor-covenant.org/version/2/1/code_of_conduct/).
By participating you agree to abide by its terms. Please report unacceptable behaviour to a maintainer.

---

## Report a Bug

1. **Search existing issues** to avoid duplicates.
2. Open a new issue and include:

   * **Steps to reproduce** (code snippets, screenshots, device/OS info).
   * **Expected** vs. **actual** behaviour.
   * Version info: expo, Expo SDK (if any), library version, OS versions.

---

## Request a Feature

1. Check that the idea hasn’t already been discussed.
2. Open a *feature request* issue describing:

   * The problem you’re trying to solve.
   * Proposed API / usage example.
   * Edge-cases or technical considerations.

---

## Submit a Pull Request

> **Target branch:** `main`

1. **Fork & clone** your fork:

   ```bash
   git clone https://github.com/elliotfleming/expo-video-audio-extractor.git
   cd expo-video-audio-extractor
   ```

2. **Create a branch**:

   ```bash
   git checkout -b feat/<ShortDescriptiveName>
   ```

3. **Install dependencies**:

   ```bash
   npm install
   ```

4. **Make changes** ensuring:

   * Code passes `npm run lint` and `npm run build`.
   * New functionality includes tests or an example snippet in the README.

5. **Run checks**:

   ```bash
   npm run lint      # ESLint + Prettier
   npm run build     # Type-check + emit JS & .d.ts
   ```

6. **Commit** using the [Conventional Commits](#commit-message-guidelines) format.

7. **Push** and open a PR against `main`, describing what changed and referencing any issues.

---

## Local Development Setup

| Task                          | Command         |
| ----------------------------- | --------------- |
| Install deps                  | `npm install`   |
| Lint & format                 | `npm run lint`  |
| Compile TypeScript → `build/` | `npm run build` |
| Watch & re-build on save      | `npx tsc -w`    |

> **Testing in an app:**
>
> 1. From the library root run `npm pack` to create a tarball.<br>
> 2. In a sample Expo or expo app: `npm install <path-to-tarball>` and run the app with a development build (`eas build --profile development`).

---

## Branching & Workflow

| Branch    | Purpose                       |
| --------- | ----------------------------- |
| **main**  | Stable, released code.        |
| **feat/** | New features or enhancements. |
| **fix/**  | Bug fixes.                    |
| **docs/** | Documentation-only changes.   |

Keep your branch up-to-date:

```bash
git fetch origin
git rebase origin/main
```

---

## Commit Message Guidelines

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

<body>

<footer>
```

* **type:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`
* **scope:** optional area (`ios`, `android`, `plugin`, etc.)
* **subject:** ≤ 50 chars, imperative.

Example:

```
feat(android): add hardware-encoder fallback

Implements COLOR_FormatSurface detection and falls back to
ARGB→YUV conversion on devices that reject RGBA input.

Closes #27
```

---

## License

By contributing you agree your work will be released under the project’s [MIT License](LICENSE).
