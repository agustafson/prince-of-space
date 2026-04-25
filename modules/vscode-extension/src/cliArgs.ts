/**
 * Arguments passed to {@code java} to run the CLI formatter on stdin.
 * Kept in a tiny module so {@code npm test} can assert the contract without the VS Code runtime.
 */
export function cliFormatterArgs(jar: string, javaVersion: number): string[] {
  return ["-jar", jar, "--stdin", "--java-version", String(javaVersion)];
}
