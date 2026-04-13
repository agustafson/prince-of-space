# Formatting Decisions & Configuration Options

This document lists the key formatting decision points for the Prince of Space formatter. Each section describes a decision area, the options, what other formatters do, and our proposed default.

---

## Part 1: Configuration Options (The Knobs)

### 1. Indentation: Tabs vs Spaces

| Option | Used By |
|--------|---------|
| Spaces (4) | Java convention since 1999; Oracle style guide; IntelliJ default |
| Spaces (2) | Google Java Style; google-java-format default |
| Tabs | A minority of Java projects; more common in Go, C |

**Config:** `indentStyle: spaces|tabs`, `indentSize: <number>` (number of spaces or number of tabs per indent level)
**Default:** `spaces`, `4`

With **`spaces`**, `indentSize` is how many space characters make up one indent step. With **`tabs`**, `indentSize` is how many tab characters (`\t`) make up one indent step (often `1`).

---

### 2. Preferred Line Length & Maximum Line Length

Rather than a single hard limit, we use a two-threshold approach:

- **`preferredLineLength`** (soft limit): The formatter *tries* to keep lines at or below this length. This is where wrapping decisions are first triggered.
- **`maxLineLength`** (hard limit): The formatter *never* exceeds this length. Lines between preferred and max are allowed when wrapping at the preferred length would produce ugly results (e.g., a single-token continuation line, or breaking an expression that's only slightly over).

This is similar to how Prettier treats `printWidth` as a guide rather than a hard wall, and how rustfmt has per-construct widths alongside `max_width`.

**Examples of where the soft/hard distinction helps:**

```java
// Line is 125 chars. Preferred is 120, max is 150.
// Wrapping here would produce an ugly single-token continuation, so we allow it:
var result = someService.processRequest(requestId, userName, Optional.of(defaultConfig));

// Line is 145 chars. Still under max, but wrapping produces clean output, so we wrap:
var result = someService.processRequest(
        requestId, userName, Optional.of(defaultConfig), additionalParams);

// Line is 155 chars. Over max, must wrap regardless:
var result = someService.processRequest(
        requestId,
        userName,
        Optional.of(defaultConfig),
        additionalParams);
```

**Config:** `preferredLineLength: <number>`, `maxLineLength: <number>`
**Default:** `120`, `150`

---

### 3. Continuation Indent Size

When a statement wraps to the next line, how far should the continuation be indented? This is the #1 cause of the "pushed too far right" complaint about google-java-format.

| Choice | Used By |
|--------|---------|
| Same as indent size | Simpler; what Black does for Python |
| +4 from parent | Oracle convention |
| +8 from parent | google-java-format double-continuation |

**Config:** `continuationIndentSize: <number>`
**Default:** `4` (same as default indent size)

Units match **`indentSize`**: with **`spaces`**, this is the number of space characters inserted before a wrapped continuation line. With **`tabs`**, it is the number of **tab characters** inserted for that continuation (not derived by dividing by `indentSize`—both settings are direct character counts in their respective styles).

---

### 4. Line Wrapping Strategy

When wrapping is triggered, how do we distribute elements across lines?

| Strategy | Name | Description |
|----------|------|-------------|
| Keep as much on one line as possible | **`wide`** | Only wrap what's needed to stay within limits |
| If wrapping needed, one element per line | **`narrow`** | Every element gets its own line |
| Fit on one line or go full one-per-line | **`balanced`** | All-or-nothing: fits on one line, or each element gets its own line |

**Config:** `wrapStyle: wide|narrow|balanced`
**Default:** `balanced`

The `balanced` strategy (Prettier's approach) avoids the messy middle ground where some args are on one line and others on the next. Either it all fits, or each gets its own line.

---

### 5. Multi-Line Parameter Closing Style

When method parameters or arguments are wrapped across multiple lines, should the closing parenthesis go on its own line?

```java
// closingParenOnNewLine: true (default)
public void process(
        String name,
        int age,
        boolean active
) {
    // ...
}

doSomething(
        name,
        age,
        active
);

// closingParenOnNewLine: false
public void process(
        String name,
        int age,
        boolean active) {
    // ...
}

doSomething(
        name,
        age,
        active);
```

This applies consistently to both method parameter declarations and method call arguments.

**Wrapped type clauses:** When `implements`, `extends`, or `permits` lists wrap across lines, the **`{`** that begins the class/interface/record body uses the same idea: with `closingParenOnNewLine=true`, the `{` is typically alone on the line after the last type; with `false`, `{` stays on the same line as the last type (K&R style). There is no `)` in a type clause, but the config name reflects the shared “closing delimiter” behavior.

When `closingParenOnNewLine=true` and `continuationIndentSize` equals `indentSize`, this provides a crucial visual separator between wrapped parameters and the method body — without it, parameters and body are indistinguishable. This convention aligns with Kotlin's default style and common TypeScript/Prettier formatting.

**Config:** `closingParenOnNewLine: true|false`
**Default:** `true`

---

### 6. Trailing Commas

In Java, trailing commas are valid in enum constants and array initializers. Adding them in multi-line contexts produces cleaner diffs.

```java
// trailingCommas: true
enum Status {
    ACTIVE,
    INACTIVE,
    PENDING,
}

String[] names = {
        "Alice",
        "Bob",
        "Charlie",
};

// trailingCommas: false (default)
enum Status {
    ACTIVE,
    INACTIVE,
    PENDING
}
```

**Note:** Java only supports trailing commas in enum constants and array initializers. This option only affects those contexts.

**Config:** `trailingCommas: true|false`
**Default:** `false`

---

## Part 2: Configuration Summary

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `indentStyle` | `spaces` \| `tabs` | `spaces` | Use tabs or spaces for indentation |
| `indentSize` | integer | `4` | Number of spaces or tabs per indent level |
| `preferredLineLength` | integer | `120` | Soft line width target; wrapping is triggered here |
| `maxLineLength` | integer | `150` | Hard line width limit; never exceeded |
| `continuationIndentSize` | integer | `4` | Spaces or tabs for continuation lines (same unit convention as `indentSize`) |
| `wrapStyle` | `wide` \| `narrow` \| `balanced` | `balanced` | How to handle line wrapping |
| `closingParenOnNewLine` | boolean | `true` | Whether closing `)` goes on its own line in multi-line params/args |
| `trailingCommas` | boolean | `false` | Add trailing commas in enum constants and array initializers |

**Total: 8 options.**

---

## Part 3: Decided Formatting Behaviors (Not Configurable)

### Method Chaining (Fluent APIs / Builders / Streams)

When a chain **wraps** (preferred line length exceeded, or a lambda-heavy chain forces wrapping), each **chained** call is placed on its **own** line with a **leading dot** (same idea as Kotlin’s fluent style and Prettier’s typical JS/TS chains). The **receiver** is alone on the first line; every `.method(...)` after it starts a continuation line. Continuation lines are indented with `continuationIndentSize` from the **statement** start (not dot-aligned into the horizon).

```java
// Multi-segment chain (two or more .method() links after the receiver)
var result = list
        .stream()
        .filter(x -> x.isActive())
        .map(x -> x.getName())
        .collect(Collectors.toList());
```

**Single-segment chain:** If there is only **one** method call after a **simple** receiver (a name, `this`, `super`, or field access such as `obj.field`), it stays on one line with the receiver so trivial calls do not add an extra line:

```java
var s = items.stream();   // not split into "items" + ".stream()"
```

If the receiver is **not** “simple” (e.g. a parenthesized or nested expression), the lone `.method()` still begins on the next continuation line so layout stays consistent.

**Rationale:** Leading-dot chains are easy to scan, produce **one method per line** in diffs when the chain changes, and align with common practice in Kotlin and in Prettier-style formatters. The single-call exception matches typical Java usage for `foo.bar()` and `items.stream()` without needless vertical sprawl.

---

### Lambda Expressions

```java
// Short lambda - inline
list.forEach(x -> System.out.println(x));

// Block lambda - brace on same line as arrow
list.forEach(x -> {
    process(x);
    log(x);
});

// Lambda as method argument - opens inline, never pushes paren to new line
executor.submit(() -> {
    doWork();
    cleanup();
});
```

Lambda arguments should NOT cause the opening paren to wrap to a new line (this is the google-java-format mistake that everyone dislikes).

---

### Ternary Expressions

```java
// Fits on one line
var x = condition ? "yes" : "no";

// Doesn't fit - operator at start of continuation line
var result = someVeryLongCondition
        ? computeThisValue()
        : computeThatValue();
```

---

### Binary Operator Wrapping

```java
// Operator at start of continuation line
boolean result = isVeryLongConditionA()
        && isVeryLongConditionB()
        && isVeryLongConditionC();
```

Operators at line start makes logical structure visible from the left margin.

---

### Forced Braces

Braces are always required around `if`, `else`, `for`, `while`, `do` bodies — even single-statement bodies.

```java
// Input (no braces)
if (condition) doSomething();

// Output (braces added)
if (condition) {
    doSomething();
}
```

---

### Brace Placement

K&R style (opening brace on same line). This is the overwhelming Java convention. Not configurable.

```java
if (condition) {
    doSomething();
} else {
    doOther();
}
```

---

### Blank Lines

- One blank line between methods
- One blank line between field groups and methods
- No more than one consecutive blank line anywhere (collapse multiples)
- No blank line after opening brace or before closing brace
- Preserve single blank lines within method bodies (developer intent)

---

### Annotation Placement

```java
// Declaration annotations: own line, one per line
@Override
@Nonnull
public String toString() {

// Parameter annotations: inline (preserved as-is)
public void process(@Nonnull String name, @Valid Request request) {

// Type-use annotations (e.g., JSpecify @Nullable): position preserved
// We do NOT move these. If a developer writes:
public @Nullable String result() { ... }
// it stays exactly there. We respect type-use annotation placement.
```

Type-use annotations (like JSpecify's `@Nullable`) are placed directly adjacent to the type they annotate. The formatter preserves this position and does not reorder annotations relative to modifiers. This is critical for JSpecify/nullness correctness where `@Nullable` must be next to the type, not before modifiers.

---

### Try-with-resources

```java
// Single resource - one line
try (var stream = Files.lines(path)) {

// Multiple resources - each declaration aligned vertically
try (var input  = new FileInputStream(src);
     var output = new FileOutputStream(dest)) {
```

When multiple resources are declared, `var` (or type) declarations are aligned to the same column. The closing `)` follows `closingParenOnNewLine` setting (implemented in `PrincePrettyPrinterVisitor.visit(TryStmt)` when there is more than one resource).

---

### Array and Collection Initializers

Governed by `wrapStyle`. Same fit-or-tall logic as method arguments. Trailing commas governed by `trailingCommas` setting.

```java
// Fits on one line
int[] values = {1, 2, 3, 4, 5};

// Doesn't fit - one per line
String[] names = {
        "Alice",
        "Bob",
        "Charlie"
};
```

---

### Enum Constants

```java
// Simple enum with few constants - one line
enum Color { RED, GREEN, BLUE }

// Many constants or long names - one per line
enum Status {
    ACTIVE,
    INACTIVE,
    PENDING,
    DELETED
}

// Enum with bodies - always one per line
enum Planet {
    EARTH(5.976e+24, 6.37814e6),
    MARS(6.421e+23, 3.3972e6);

    // ...
}
```

---

### Import Organization

**Delegated to Spotless.** The Prince of Space formatter does not handle import sorting, grouping, or removal. This is Spotless's responsibility and can be configured independently.

---

## Part 4: Options We Considered But Rejected

| Option | Why Rejected |
|--------|-------------|
| Brace style (K&R vs Allman) | Java community has overwhelming consensus on K&R |
| Import order customization | Delegated to Spotless |
| Blank line customization | Our defaults match community standards |
| Operator position (start vs end of line) | Start-of-line is clearly more readable; no need for a knob |
| Single vs double quotes | Java doesn't have this choice |
| Force braces toggle | Always forcing braces is a safety/readability best practice |
