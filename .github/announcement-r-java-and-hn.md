# Announcement copy (r/java, Hacker News, GitHub)

Paste and adapt as needed. **r/java** rules change over time — check the sidebar for showcase / self-promotion threads.

---

## GitHub repository topics (Settings → General → Topics)

Add these (GitHub stores them on the repo; there is no `topics` file in git):

```
java, code-formatter, spotless, javaparser, maven-central, gradle, static-analysis, intellij-idea, vscode, prettier, google-java-format-alternative
```

Optional extras if you still have room: `code-style`, `ast`, `open-source`, `apache-2`

---

## GitHub Release notes (short — for the `v0.1.0` release description)

**Prince of Space 0.1.0** is the first public release on Maven Central under `io.github.agustafson`.

**Artifacts:** `prince-of-space-core`, `prince-of-space-spotless`, `prince-of-space-bundled` — see [README](https://github.com/agustafson/prince-of-space#artifacts-maven-central) for coordinates.

**Highlights:** opinionated Java formatter (Prettier/ktlint-style), eight config knobs, idempotent output, Java 8–25+ sources, Spotless `FormatterStep`, IntelliJ plugin, CLI, VS Code extension.

Full changelog: [CHANGELOG.md](https://github.com/agustafson/prince-of-space/blob/main/CHANGELOG.md).

---

## Hacker News — title (Show HN)

Use a single line; the site prefers clarity over buzzwords:

**Show HN: Prince of Space – a configurable Java formatter (Maven Central, Spotless, Prettier-like defaults)**

Alternative shorter:

**Show HN: Prince of Space – Java code formatter with 8 options, now on Maven Central**

---

## Hacker News — first comment (optional, as submitter)

You can post this as a reply to your own thread so the thread has context:

> Source and docs: https://github.com/agustafson/prince-of-space
> Maven coordinates: `io.github.agustafson:prince-of-space-core:0.1.0` (also `prince-of-space-spotless`, `prince-of-space-bundled`).
> Philosophy: strong defaults, minimal configuration (vs hundreds of IDE knobs). Happy to answer questions or take feedback.

---

## Reddit r/java — post body (Markdown)

**Title ideas** (pick one; some subs want descriptive titles):

- `Prince of Space – Java formatter on Maven Central (Spotless, IntelliJ, 8 config options)`
- `[OSS] Prince of Space 0.1.0 – Prettier-style Java formatter released to Maven Central`

**Body:**

Hi all,

I’ve published **Prince of Space**, a Java source formatter we’ve been building with a **small, explicit configuration surface** (eight options: indentation, line length / wrapping style, trailing commas, etc.) instead of reproducing every IDE toggle. The goal is closer to **Prettier / ktlint**: strong defaults, idempotent output, and `format(format(x)) == format(x)`.

**Maven Central:** `io.github.agustafson` — e.g. `io.github.agustafson:prince-of-space-core:0.1.0`. There are also **`prince-of-space-spotless`** (custom Spotless step) and **`prince-of-space-bundled`** (fat jar with relocated deps).

**Integrations:** library API, **Spotless** (`PrinceOfSpaceStep`), **IntelliJ** plugin, **CLI** shadow jar, **VS Code** extension (via CLI).

Repo + README with Gradle/Maven snippets: https://github.com/agustafson/prince-of-space

I’m not trying to replace every team’s IntelliJ preset overnight, but if you’ve wanted something **publishable and scriptable** with fewer philosophical degrees of freedom, this might be worth a look. Feedback and issues welcome.

---

## Same post for HN?

HN is usually **shorter**; the title carries the pitch. Use the **Show HN title** above and the **short first comment** block—avoid walls of text. The Reddit version can stay more detailed.
