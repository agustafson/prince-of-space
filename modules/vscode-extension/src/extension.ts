import * as vscode from "vscode";
import { formatJavaSource, resolveCliJar } from "./formatter";

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
  const javaVersion = cfg.get<number>("javaVersion") ?? 17;
  try {
    const formatted = await formatJavaSource(javaBin, jar, javaVersion, document.getText(), token);
    const full = new vscode.Range(document.positionAt(0), document.positionAt(document.getText().length));
    return [vscode.TextEdit.replace(full, formatted)];
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    void vscode.window.showErrorMessage(`Prince of Space: ${msg}`);
    return [];
  }
}

export function activate(context: vscode.ExtensionContext): void {
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

export function deactivate(): void {}
