import * as vscode from "vscode";
import { spawn } from "child_process";
import * as path from "path";
import { cliFormatterArgs } from "./cliArgs";

export async function resolveCliJar(
  explicit: string | undefined,
  token: vscode.CancellationToken,
): Promise<string | null> {
  if (explicit && explicit.trim().length > 0) {
    const p = explicit.trim();
    const uri = vscode.Uri.file(path.isAbsolute(p) ? p : path.join((vscode.workspace.workspaceFolders ?? [])[0]?.uri.fsPath ?? "", p));
    try {
      await vscode.workspace.fs.stat(uri);
      return uri.fsPath;
    } catch {
      return null;
    }
  }
  const folders = vscode.workspace.workspaceFolders;
  if (!folders || folders.length === 0) {
    return null;
  }
  for (const wf of folders) {
    if (token.isCancellationRequested) {
      return null;
    }
    const pattern = new vscode.RelativePattern(wf, "modules/cli/build/libs/prince-of-space-cli-*.jar");
    const found = await vscode.workspace.findFiles(pattern, "**/node_modules/**", 5, token);
    if (found.length > 0) {
      return found[0].fsPath;
    }
  }
  return null;
}

export function formatJavaSource(
  javaBin: string,
  jar: string,
  javaVersion: number,
  source: string,
  token: vscode.CancellationToken,
): Promise<string> {
  return new Promise((resolve, reject) => {
    const proc = spawn(javaBin, cliFormatterArgs(jar, javaVersion), {
      stdio: ["pipe", "pipe", "pipe"],
    });
    const out: Buffer[] = [];
    const err: Buffer[] = [];
    const sub = token.onCancellationRequested(() => proc.kill("SIGTERM"));
    proc.stdout.on("data", (c: Buffer) => out.push(c));
    proc.stderr.on("data", (c: Buffer) => err.push(c));
    proc.on("error", (e) => {
      sub.dispose();
      reject(e);
    });
    proc.on("close", (code) => {
      sub.dispose();
      if (code !== 0) {
        reject(new Error(err.length > 0 ? Buffer.concat(err).toString("utf8") : `formatter exited with code ${code}`));
      } else {
        resolve(Buffer.concat(out).toString("utf8"));
      }
    });
    proc.stdin.write(source, "utf8");
    proc.stdin.end();
  });
}
