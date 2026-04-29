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
