/**
 * Arguments passed to {@code java} to run the CLI formatter on stdin.
 * Kept in a tiny module so {@code npm test} can assert the contract without the VS Code runtime.
 */
export interface FormatterOptions {
  javaVersion: number;
  indentStyle: string;
  indentSize: number;
  lineLength: number;
  wrapStyle: string;
  closingParenOnNewLine: boolean;
  trailingCommas: boolean;
}

const MIN_JAVA_VERSION = 8;

/**
 * Validates the formatter options before we hand them to the CLI. Returns a
 * human-readable error message if the options would cause the CLI to fail with
 * an `IllegalArgumentException`, or `null` when the options look usable.
 */
export function validateFormatterOptions(opts: FormatterOptions): string | null {
  if (!Number.isInteger(opts.javaVersion) || opts.javaVersion < MIN_JAVA_VERSION) {
    return `princeOfSpace.javaVersion must be an integer >= ${MIN_JAVA_VERSION} (got ${opts.javaVersion}).`;
  }
  return null;
}

export function cliFormatterArgs(jar: string, opts: FormatterOptions): string[] {
  const args = [
    "-jar", jar,
    "--stdin",
    "--java-version", String(opts.javaVersion),
    "--indent-style", opts.indentStyle,
    "--indent-size", String(opts.indentSize),
    "--line-length", String(opts.lineLength),
    "--wrap-style", opts.wrapStyle,
  ];
  if (!opts.closingParenOnNewLine) {
    args.push("--no-closing-paren-on-new-line");
  }
  if (opts.trailingCommas) {
    args.push("--trailing-commas");
  }
  return args;
}
