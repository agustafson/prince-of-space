package com.example.showcase;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.StructuredTaskScope;
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
    AutoCloseable {

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
        ExecutorService executorService) {
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
        int maxResults) {
        var stream = input.stream().filter(predicate).sorted(comparator);
        if (removeDuplicates) {
            stream = stream.distinct();
        }
        return stream.limit(maxResults).collect(Collectors.toList());
    }

    // Scenario 5: Method chaining with streams
    public List<String> getProcessedItems() {
        return items
            .stream()
            .filter(item -> item != null && !item.isBlank())
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
                var trimmed = item.trim();
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
        var shortTernary = items.isEmpty() ? "none" : "some";
        var longTernary = legacyField != null && !legacyField.isBlank()
            ? legacyField.trim().toUpperCase()
            : items.stream().findFirst().orElse("unknown").toUpperCase();
        return longTernary;
    }

    // Scenario 9: Binary operator wrapping
    public boolean isValid() {
        return legacyField != null
            && !legacyField.isBlank()
            && items != null
            && !items.isEmpty()
            && items.stream().allMatch(item -> item != null && !item.isBlank())
            && complexGenericField != null
            && complexGenericField.size() > 0;
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
            .filter(item -> item != null && !item.isBlank())
            .collect(Collectors.groupingBy(
                item -> String.valueOf(item.charAt(0)).toUpperCase(),
                Collectors.mapping(String::toLowerCase, Collectors.toList())));
    }

    // Scenario 12: Try-with-resources
    public void copyFile(Path source, Path destination) throws IOException {
        try (var input = new FileInputStream(source.toFile());
             var output = new FileOutputStream(destination.toFile());
             var buffered = new BufferedInputStream(input)) {
            buffered.transferTo(output);
        }
    }

    // Scenario 13: Annotation placement (including JSpecify type-use)
    public @org.jspecify.annotations.Nullable String findItem(String query) {
        return items
            .stream()
            .filter(item -> item.contains(query))
            .findFirst()
            .orElse(null);
    }

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
        var traditional = "Hello " + legacyField + ", you have " + items.size() + " items in your collection. "
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
        List<T> source,
        Function<T, R> transformer,
        java.util.function.BinaryOperator<R> combiner) {
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

    // Scenario 21: Record declarations
    record UserProfile(String name, String email, int age) {}

    record DetailedProfile(
        String firstName,
        String lastName,
        String email,
        String phone,
        String address,
        String city,
        String country,
        int age) {

        DetailedProfile {
            if (age < 0) {
                throw new IllegalArgumentException("Age must be non-negative: " + age);
            }
        }
    }

    // Scenario 22: Sealed interface hierarchy
    sealed interface Shape permits Shape.Circle, Shape.Rectangle, Shape.Triangle {

        double area();

        record Circle(double radius) implements Shape {

            @Override
            public double area() {
                return Math.PI * radius * radius;
            }
        }

        record Rectangle(double width, double height) implements Shape {

            @Override
            public double area() {
                return width * height;
            }
        }

        record Triangle(double base, double height) implements Shape {

            @Override
            public double area() {
                return 0.5 * base * height;
            }
        }
    }

    // Scenario 23: Pattern matching instanceof
    public String describeShape(Shape shape) {
        if (shape instanceof Shape.Circle circle) {
            return "Circle with radius " + circle.radius() + " and area " + String.format("%.2f", circle.area());
        } else if (shape instanceof Shape.Rectangle rect && rect.width() == rect.height()) {
            return "Square with side " + rect.width();
        } else if (shape instanceof Shape.Rectangle rect) {
            return "Rectangle " + rect.width() + " by " + rect.height();
        } else if (shape instanceof Shape.Triangle tri) {
            return "Triangle with base " + tri.base() + " and height " + tri.height();
        } else {
            return "Unknown shape";
        }
    }

    // Scenario 24: Switch expressions
    public double getScaleFactor(Shape shape) {
        return switch (shape) {
            case Shape.Circle c -> c.radius() > 100 ? 0.5 : 1.0;
            case Shape.Rectangle r -> r.width() == r.height() ? 1.0 : 0.75;
            case Shape.Triangle t -> 1.0;
        };
    }

    // Scenario 25: Text blocks
    public String generateReport() {
        return """
                Report for %s
                =============
                Items: %d
                Status: %s
                """.formatted(legacyField, items.size(), isValid() ? "valid" : "invalid");
    }

    // Scenario 26: Record patterns in switch
    public String serializeShape(Shape shape) {
        return switch (shape) {
            case Shape.Circle(var r) -> "circle:" + r;
            case Shape.Rectangle(var w, var h) -> "rect:" + w + "x" + h;
            case Shape.Triangle(var b, var h) -> "tri:" + b + "x" + h;
        };
    }

    // Scenario 27: Switch with guards (when clauses)
    public String classifyShape(Shape shape) {
        return switch (shape) {
            case Shape.Circle(var r) when r > 100 -> "large circle";
            case Shape.Circle(var r) when r > 10 -> "medium circle";
            case Shape.Circle c -> "small circle";
            case Shape.Rectangle(var w, var h) when w == h -> "square with side " + w;
            case Shape.Rectangle(var w, var h) when w > h * 2 -> "wide rectangle " + w + " by " + h;
            case Shape.Rectangle r -> "rectangle " + r.width() + " by " + r.height();
            case Shape.Triangle(var b, var h) when b * h / 2 > 1000 -> "large triangle";
            case Shape.Triangle t -> "triangle with base " + t.base();
        };
    }

    // Scenario 28: Virtual threads
    public void processWithVirtualThreads(List<String> urls) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = urls
                .stream()
                .map(url -> executor.submit(() -> fetchAndProcess(url)))
                .toList();
            for (var future : futures) {
                var result = future.get();
                System.out.println("Processed: " + result);
            }
        }
    }

    // Scenario 29: Structured concurrency
    record LoadedProfile(String name, List<String> orders, double balance) {}

    public LoadedProfile loadUserProfile(String userId) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var nameTask = scope.fork(() -> fetchUserName(userId));
            var ordersTask = scope.fork(() -> fetchUserOrders(userId));
            var balanceTask = scope.fork(() -> fetchUserBalance(userId));
            scope.join().throwIfFailed();
            return new LoadedProfile(nameTask.get(), ordersTask.get(), balanceTask.get());
        }
    }

    // Scenario 30: Sequenced collections
    public void demonstrateSequencedCollections() {
        var list = List.of("first", "second", "third", "fourth", "fifth");
        var first = list.getFirst();
        var last = list.getLast();
        var reversed = list.reversed();
        System.out.println("First: " + first + ", Last: " + last);
        reversed.forEach(System.out::println);
    }

    // Scenario 31: Long for-loop header
    public int sumWithLongForHeader() {
        int sum = 0;
        for (int i = 0, j = 0; i < items.size()
            && j < items.size()
            && legacyField != null
            && legacyField.length() > 0; i++, j++) {
            sum += i + j;
        }
        return sum;
    }

    // Scenario 32: For-each with long element type
    public void forEachLongElementType() {
        for (java.util.Map.Entry<String, java.util.List<Optional<CompletableFuture<String>>>> e : complexGenericField.entrySet()) {
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

    // Scenario 36: Constructor chaining with long argument list
    public FormatterShowcase(String legacyFieldOnly, List<String> itemsOnly, boolean validate) {
        this(
            legacyFieldOnly,
            itemsOnly,
            java.util.Collections.emptyMap(),
            validate,
            "en-US",
            java.util.concurrent.ForkJoinPool.commonPool());
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
            "legacyField and items must be populated before calling longAssert in production systems with strict validation rules enabled";
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

    // Scenario 44: Lambda with long parameter list (hard maxLineLength)
    public void longLambdaParameters() {
        java.util.function.BiFunction<
            java.util.Map.Entry<String, java.util.List<Optional<CompletableFuture<String>>>>,
            java.util.Map.Entry<String, java.util.List<Optional<CompletableFuture<String>>>>,
            Integer> cmp =
            (
            java.util.Map.Entry<String, java.util.List<Optional<CompletableFuture<String>>>> left,
            java.util.Map.Entry<String, java.util.List<Optional<CompletableFuture<String>>>> right) -> left
            .getKey()
            .compareTo(right.getKey());
        cmp.apply(null, null);
    }

    // Scenario 45: Switch entry with long guard (hard maxLineLength)
    public String switchLongGuard(Object o) {
        return switch (o) {
            case String s
                when s.length() > 0
                && legacyField != null
                && !legacyField.isBlank()
                && items != null
                && !items.isEmpty()
                && s.contains(legacyField)
                && s.trim().toLowerCase().startsWith("prefix") -> "string-match";
            default -> "other";
        };
    }

    // Scenario 46: Wrapped non-chain call with block lambda argument
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

    // Placeholder methods
    private void validate(String locale) {}

    private List<String> getColumns() {
        return List.of("id", "name");
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

    private void loadData() {}

    private Object processData() {
        return null;
    }

    private Object transformResult(Object r, Object o) {
        return r;
    }

    private Object getDefaultOptions() {
        return null;
    }

    private void saveResult(Object r) {}

    private void notifyListeners(Object r) {}

    private void logError(String msg, Throwable t) {}

    private String fetchAndProcess(String url) {
        return "processed: " + url;
    }

    private String fetchUserName(String userId) {
        return "User " + userId;
    }

    private List<String> fetchUserOrders(String userId) {
        return List.of();
    }

    private double fetchUserBalance(String userId) {
        return 0.0;
    }
}
