# Research Notes

## Sources

- [Why are there no decent code formatters for Java?](https://jqno.nl/post/2024/08/24/why-are-there-no-decent-code-formatters-for-java/) - Jan Ouwens
- [Prettier Option Philosophy](https://prettier.io/docs/option-philosophy)
- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [rustfmt Configuration](https://github.com/rust-lang/rustfmt/blob/main/Configurations.md)
- [Black - The Uncompromising Python Code Formatter](https://github.com/psf/black)
- [Spotless - Keep your code spotless](https://github.com/diffplug/spotless)
- [Spotless CONTRIBUTING.md](https://github.com/diffplug/spotless/blob/main/CONTRIBUTING.md)
- [Oracle Java Code Conventions](https://www.oracle.com/java/technologies/javase/codeconventions-indentation.html)

## Formatter Comparison Matrix

| Feature | gofmt | Black | Prettier | rustfmt | google-java-format |
|---------|-------|-------|----------|---------|-------------------|
| Config options | 0 | ~5 | ~15 (frozen) | ~60 | 1 (AOSP mode) |
| Philosophy | One true style | Uncompromising | Frozen options | Moderate config | Opinionated |
| Line length default | N/A | 88 | 80 | 100 | 100 |
| Indent | Tabs | 4 spaces | 2 spaces | 4 spaces | 2 spaces (4 AOSP) |
| Community reception | Beloved | Widely adopted | Widely adopted | Generally liked | Controversial |

### Key Insight: The Sweet Spot

- **0 options (gofmt):** Works for Go because the language culture embraces uniformity. Java culture does not.
- **~5 options (Black):** The minimum viable set. Line length is essential. A few safety valves.
- **~10 options (our target):** Covers the things Java developers actually fight about (indent size, line length, wrapping style) without inviting bikeshedding.
- **60+ options (rustfmt):** Too many. Most users never change most options. Creates decision fatigue.

## What Java Developers Care About Most (Ranked)

Based on community discussions, IDE settings frequency, and formatter complaints:

1. **Indentation size** - 2 vs 4 spaces is the most common point of contention
2. **Line length** - 80 vs 100 vs 120 is endlessly debated
3. **Lambda formatting** - The #1 complaint about google-java-format
4. **Method chaining** - Stream API and builders make this critical in modern Java
5. **Continuation indent** - The "pushed too far right" complaint
6. **Import ordering** - Teams have strong opinions but the solution is standardize-and-forget
7. **Wrapping behavior** - When/where to break long lines
8. **Blank lines** - How many, where

Items 1-3 are "table stakes" - getting them wrong is a dealbreaker. Items 4-5 are what differentiates a good formatter from a bad one. Items 6-8 are hygiene.

## AST Parser Recommendation: JavaParser

**Why JavaParser over alternatives:**

- **Lightweight:** ~2MB, no heavy dependencies
- **Clean API:** Well-designed visitor pattern, easy to traverse and modify
- **Comment-aware:** Preserves and associates comments with AST nodes (critical for a formatter)
- **Pretty-printer:** Has built-in `DefaultPrettyPrinter` that we can extend or replace
- **Active:** Regular releases, supports Java 21+
- **Purpose-built:** Designed exactly for parsing Java source code

**Why not Eclipse JDT:** Too heavy, tightly coupled to Eclipse, complex API. Overkill for formatting.
**Why not javac internals:** Both google-java-format and palantir-java-format use javac's internal AST (`com.sun.tools.javac.tree`), which guarantees language-level parity since it IS the compiler. However: it requires `--add-exports` flags, has no official API stability guarantees, does NOT preserve comments in the AST (comments must be extracted separately from the token stream), and has no built-in pretty-printer. This is a viable choice if language fidelity is paramount, but JavaParser's public API, comment preservation, and pretty-printer infrastructure make it a better starting point for a new formatter. We should revisit if JavaParser's support for new Java syntax becomes a bottleneck.
**Why not Spoon:** Built on Eclipse JDT, more suited for code transformation. Adds weight without benefit for formatting.

## Spotless Integration Notes

### How Spotless Works
- Core is `FormatterStep`: `String format(String rawUnix, File file)`
- Steps are composed in sequence; Spotless handles encoding, line endings, idempotency, git ratcheting
- Third-party formatters are loaded via `JarState` (classpath isolation) and invoked via reflection
- Integration pattern: create a `FooStep` class implementing `FormatterStep`, handle serialization for Gradle up-to-date checks

### Integration Strategy
1. Create `prince-of-space-spotless` module that implements `FormatterStep`
2. Load our formatter jar via `JarState`
3. Call formatting API via reflection (for classpath isolation) or compile-only source set
4. **No Spotless PR needed initially** - the `custom` step API allows any formatter on the buildscript classpath to integrate immediately:
   ```groovy
   spotless {
     java {
       custom 'princeOfSpace', { source -> PrinceOfSpace.format(source) }
     }
   }
   ```
5. For first-class named step (e.g., `princeOfSpace()` in Spotless DSL), we'd later submit a PR adding a `PrinceOfSpaceStep.java` to `spotless-lib` that uses the provisioner to download our artifact
6. Spotless maintainer has expressed openness to hosting third-party formatter plugins as peers
