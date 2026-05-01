package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the CLI's shared-Formatter usage pattern (see {@code cli/Main.java},
 * which builds one {@link Formatter} and dispatches it across a fixed thread pool).
 *
 * <p>If any internal field of {@link Formatter} or its underlying {@code FormattingEngine} were
 * not thread-safe, threads would race on the JavaParser instance, the LexicalPreservingPrinter
 * setup, or the visitors and produce divergent or corrupted output. This test compares each
 * thread's output against the single-threaded reference for the same input.
 */
class FormatterConcurrencyTest {

    private static final int THREAD_COUNT = 8;
    private static final int ITERATIONS_PER_THREAD = 10;

    @SuppressWarnings("PMD.CloseResource") // executor is shut down in finally; ExecutorService is not AutoCloseable on the Java 17 target
    @Test
    void sharedFormatter_concurrentFormat_matchesSingleThreadReference() throws Exception {
        Formatter formatter =
                new Formatter(
                        FormatterConfig.builder()
                                .javaLanguageLevel(JavaLanguageLevel.of(21))
                                .build());

        List<String> inputs = sampleInputs();
        List<String> references = new ArrayList<>(inputs.size());
        for (String input : inputs) {
            references.add(formatter.format(input));
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<String>>> futures = new ArrayList<>(THREAD_COUNT);
        try {
            for (int t = 0; t < THREAD_COUNT; t++) {
                futures.add(
                        executor.submit(() -> {
                            start.await();
                            List<String> outputs = new ArrayList<>(inputs.size() * ITERATIONS_PER_THREAD);
                            for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                                for (String input : inputs) {
                                    outputs.add(formatter.format(input));
                                }
                            }
                            return outputs;
                        }));
            }
            start.countDown();

            for (int t = 0; t < THREAD_COUNT; t++) {
                List<String> outputs = futures.get(t).get(60, TimeUnit.SECONDS);
                assertThat(outputs)
                        .as("thread %d output count", t)
                        .hasSize(inputs.size() * ITERATIONS_PER_THREAD);
                for (int i = 0; i < outputs.size(); i++) {
                    String expected = references.get(i % inputs.size());
                    assertThat(outputs.get(i))
                            .as("thread %d iteration %d", t, i)
                            .isEqualTo(expected);
                }
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static List<String> sampleInputs() {
        return List.of(
                """
                class T {
                    void m() {
                        if (x < 0) return "neg";
                        else return "pos";
                    }
                }
                """,
                """
                import java.util.List;

                class T {
                    String find(List<String> items, String q) {
                        return items.stream().filter(item -> item.contains(q)).findFirst().orElse(null);
                    }
                }
                """,
                """
                sealed interface Shape permits Shape.Circle, Shape.Rectangle {
                    record Circle(double radius) implements Shape {}
                    record Rectangle(double width, double height) implements Shape {}
                }

                class T {
                    double area(Shape shape) {
                        return switch (shape) {
                            case Shape.Circle c -> Math.PI * c.radius() * c.radius();
                            case Shape.Rectangle r -> r.width() * r.height();
                        };
                    }
                }
                """,
                """
                class T {
                    @Override @Deprecated public void m(int x, int y, int z) {
                        for (int i = 0; i < 10; i++) {
                            int sum = x + y + z + i;
                            System.out.println(sum);
                        }
                    }
                }
                """);
    }
}
