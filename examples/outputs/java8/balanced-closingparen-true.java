package com.example.showcase;

// Scenario 67: Import-section commentary anchors (stable diff markers before import list)
/* Block comment variant for scenario67 so both line and block commentary near imports are formatted. */
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
        implements Comparable<FormatterShowcase>,
        java.io.Serializable,
        Cloneable,
        AutoCloseable
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
    public FormatterShowcase(
            String legacyField,
            List<String> items,
            Map<String, List<Optional<CompletableFuture<String>>>> complexGenericField,
            boolean validateOnConstruction,
            String defaultLocale,
            ExecutorService executorService
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
    public static <T extends Comparable<T>> List<T> filterAndSort(
            List<T> input,
            Predicate<T> predicate,
            java.util.Comparator<T> comparator,
            boolean removeDuplicates,
            int maxResults
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
        return legacyField != null
                && !legacyField.isEmpty()
                && items != null
                && !items.isEmpty()
                && items.stream().allMatch(item -> item != null && !item.isEmpty())
                && complexGenericField != null
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
            .collect(Collectors.groupingBy(
                    item -> String.valueOf(item.charAt(0)).toUpperCase(),
                    Collectors.mapping(String::toLowerCase, Collectors.toList())
            ));
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
    public static final String[] DEFAULT_COLUMNS = {
            "id",
            "name",
            "email",
            "created_at",
            "updated_at",
            "status",
            "role",
            "department",
            "very_long_column_name_created_timestamp_millis",
            "very_long_column_name_updated_timestamp_millis",
            "very_long_column_name_external_system_identifier"
    };
    public static final int[] SMALL_ARRAY = {1, 2, 3};

    // Scenario 15: Enum declarations
    enum Priority { LOW, MEDIUM, HIGH, CRITICAL }

    enum HttpStatus {
        OK(200, "OK"),
        CREATED(201, "Created"),
        BAD_REQUEST(400, "Bad Request"),
        UNAUTHORIZED(401, "Unauthorized"),
        FORBIDDEN(403, "Forbidden"),
        NOT_FOUND(404, "Not Found"),
        INTERNAL_SERVER_ERROR(500, "Internal Server Error");
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
        String traditional = "Hello "
                + legacyField
                + ", you have "
                + items.size()
                + " items in your collection. "
                + "Please review them at your earliest convenience. "
                + "If you have any questions, please contact support.";
        return traditional;
    }

    // Scenario 17: Nested interface
    @FunctionalInterface
    interface Transformer<T, R> {
        R transform(T input);
    }

    // Scenario 18: Complex generic method signature
    public <T extends Comparable<T> & java.io.Serializable, R extends List<? super T>> R transformAndCollect(
            List<T> source,
            Function<T, R> transformer,
            java.util.function.BinaryOperator<R> combiner
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
        while (i < items.size()
                && legacyField != null
                && !legacyField.isEmpty()
                && items.stream().skip(i).findFirst().isPresent()
                && complexGenericField != null) {
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

    // Scenario 35b: Wrapped multi-arg call with trailing block lambda - TDR-021 (header inline, body wraps)
    public String executeWithFallback() {
        return retryRegistry.executeWithFallbackAndCircuitBreakerOnTimeout(longOperationContextDescriptor, () -> {
            loadData();
            return processData();
        });
    }

    // Scenario 36: Constructor chaining with long argument list
    public FormatterShowcase(String legacyFieldOnly, List<String> itemsOnly, boolean validate) {
        this(
                legacyFieldOnly,
                itemsOnly,
                java.util.Collections.emptyMap(),
                validate,
                "en-US",
                java.util.concurrent.ForkJoinPool.commonPool()
        );
    }

    // Scenario 37: Multi-catch
    public void multiCatch() {
        try {
            copyFile(java.nio.file.Paths.get("a"), java.nio.file.Paths.get("b"));
        } catch (java.io.IOException
                | java.lang.IllegalStateException
                | java.lang.IllegalArgumentException
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
                "legacyField and items must be populated before calling longAssert in production systems "
                + "with strict validation rules enabled";
    }

    // Scenario 39b: Long string literal in method argument
    public void longExceptionMessage() {
        throw new IllegalStateException(
                "Component scan for configuration class [%s] could not be used with "
                + "conditions in REGISTER_BEAN phase: %s".formatted(legacyField, items)
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
        } while (i >= 0
                && legacyField != null
                && items != null
                && !items.isEmpty()
                && items.size() > i
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
        java.util.function.BiFunction<
                java.util.Map.Entry<String, java.util.List<Optional<CompletableFuture<String>>>>,
                java.util.Map.Entry<String, java.util.List<Optional<CompletableFuture<String>>>>,
                Integer> cmp =
                (
                        java.util.Map.Entry<String, java.util.List<Optional<CompletableFuture<String>>>> left,
                        java.util.Map.Entry<String, java.util.List<Optional<CompletableFuture<String>>>> right
                ) -> left.getKey().compareTo(right.getKey());
        cmp.apply(null, null);
    }

    // Scenario 45: Wrapped non-chain call with block lambda argument
    public void transformMethodsWithBlockLambda(java.util.List actualMethods, java.util.Set forcePublic) {
        java.util.List methods = CollectionUtils.transform(actualMethods, value -> {
            java.lang.reflect.Method method = (java.lang.reflect.Method) value;
            int modifiers = Constants.ACC_FINAL
                    | (method.getModifiers()
                    & ~Constants.ACC_ABSTRACT
                    & ~Constants.ACC_NATIVE
                    & ~Constants.ACC_SYNCHRONIZED);
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
                + "production systems with strict validation rules enabled and mandatory audit logging";
    }

    // Scenario 47: Break after '=' while keeping RHS on one continuation line
    public void initializerBreakAfterEqualsSingleLineRhs() {
        java.util.function.Function<java.util.Map<String, Integer>, Integer> longNamedMapper =
                map -> map.getOrDefault("ab", 0);
        System.out.println(longNamedMapper.apply(java.util.Collections.<String, Integer>emptyMap()));
    }

    // Scenario 48: Wrapped argument alignment (continuation indent, not method-name alignment)
    public void wrappedArgumentAlignmentRegression() {
        saveWithVeryLongMethodNameForAlignmentRegression(
                "alpha-alpha-alpha-alpha",
                "beta-beta-beta-beta",
                "gamma-gamma-gamma-gamma",
                "delta-delta-delta-delta"
        );
        new VeryLongArgumentCarrierForAlignmentRegression(
                "first-first-first",
                "second-second-second",
                "third-third-third",
                "fourth-fourth-fourth"
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
                .trim())
            .filter(s -> s.length() > 3)
            .map(s -> Arrays
                .stream(s.split("-"))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(part -> part
                    .toLowerCase()
                    .replace("_", "")
                    .replace(".", "")
                    .substring(0, Math.min(part.length(), 12)))
                .collect(Collectors.joining("-")))
            .map(s -> Arrays
                .stream(s.split(":"))
                .map(segment -> segment.trim().toLowerCase())
                .collect(Collectors.joining(":")))
            .anyMatch(s -> s.contains(query.toLowerCase().trim())
                    && s
                        .chars()
                        .mapToObj(c -> String.valueOf((char) c))
                        .collect(Collectors.joining())
                        .startsWith(query.substring(0, Math.min(query.length(), 3)).toLowerCase()));
    }

    // Scenario 50: Nested lambda call should avoid dangling ')' before ';'
    public void nestedLambdaWarnCallWrapping() {
        cappedLogNoCustomerData(l -> l.warn(
                "Bad thing happened and we have lots of information to tell you in this warning payload",
                new IllegalStateException(
                        "Bad thing happened and this diagnostic stack summary is also intentionally very long to force wrapping"
                )
        ));
    }

    // Scenario 53: Interface extends clause with many super-interfaces
    interface VeryLongExtendsClauseScenario53
            extends java.io.Serializable,
            java.lang.Cloneable,
            java.util.function.Supplier<String>,
            java.util.function.Consumer<String>,
            java.util.concurrent.Callable<String>,
            java.util.Comparator<String>,
            java.util.function.Predicate<String>
    {
    }

    static final class VeryLongArgumentCarrierForAlignmentRegression {
        VeryLongArgumentCarrierForAlignmentRegression(String a, String b, String c, String d) {
        }
    }

    // Scenario 54: Nested wrapped calls — inner ')' must stack continuation indent (not flush with outer call)
    public void nestedWrappedCallInnerListContinuationScenario54(String issueKey) {
        auditLogSuccessWithMessageScenario54(
                i18nGetTextScenario54(
                        UpdateALItemsConversationExecutionStatusScenario54.SUCCESS.getI18nKeyScenario54(),
                        resultGetNameScenario54(),
                        issueKey
                ),
                ""
        );
    }

    // Scenario 55: Consecutive line comments before first field in a type body (no blank line between comments)
    static final class ConsecutiveLineCommentsBeforeFieldScenario55 {
        // showroom scenario 55 comment a
        // showroom scenario 55 comment b
        private static final int FLAGS_SCENARIO55 = 1;
    }

    // Scenario 56: Wrapped call with comments interleaved in argument list
    public String scenario56WrappedCommentsInArgumentList() {
        return mergeSymbolicScenario56Arguments(
                "showroom-scenario-56-arg-alpha-long-symbolic-prefix",
                // Comment between positional arguments anchors ordering across format(format(x)) passes
                "showroom-scenario-56-arg-bravo-long-symbolic-middle",
                "showroom-scenario-56-arg-charlie-long-symbolic-near-tail",
                // Trailing-arg comment anchors the delimiter region before closing paren formatting
                "showroom-scenario-56-arg-delta-long-symbolic-suffix"
        );
    }

    private String mergeSymbolicScenario56Arguments(String alpha, String bravo, String charlie, String delta) {
        return alpha + bravo + charlie + delta;
    }

    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD})
    private @interface Scenario57Shelf {
        /**
         * Token bucket with long literals that stress-wrap inside annotation parentheses.
         */
        String[] descriptors();
    }

    // Scenario 57: Annotation declarations, nested members, arrays, mixed comments inside array literals
    @Scenario57Shelf(descriptors = {
            "showroom-scenario57-descriptor-alpha-symbolic-handle",
            /*
                     * Annotation array element block commentary must stay anchored when lists wrap across lines.
                     */
    "showroom-scenario57-descriptor-bravo-symbolic-handle",
            "showroom-scenario57-descriptor-charlie-symbolic-handle"
    })
    @SuppressWarnings({"deprecation", "unchecked", "rawtypes", "unused"})
    public void scenario57AnnotatedMethodWithBuckets() {
    }

    // Scenario 58: Nested array initializers with line and block comments between elements
    public static final String[][] SCENARIO58_TABLE = new String[][] {
            {
            "showroom-scenario58-r0-c0-extra-long-handle",
            "showroom-scenario58-r0-c1-extra-long-handle",
            "showroom-scenario58-r0-c2-extra-long-handle"
    },
            {
            // row comment before first cell keeps row grouping visible when initializer wraps
    "showroom-scenario58-r1-c0-extra-long-handle",
            /* intra-row block anchors between cells when commas wrap independently */
    "showroom-scenario58-r1-c1-extra-long-handle",
            "showroom-scenario58-r1-c2-extra-long-handle"
    }
    };

    // Scenario 59: Enum with long implements list (additional Java 17+ record echoes implements clause in sibling trees)
    enum Scenario59PipelineStage implements Runnable, java.util.function.Supplier<String>, java.util.function.Predicate<
            Scenario59PipelineStage>, java.util.function.Consumer<Object>, java.util.concurrent.Callable<
            Scenario59PipelineStage>, java.util.Comparator<Scenario59PipelineStage> {
        ALPHA,
        BRAVA;
        @Override
        public void run() {
        }

        @Override
        public String get() {
            return name();
        }

        @Override
        public boolean test(Scenario59PipelineStage candidate) {
            return candidate != null && candidate.ordinal() >= ordinal();
        }

        @Override
        public void accept(Object ignored) {
        }

        @Override
        public Scenario59PipelineStage call() {
            return ALPHA;
        }

        @Override
        public int compare(Scenario59PipelineStage lhs, Scenario59PipelineStage rhs) {
            return Integer.compare(lhs.ordinal(), rhs.ordinal());
        }
    }

    static abstract class Scenario60LongCtorHeadroom {
        Scenario60LongCtorHeadroom(
                String alphaExtraLongSymbolicHandle,
                String bravoExtraLongSymbolicHandle,
                String charlieExtraLongSymbolicHandle,
                String deltaExtraLongSymbolicHandle,
                String echoExtraLongSymbolicHandle,
                String foxtrotExtraLongSymbolicHandle,
                String golfExtraLongSymbolicHandle,
                String hotelExtraLongSymbolicHandle
        ) {
        }

        abstract void touch();
    }

    // Scenario 60: Anonymous subclass with a very long super(...) argument list
    public Scenario60LongCtorHeadroom scenario60AnonymousSubclassWithLongCtorArgumentList() {
        return new Scenario60LongCtorHeadroom(
                "showroom-scenario60-alpha-extra-long-symbolic-handle-tail",
                "showroom-scenario60-bravo-extra-long-symbolic-handle-tail",
                "showroom-scenario60-charlie-extra-long-symbolic-handle-tail",
                "showroom-scenario60-delta-extra-long-symbolic-handle-tail",
                "showroom-scenario60-echo-extra-long-symbolic-handle-tail",
                "showroom-scenario60-foxtrot-extra-long-symbolic-handle-tail",
                "showroom-scenario60-golf-extra-long-symbolic-handle-tail",
                "showroom-scenario60-hotel-extra-long-symbolic-handle-tail"
        ) {
            @Override
            void touch() {
            }
        };
    }

    // Scenario 61: Explicit generic instantiation combined with cascaded method references
    public java.util.SortedSet<String> scenario61ParameterizedCallsAndMethodRefs() {
        java.util.Comparator<String> comparatorForScenario61ExtraLongSymbolicTail = java.util.Comparator
            .<String>nullsFirst(String::compareToIgnoreCase)
            .thenComparing(String::length)
            .thenComparingInt(String::hashCode);
        return items
            .parallelStream()
            .<String>map(Function.<String, String>identity().andThen(String::trim))
            .filter(s -> s != null && !s.trim().isEmpty())
            .collect(java.util.stream.Collectors.<String, java.util.TreeSet<String>>toCollection(() -> new java.util.TreeSet<>(
                    comparatorForScenario61ExtraLongSymbolicTail
            )));
    }

    // Scenario 62: Chained instanceof guards with explicit casts (pattern-matching overload appears on Java 17+ trees)
    public boolean scenario62InstanceofCastBooleanChain(Object maybeText, Number maybeCardinality) {
        return maybeText instanceof String
                && maybeCardinality instanceof Integer
                && legacyField != null
                && ((String) maybeText).startsWith("showroom-scenario62-prefix-extra-long-symbolic-handle")
                && ((Integer) maybeCardinality).intValue() > ((String) maybeText).length()
                && ((String) maybeText).regionMatches(0, legacyField, 0, Math.min(legacyField.length(), 9));
    }

    // Scenario 63: Nested lambdas crossing scheduling and per-element iteration
    public void scenario63NestedLambdasFanout() {
        items.forEach(outerLine -> executorService.submit(() -> items
            .stream()
            .filter(inner -> inner != null && inner.compareTo(outerLine) != 0)
            .forEach(innerLine -> {
                                                                            Runnable scoped =
                                                                                    () -> System.out.println(
                                                                                            innerLine + outerLine + showroomScenario63Tail()
                                                                            );
                                                                            scoped.run();
                                                                        })));
    }

    private static String showroomScenario63Tail() {
        return "-showroom-scenario63-nested-tail-extra-long-symbolic-handle";
    }

    // Scenario 64: Try / catch / finally with commentary between resources and handlers
    public void scenario64TryCatchFinallyWithComments(Path source, Path destination) throws IOException {
        try (// scenario64 head comment anchors the try-with-resources open layout across trees
        FileInputStream scenario64Input = new FileInputStream(source.toFile());
                /*
                 * scenario64 spacer between declarators stresses wrapped resource indentation.
                 */
        FileOutputStream scenario64Output = new FileOutputStream(destination.toFile());
                BufferedInputStream scenario64Buffered = new BufferedInputStream(scenario64Input)
        ) {
            byte[] buffer = new byte[4096];
            int copied;
            while ((copied = scenario64Buffered.read(buffer)) != -1) {
                scenario64Output.write(buffer, 0, copied);
            }
        } catch (IOException | IllegalStateException e) {
            logError("scenario64-io-state-extra-long-handle", e);
        } catch (RuntimeException e) {
            throw e;
        } finally {
            // scenario64 tail commentary anchors finally clause separation from catch blocks above
            loadData();
        }
    }

    // Scenario 65: Classic switch statements (switch expressions live on Java 17+ trees only)
    public String scenario65ClassicEnumSwitch(Priority scenario65Priority) {
        switch (scenario65Priority) {
            case LOW:
                return "low-showroom-scenario65-classic-enum-extra-long-symbolic-handle";
            case MEDIUM:
                return "medium-showroom-scenario65-classic-enum-extra-long-symbolic-handle";
            case HIGH:
                return "high-showroom-scenario65-classic-enum-extra-long-symbolic-handle";
            case CRITICAL:
                return "critical-showroom-scenario65-classic-enum-extra-long-symbolic-handle";
            default:
                throw new IllegalStateException("showroom-scenario65-enum-default-extra-long-symbolic-handle");
        }
    }

    public int scenario65ClassicIntSwitch(int scenario65StatusCode) {
        switch (scenario65StatusCode) {
            case 100:
                return 1;
            case 200:
                return 2;
            case 404:
                return 4;
            case 503:
                return 5;
            default:
                return 0;
        }
    }

    // Scenario 66: Fluent stream chains stretched across wide nested ternary branches
    public long scenario66FluentStreamsInsideTernary(boolean prioritizeLegacy) {
        return prioritizeLegacy
                ? (legacyField != null
                ? items
            .stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toLowerCase)
            .distinct()
            .count()
                : 0L)
                : items
            .parallelStream()
            .map(String::trim)
            .sorted(String.CASE_INSENSITIVE_ORDER.reversed())
            .mapToLong(String::hashCode)
            .sum();
    }

    enum UpdateALItemsConversationExecutionStatusScenario54 {
        SUCCESS;
        String getI18nKeyScenario54() {
            return "format.showcase.scenario54.key";
        }
    }

    private void auditLogSuccessWithMessageScenario54(String message, String extra) {
    }

    private String i18nGetTextScenario54(String i18nKey, String displayName, String issueKey) {
        return i18nKey + displayName + issueKey;
    }

    private String resultGetNameScenario54() {
        return "name";
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
