# Changelog

## Unreleased

### Added

* Native Spotless integration: `princeOfSpace()` is now a built-in step in [Spotless Gradle plugin 8.9.0+](https://github.com/diffplug/spotless/releases/tag/gradle%2Fv8.9.0) and `<princeOfSpace>` in [Spotless Maven plugin 3.9.0+](https://github.com/diffplug/spotless/releases/tag/maven%2Fv3.9.0), via [diffplug/spotless#2991](https://github.com/diffplug/spotless/pull/2991). See README "Spotless (Gradle)" / "Spotless (Maven)".

### Changed

* Bump JavaParser from 3.28.1 to 3.28.2 so multi-label unnamed switch patterns (`case Foo _, Bar _ -> …`) parse correctly ([javaparser#4996](https://github.com/javaparser/javaparser/issues/4996) / [PR #5009](https://github.com/javaparser/javaparser/pull/5009)).

## 2.1.2 (2026-05-03)

### Changed

* [4befd] chore: refresh Spring benchmark README [skip ci] (github-actions[bot])

* [a68e3] ci: strip CR/LF from GitHub token before gh release and git push (Andrew Gustafson)
