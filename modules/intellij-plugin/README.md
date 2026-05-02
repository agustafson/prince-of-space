# IntelliJ Platform plugin

Gradle project `:intellij-plugin` packages a plugin that depends on `:core`.

## Development

```bash
./gradlew :intellij-plugin:runIde
```

Uses the IntelliJ Platform Gradle plugin (Community baseline in `build.gradle.kts`). **Settings → Tools → Prince of Space** exposes every `FormatterConfig` knob (indent, line length, wrap style, trailing commas, etc.), optional **format on save**, and either **project language level** or a **fixed Java release** for JavaParser. **Code → Reformat with Prince of Space…** (and the editor context menu) uses the same settings.

**Settings storage:** options are persisted with IDE workspace scope (`StoragePathMacros.WORKSPACE_FILE`), i.e. per-user workspace state rather than committed project files. To share identical formatter defaults across machines via VCS, replicate settings manually or align team conventions; switching to project-file storage would be a separate plugin change.

## Distribution

```bash
./gradlew :intellij-plugin:buildPlugin
```

The ZIP is under `modules/intellij-plugin/build/distributions/`.
