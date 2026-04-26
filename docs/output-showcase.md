# Output Showcase

This page shows the same Java source formatted with different configuration options.
All examples use the `java17` input from [`examples/inputs/java17/FormatterShowcase.java`](https://github.com/agustafson/prince-of-space/blob/main/examples/inputs/java17/FormatterShowcase.java).
The full set of 24 golden outputs lives in the [`examples/outputs/`](https://github.com/agustafson/prince-of-space/tree/main/examples/outputs) directory.

---

## Wrap style

`wrapStyle` is the most consequential option. It controls how elements are distributed across lines once wrapping is triggered.

### Constructor with many parameters (Scenario 3)

**Input**

```java
public FormatterShowcase(String legacyField, List<String> items,
        Map<String, List<Optional<CompletableFuture<String>>>> complexGenericField,
        boolean validateOnConstruction, String defaultLocale, ExecutorService executorService) {
    ...
}
```

**`balanced`** (default) — fits on one line or one element per line; no messy middle:

```java
public FormatterShowcase(
        String legacyField,
        List<String> items,
        Map<String, List<Optional<CompletableFuture<String>>>> complexGenericField,
        boolean validateOnConstruction,
        String defaultLocale,
        ExecutorService executorService
) {
```

**`wide`** — pack as much as fits on each line before breaking:

```java
public FormatterShowcase(String legacyField, List<String> items,
        Map<String, List<Optional<CompletableFuture<String>>>> complexGenericField, boolean validateOnConstruction,
        String defaultLocale, ExecutorService executorService
) {
```

**`narrow`** — one element per line as soon as any wrapping is needed (same as `balanced` here because the parameter list does not fit on one line):

```java
public FormatterShowcase(
        String legacyField,
        List<String> items,
        Map<String, List<Optional<CompletableFuture<String>>>> complexGenericField,
        boolean validateOnConstruction,
        String defaultLocale,
        ExecutorService executorService
) {
```

### Generic method with arguments (Scenario 4)

**Input**

```java
public static <T extends Comparable<T>> List<T> filterAndSort(List<T> input, Predicate<T> predicate, java.util.Comparator<T> comparator, boolean removeDuplicates, int maxResults) {
```

**`balanced`** / **`narrow`**:

```java
public static <T extends Comparable<T>> List<T> filterAndSort(
        List<T> input,
        Predicate<T> predicate,
        java.util.Comparator<T> comparator,
        boolean removeDuplicates,
        int maxResults
) {
```

**`wide`** — packs the first two arguments on the opening line:

```java
public static <T extends Comparable<T>> List<T> filterAndSort(List<T> input, Predicate<T> predicate,
        java.util.Comparator<T> comparator, boolean removeDuplicates, int maxResults
) {
```

### Binary operators (Scenario 9)

**Input**

```java
return legacyField != null && !legacyField.isBlank() && items != null && !items.isEmpty() && items.stream().allMatch(item -> item != null && !item.isBlank()) && complexGenericField != null && complexGenericField.size() > 0;
```

**`balanced`** / **`narrow`** — each operand on its own line:

```java
return legacyField != null
        && !legacyField.isBlank()
        && items != null
        && !items.isEmpty()
        && items.stream().allMatch(item -> item != null && !item.isBlank())
        && complexGenericField != null
        && complexGenericField.size() > 0;
```

**`wide`** — packs operands until the line is full:

```java
return legacyField != null && !legacyField.isBlank() && items != null && !items.isEmpty()
        && items.stream().allMatch(item -> item != null && !item.isBlank()) && complexGenericField != null
        && complexGenericField.size() > 0;
```

---

## Continuation indent

Continuation indent is always `2 * indentSize` (8 spaces with the default `indentSize=4`). This follows the Oracle/IntelliJ convention and guarantees that wrapped parameters are visually distinct from the method body:

```java
public FormatterShowcase(
        String legacyField,              // 8 spaces (continuation)
        List<String> items,
        ExecutorService executorService
) {
    this.legacyField = legacyField;      // 4 spaces (body indent) — clearly separate
```

---

## Closing parenthesis placement

`closingParenOnNewLine` controls whether `)` gets its own line when a parameter or argument list wraps.

**`closingParenOnNewLine: true`** (default):

```java
public FormatterShowcase(
        String legacyField,
        List<String> items,
        ExecutorService executorService
) {
    // ← closing paren on its own line
```

**`closingParenOnNewLine: false`**:

```java
public FormatterShowcase(
        String legacyField,
        List<String> items,
        ExecutorService executorService) {
    this.legacyField = legacyField;
```

---

## Interactive comparison

Open **`examples/compare.html`** in a browser for a live diff viewer: pick a Java version, choose two configurations from the dropdowns, and the two outputs are shown side-by-side with changed lines highlighted. Scroll is synchronized between the two panes.

## Viewing all 24 outputs

The `examples/outputs/` directory contains one file per configuration:

```
examples/outputs/
  java8/
  java17/
    balanced-closingparen-true.java
    balanced-closingparen-false.java
    narrow-closingparen-true.java
    narrow-closingparen-false.java
    wide-closingparen-true.java
    wide-closingparen-false.java
  java21/
  java25/
```

Each file is the full `FormatterShowcase.java` formatted with that combination of options (the two vary-able options are `wrapStyle` and `closingParenOnNewLine`; `indentStyle`, `indentSize`, `lineLength`, and `trailingCommas` are held at their defaults).

To regenerate after changing formatting behaviour:

```bash
REGENERATE_SHOWROOM=true ./gradlew :core:test --tests RegenerateShowroomGoldens
python3 scripts/generate-compare.py
```

The second command re-embeds the updated outputs into `examples/compare.html`.
