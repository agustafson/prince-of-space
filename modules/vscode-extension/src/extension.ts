import * as vscode from "vscode";
import { disposeFormatterDaemons, formatJavaSource, resolveCliJar } from "./formatter";
import { FormatterOptions, validateFormatterOptions } from "./cliArgs";

async function formatJavaDocument(
  document: vscode.TextDocument,
  token: vscode.CancellationToken,
): Promise<vscode.TextEdit[] | undefined> {
  if (document.languageId !== "java") {
    return [];
  }
  const cfg = vscode.workspace.getConfiguration("princeOfSpace");
  const jar = await resolveCliJar(cfg.get<string>("cliJar"), token);
  if (!jar) {
    void vscode.window.showErrorMessage(
      "Prince of Space: set princeOfSpace.cliJar or build the CLI (./gradlew :cli:shadowJar) so modules/cli/build/libs/prince-of-space-cli-*.jar exists.",
    );
    return [];
  }
  const javaBin = cfg.get<string>("javaExecutable") ?? "java";
  const opts: FormatterOptions = {
    javaVersion: cfg.get<number>("javaVersion") ?? 17,
    indentStyle: cfg.get<string>("indentStyle") ?? "SPACES",
    indentSize: cfg.get<number>("indentSize") ?? 4,
    lineLength: cfg.get<number>("lineLength") ?? 120,
    wrapStyle: cfg.get<string>("wrapStyle") ?? "BALANCED",
    closingParenOnNewLine: cfg.get<boolean>("closingParenOnNewLine") ?? true,
    trailingCommas: cfg.get<boolean>("trailingCommas") ?? false,
  };
  const validationError = validateFormatterOptions(opts);
  if (validationError) {
    void vscode.window.showErrorMessage(`Prince of Space: ${validationError}`);
    return [];
  }
  const sourceText = document.getText();
  try {
    const formatted = await formatJavaSource(javaBin, jar, opts, sourceText, token);
    const full = new vscode.Range(document.positionAt(0), document.positionAt(sourceText.length));
    return [vscode.TextEdit.replace(full, formatted)];
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    void vscode.window.showErrorMessage(`Prince of Space: ${msg}`);
    return [];
  }
}

export function activate(context: vscode.ExtensionContext): void {
  context.subscriptions.push(
    vscode.workspace.onDidChangeConfiguration((e) => {
      if (e.affectsConfiguration("princeOfSpace")) {
        disposeFormatterDaemons();
      }
    }),
  );

  context.subscriptions.push(
    vscode.languages.registerDocumentFormattingEditProvider({ language: "java" }, {
      provideDocumentFormattingEdits: (doc, _opt, tok) => formatJavaDocument(doc, tok),
    }),
  );

  context.subscriptions.push(
    vscode.commands.registerCommand("princeOfSpace.formatDocument", async () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor || editor.document.languageId !== "java") {
        void vscode.window.showInformationMessage("Prince of Space: open a Java file to format.");
        return;
      }
      const tokenSource = new vscode.CancellationTokenSource();
      try {
        const edits = await formatJavaDocument(editor.document, tokenSource.token);
        if (edits && edits.length > 0) {
          await editor.edit((eb) => {
            for (const ed of edits) {
              eb.replace(ed.range, ed.newText);
            }
          });
        }
      } finally {
        tokenSource.dispose();
      }
    }),
  );
}

export function deactivate(): void {
  disposeFormatterDaemons();
}
