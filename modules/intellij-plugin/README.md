# IntelliJ Platform plugin

Gradle project `:intellij-plugin` packages a plugin that depends on `:core`.

## Development

```bash
./gradlew :intellij-plugin:runIde
```

Uses the IntelliJ Gradle plugin (Community 2023.2.x baseline). **Code → Reformat with Prince of Space…** (and the editor context menu) formats the current Java file using the module’s language level.

## Distribution

```bash
./gradlew :intellij-plugin:buildPlugin
```

The ZIP is under `modules/intellij-plugin/build/distributions/`.
