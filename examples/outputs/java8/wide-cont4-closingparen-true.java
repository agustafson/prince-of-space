package com.example.showcase;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Predicate;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

// Scenario 1: Class declaration with long implements list
public class FormatterShowcase
    implements Comparable<FormatterShowcase>, java.io.Serializable, Cloneable, AutoCloseable
{
    // Scenario 2: Field declarations with annotations
    private static final long serialVersionUID = 1L;
    @Deprecated
    private String legacyField;
    private final List<String> items;
    private final Map<String, List<Optional<CompletableFuture<String>>>> complexGenericField;
    private final ExecutorService executorService;
    // Scenario 2b: Long field lambda initializer
    private static final java.util.function.Predicate<String> LONG_FIELD_LAMBDA_FILTER =
        value -> value != null && value.trim().length() > 3 && value.startsWith("prefix");

    // Scenario 3: Constructor with many parameters
    public FormatterShowcase(String legacyField, List<String> items,
        Map<String, List<Optional<CompletableFuture<String>>>> complexGenericField, boolean validateOnConstruction,
        String defaultLocale, ExecutorService executorService
    ) {
        this.legacyField = legacyField;
        this.items = items;
        this.complexGenericField = complexGenericField;
        this.executorService = executorService;
        if (validateOnConstruction) {
            validate(defaultLocale);
        }
    }

    // Scenario 4: Method with long parameter list and generics
    public static <T extends Comparable<T>> List<T> filterAndSort(List<T> input, Predicate<T> predicate,
        java.util.Comparator<T> comparator, boolean removeDuplicates, int maxResults
    ) {
        java.util.stream.Stream<T> stream = input.stream().filter(predicate).sorted(comparator);
        if (removeDuplicates) {
            stream = stream.distinct();
        }
        return stream.limit(maxResults).collect(Collectors.toList());
    }

    // Scenario 5: Method chaining with streams
    public List<String> getProcessedItems() {
        return items
            .stream()
            .filter(item -> item != null && !item.isEmpty())
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(item -> item.length() > 3)
            .sorted()
            .distinct()
            .collect(Collectors.toList());
    }

    // Scenario 6: Method chaining with builder pattern
    public String buildQuery() {
        return new StringBuilder()
            .append("SELECT ")
            .append(String.join(", ", getColumns()))
            .append(" FROM ")
            .append(getTableName())
            .append(" WHERE ")
            .append(buildWhereClause())
            .append(" ORDER BY ")
            .append(getOrderColumn())
            .append(" LIMIT ")
            .append(getLimit())
            .toString();
    }

    // Scenario 7: Lambda expressions
    public void processWithCallbacks() {
        items.forEach(item -> System.out.println(item));

        items
            .stream()
            .filter(item -> {
                String trimmed = item.trim();
                return !trimmed.isEmpty() && trimmed.length() > 3;
            })
            .forEach(item -> System.out.println(item));

        CompletableFuture
            .supplyAsync(() -> {
                loadData();
                return processData();
            }, executorService)
            .thenApply(result -> transformResult(result, getDefaultOptions()))
            .thenAccept(finalResult -> {
                saveResult(finalResult);
                notifyListeners(finalResult);
            })
            .exceptionally(ex -> {
                logError("Processing failed", ex);
                return null;
            });
    }

    // Scenario 8: Ternary expressions
    public String getDisplayName() {
        String shortTernary = items.isEmpty() ? "none" : "some";
        String longTernary = legacyField != null && !legacyField.isEmpty()
            ? legacyField.trim().toUpperCase()
            : items.stream().findFirst().orElse("unknown").toUpperCase();
        return longTernary;
    }

    // Scenario 9: Binary operator wrapping
    public boolean isValid() {
        return legacyField != null && !legacyField.isEmpty() && items != null && !items.isEmpty()
            && items.stream().allMatch(item -> item != null && !item.isEmpty()) && complexGenericField != null
            && complexGenericField.size() > 0;
    }

    // Scenario 9b: Boolean operator chain with nested wrapped method chain operand
    public boolean hasMatchingItem(String query) {
        return items != null
            && items
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .anyMatch(s -> s.contains(query.toLowerCase()));
    }

    // Scenario 10: If/else without braces
    public String categorize(int value) {
        if (value < 0) {
            return "negative";
        } else if (value == 0) {
            return "zero";
        } else if (value < 10) {
            return "small";
        } else if (value < 100) {
            return "medium";
        } else {
            return "large";
        }
    }

    // Scenario 11: Nested generics and long method calls
    public Map<String, List<String>> groupByFirstLetter() {
        return items
            .stream()
            .filter(item -> item != null && !item.isEmpty())
            .collect(Collectors.groupingBy(item -> String.valueOf(item.charAt(0)).toUpperCase(),
                Collectors.mapping(String::toLowerCase, Collectors.toList())
            )
            );
    }

    // Scenario 12: Try-with-resources
    public void copyFile(Path source, Path destination) throws IOException {
        try (FileInputStream input = new FileInputStream(source.toFile());
            FileOutputStream output = new FileOutputStream(destination.toFile());
            BufferedInputStream buffered = new BufferedInputStream(input)
        ) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = buffered.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }
    }

    // Scenario 13: Annotation placement
    @Override
    @SuppressWarnings("unchecked")
    public int compareTo(FormatterShowcase other) {
        return this.legacyField.compareTo(other.legacyField);
    }

    // Scenario 14: Array initializers
    public static final String[] DEFAULT_COLUMNS = {"id", "name", "email", "created_at", "updated_at", "status",
        "role", "department", "very_long_column_name_created_timestamp_millis",
        "very_long_column_name_updated_timestamp_millis", "very_long_column_name_external_system_identifier"};
    public static final int[] SMALL_ARRAY = {1, 2, 3};

    // Scenario 15: Enum declarations
    enum Priority { LOW, MEDIUM, HIGH, CRITICAL }

    enum HttpStatus {
        OK(200, "OK"), CREATED(201, "Created"), BAD_REQUEST(400, "Bad Request"), UNAUTHORIZED(401, "Unauthorized"),
        FORBIDDEN(403, "Forbidden"), NOT_FOUND(404, "Not Found"), INTERNAL_SERVER_ERROR(500, "Internal Server Error");

        private final int code;
        private final String message;

        HttpStatus(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }

    // Scenario 16: Long string concatenation
    public String buildMessage() {
        String traditional = "Hello " + legacyField + ", you have " + items.size() + " items in your collection. "
            + "Please review them at your earliest convenience. " + "If you have any questions, please contact support.";
        return traditional;
    }

    // Scenario 17: Nested interface
    @FunctionalInterface
    interface Transformer<T, R> {
        R transform(T input);
    }

    // Scenario 18: Complex generic method signature
    public <T extends Comparable<T> & java.io.Serializable, R extends List<? super T>> R transformAndCollect(
        List<T> source, Function<T, R> transformer, java.util.function.BinaryOperator<R> combiner
    ) {
        return source
            .stream()
            .map(transformer)
            .reduce(combiner)
            .orElseThrow(() -> new IllegalStateException("Cannot transform empty list"));
    }

    // Scenario 19: Multiple single-line methods
    @Override
    public void close() {
        /* cleanup */
    }

    @Override
    public String toString() {
        return "FormatterShowcase{" + "legacyField='" + legacyField + "'" + ", items=" + items + "}";
    }

    // Scenario 20: Default and static interface methods
    interface Validator<T> {
        boolean validate(T item);

        default Validator<T> and(Validator<T> other) {
            return item -> this.validate(item) && other.validate(item);
        }

        default Validator<T> or(Validator<T> other) {
            return item -> this.validate(item) || other.validate(item);
        }

        static <T> Validator<T> alwaysTrue() {
            return item -> true;
        }
    }

    // Scenario 31: Long for-loop header
    public int sumWithLongForHeader() {
        int sum = 0;
        for (
            int i = 0, j = 0;
            i < items.size() && j < items.size() && legacyField != null && legacyField.length() > 0;
            i++, j++) {
            sum += i + j;
        }
        return sum;
    }

    // Scenario 32: For-each with long element type
    public void forEachLongElementType() {
        for (java.util.Map.Entry<String, java.util.List<Optional<CompletableFuture<String>>>> e :
            complexGenericField.entrySet()) {
            if (e.getKey() != null) {
                sumWithLongForHeader();
            }
        }
    }

    // Scenario 33: While with long condition
    public int drainWhileLong(int start) {
        int i = start;
        while (i < items.size() && legacyField != null && !legacyField.isEmpty()
            && items.stream().skip(i).findFirst().isPresent() && complexGenericField != null) {
            i++;
        }
        return i;
    }

    // Scenario 34: Standalone block lambda
    public void standaloneLambda() {
        java.lang.Runnable r = () -> {
            loadData();
            saveResult(processData());
        };
        r.run();
    }

    // Scenario 35: Lambda as last argument (non-chain call)
    public void sortWithComparator() {
        java.util.Collections.sort(items, (String a, String b) -> {
            return a.compareToIgnoreCase(b);
        });
    }

    // Scenario 36: Constructor chaining with long argument list
    public FormatterShowcase(String legacyFieldOnly, List<String> itemsOnly, boolean validate) {
        this(legacyFieldOnly, itemsOnly, java.util.Collections.emptyMap(), validate, "en-US",
            java.util.concurrent.ForkJoinPool.commonPool()
        );
    }

    // Scenario 37: Multi-catch
    public void multiCatch() {
        try {
            copyFile(java.nio.file.Paths.get("a"), java.nio.file.Paths.get("b"));
        } catch (java.io.IOException | java.lang.IllegalStateException | java.lang.IllegalArgumentException
            | java.lang.UnsupportedOperationException e) {
            logError("multi-catch", e);
        }
    }

    // Scenario 38: Nested ternary
    public String nestedTernary(int a, int b, int c) {
        return a > 0 ? (b > 0 ? (c > 0 ? "all-positive" : "ab-only") : "a-only") : "non-positive-a";
    }

    // Scenario 39: Long assert message
    public void longAssert() {
        assert legacyField != null && items != null && !items.isEmpty() : 
            "legacyField and items must be populated before calling longAssert in production systems with "
            + "strict validation rules " + "enabled";
    }

    // Scenario 39b: Long string literal in method argument
    public void longExceptionMessage() {
        throw new IllegalStateException("Component scan for configuration class [%s] could not be used with "
            + "conditions in REGISTER_BEAN phase: " + "%s".formatted(legacyField, items)
        );
    }

    // Scenario 39c: Text block with formatted arguments
    public void textBlockFormattedMessage(String a, String b, String c) {
        throw new IllegalStateException(("Package-private method [%s] declared in class [%s] cannot be advised by "
            + "CGLIB-proxied handler class [%s], because it is effectively private.")
            .formatted(a, b, c)
        );
    }

    // Scenario 40: Synchronized block
    public void synchronizedBlock() {
        synchronized (complexGenericField != null ? complexGenericField : java.util.Collections.emptyMap()) {
            loadData();
        }
    }

    // Scenario 41: Anonymous class
    public java.util.Comparator<String> anonymousComparator() {
        return new java.util.Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.length() - o2.length();
            }
        };
    }

    // Scenario 42: Do-while with long condition
    public int doWhileLong(int n) {
        int i = n;
        do {
            i--;
        } while (i >= 0 && legacyField != null && items != null && !items.isEmpty() && items.size() > i
            && complexGenericField != null);
        return i;
    }

    // Scenario 43: Long return expression
    public String longReturnExpression() {
        return items
            .stream()
            .filter(item -> item != null && !item.isEmpty())
            .map(String::trim)
            .sorted()
            .findFirst()
            .orElse("none")
            .toUpperCase()
            .concat("-")
            .concat(legacyField != null ? legacyField : "x");
    }

    // Scenario 44: Lambda with long parameter list (line length)
    public void longLambdaParameters() {
        java.util.function.BiFunction<java.util.Map.Entry<String, java.util.List<Optional<CompletableFuture<String>>>>,
            java.util.Map.Entry<String, java.util.List<Optional<CompletableFuture<String>>>>, Integer> cmp =
            (java.util.Map.Entry<String, java.util.List<Optional<CompletableFuture<String>>>> left,
                        java.util.Map.Entry<String, java.util.List<Optional<CompletableFuture<String>>>> right
                    ) -> left.getKey().compareTo(right.getKey());
        cmp.apply(null, null);
    }

    // Scenario 45: Wrapped non-chain call with block lambda argument
    public void transformMethodsWithBlockLambda(java.util.List actualMethods, java.util.Set forcePublic) {
        java.util.List methods = CollectionUtils.transform(actualMethods, value -> {
            java.lang.reflect.Method method = (java.lang.reflect.Method) value;
            int modifiers = Constants.ACC_FINAL
                | (method.getModifiers() & ~Constants.ACC_ABSTRACT & ~Constants.ACC_NATIVE & ~Constants.ACC_SYNCHRONIZED);
            if (forcePublic.contains(MethodWrapper.create(method))) {
                modifiers = (modifiers & ~Constants.ACC_PROTECTED) | Constants.ACC_PUBLIC;
            }
            return ReflectUtils.getMethodInfo(method, modifiers);
        });
    }

    // Scenario 46: Assert string chunking should split at word boundaries
    public void assertChunkBoundaryWordSplit(boolean enabled) {
        assert enabled : 
            "legacyField and items must be populated before calling assertChunkBoundaryWordSplit in "
            + "production systems with strict validation rules enabled and mandatory audit " + "logging";
    }

    // Scenario 47: Break after '=' while keeping RHS on one continuation line
    public void initializerBreakAfterEqualsSingleLineRhs() {
        java.util.function.Function<java.util.Map<String, Integer>, Integer> longNamedMapper =
            map -> map.getOrDefault("ab", 0);
        System.out.println(longNamedMapper.apply(java.util.Collections.<String, Integer>emptyMap()));
    }

    // Scenario 48: Wrapped argument alignment (continuation indent, not method-name alignment)
    public void wrappedArgumentAlignmentRegression() {
        saveWithVeryLongMethodNameForAlignmentRegression("alpha-alpha-alpha-alpha", "beta-beta-beta-beta",
            "gamma-gamma-gamma-gamma", "delta-delta-delta-delta"
        );
        new VeryLongArgumentCarrierForAlignmentRegression("first-first-first", "second-second-second",
            "third-third-third", "fourth-fourth-fourth"
        );
    }

    // Scenario 49: Deeply nested chained calls (2-3 levels)
    public boolean deeplyNestedChainOperations(String query) {
        return items
            .stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> s
                .toLowerCase()
                .chars()
                .mapToObj(ch -> String.valueOf((char) ch))
                .collect(Collectors.joining())
                .trim()
            )
            .filter(s -> s.length() > 3)
            .map(s -> Arrays
                .stream(s.split("-"))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(part -> part
                    .toLowerCase()
                    .replace("_", "")
                    .replace(".", "")
                    .substring(0, Math.min(part.length(), 12))
                )
                .collect(Collectors.joining("-"))
            )
            .map(s -> Arrays
                .stream(s.split(":"))
                .map(segment -> segment.trim().toLowerCase())
                .collect(Collectors.joining(":"))
            )
            .anyMatch(s -> s.contains(query.toLowerCase().trim())
                && s
                    .chars()
                    .mapToObj(c -> String.valueOf((char) c))
                    .collect(Collectors.joining())
                    .startsWith(query.substring(0, Math.min(query.length(), 3)).toLowerCase())
            );
    }

    // Scenario 50: Nested lambda call should avoid dangling ')' before ';'
    public void nestedLambdaWarnCallWrapping() {
        cappedLogNoCustomerData(l -> l.warn("Bad thing happened and we have lots of information to tell you in this "
            + "warning " + "payload",
            new IllegalStateException("Bad thing happened and this diagnostic stack summary is also intentionally "
            + "very long to force " + "wrapping"
        )
        )
        );
    }

    // Scenario 53: Interface extends clause with many super-interfaces
    interface VeryLongExtendsClauseScenario53
        extends java.io.Serializable, java.lang.Cloneable, java.util.function.Supplier<String>,
        java.util.function.Consumer<String>, java.util.concurrent.Callable<String>, java.util.Comparator<String>,
        java.util.function.Predicate<String>
    {
    }

    static final class VeryLongArgumentCarrierForAlignmentRegression {
        VeryLongArgumentCarrierForAlignmentRegression(String a, String b, String c, String d) {
        }
    }

    // Placeholder methods
    private void validate(String locale) {
    }

    private List<String> getColumns() {
        return Arrays.asList("id", "name");
    }

    private String getTableName() {
        return "users";
    }

    private String buildWhereClause() {
        return "active = true";
    }

    private String getOrderColumn() {
        return "name";
    }

    private int getLimit() {
        return 100;
    }

    private void loadData() {
    }

    private Object processData() {
        return null;
    }

    private Object transformResult(Object r, Object o) {
        return r;
    }

    private Object getDefaultOptions() {
        return null;
    }

    private void saveResult(Object r) {
    }

    private void notifyListeners(Object r) {
    }

    private void logError(String msg, Throwable t) {
    }

    private void saveWithVeryLongMethodNameForAlignmentRegression(String a, String b, String c, String d) {
    }

    private void cappedLogNoCustomerData(java.util.function.Consumer<AuditLogger> consumer) {
    }

    interface AuditLogger {
        void warn(String message, Throwable throwable);
    }
}
