# Performance notes

The formatter does not yet ship a JMH module. For a quick regression guard, `FormatPerformanceSmokeTest`
asserts that formatting a large synthetic class (~800 methods) and many small passes completes within
generous wall-clock bounds on CI.

## Comparing to other formatters

To compare against [google-java-format](https://github.com/google/google-java-format) on the same
files, run both tools on a checkout and measure with `time` or a profiler. Keep inputs and JVM
options identical when comparing.

## Future work

- Add a `benchmark` Gradle subproject with JMH for steady-state throughput (lines/sec) and
  allocation profiles.
- Revisit `est()` / subtree string width estimates in `PrincePrettyPrinterVisitor` if profiling shows
  hot allocation from `toString()` on deep trees.
