# Showroom examples and Java levels

## Layout

- **`FormatterShowcase.java`** under `examples/inputs/java8`, `java17`, `java21`, and `java25` drives the **24 golden files** (`examples/outputs/...`, 6 formatter configs × 4 language trees). Scenario comments in those files label each block.
- **Do not add extra** `.java` files under `examples/inputs/java*/` for showroom-only syntax: fold new cases into `FormatterShowcase.java` for the right level, then run `REGENERATE_SHOWROOM=true ./gradlew :core:test --tests RegenerateShowroomGoldens` so `examples/outputs/` stays in sync. `ExamplesCorpusFormatTest` still walks every `.java` input, but the golden matrix is defined only for `FormatterShowcase.java`.

## Scenario numbering vs. language level

Scenarios **1–20** are valid on Java 8 and appear in every tree.

Scenarios **21–25** (records, sealed types, pattern `instanceof`, switch expressions, text blocks) require **Java 17** semantics. They appear in `java17`, `java21`, and `java25`, and are **omitted** from `java8` (the `java8` file jumps from scenario 20 to **31** so later scenario numbers stay aligned).

Scenarios **26–30** use **Java 21** features (for example record patterns in `switch`, `when` guards, virtual threads). They appear in `java21` and `java25`, and are omitted from `java8` and `java17`.

Scenarios **31–44** are shared control-flow and wrapping cases; they appear in **all** four trees.

**Scenario 45** (switch entry with a long guard) is only in **Java 17+** trees (`java17`, `java21`, `java25`).

**Scenario 46** (flexible constructor bodies, JEP 513) is only in **`java25`**.

**Scenario 53** (interface with a long `extends` clause) appears in **all** four trees.

**Scenario 54** (nested wrapped method call: outer call with two arguments where the first argument is another multi-argument call with long names) appears in **all** four trees. It is a regression guard for continuation indent across nested `(...)` so inner closing delimiters are not flushed to the outer call’s indent column.

**Scenario 55** (nested `static` class with two consecutive `//` line comments immediately before the first field) appears in **all** four trees. It is a regression guard against inserting a blank line between those comments when formatting type members.
