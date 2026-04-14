# VS Code extension — Prince of Space

Formats Java by running the **`prince-of-space-cli`** shadow JAR (`--stdin` / `--java-version`).

## Setup

1. Build the CLI from the repository root:

   ```bash
   ./gradlew :cli:shadowJar
   ```

2. In this directory:

   ```bash
   npm install
   npm run compile
   ```

3. Open **Run and Debug** in VS Code and launch **Extension Development Host**, or package with [vsce](https://github.com/microsoft/vscode-vsce).

## Settings

| Setting | Default | Meaning |
|---------|---------|---------|
| `princeOfSpace.cliJar` | _(empty)_ | Absolute path to the shadow JAR. If empty, the extension searches each workspace folder for `modules/cli/build/libs/prince-of-space-cli-*.jar`. |
| `princeOfSpace.javaExecutable` | `java` | Java binary used to run the JAR. |
| `princeOfSpace.javaVersion` | `17` | Passed to `--java-version`. |

## Commands

- **Prince of Space: Format Document** — formats the active Java editor (same engine as the formatting provider).
