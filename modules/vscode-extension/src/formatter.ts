import * as vscode from "vscode";
import { spawn, type ChildProcessWithoutNullStreams } from "child_process";
import type { Readable } from "node:stream";
import * as path from "path";
import { cliDaemonFormatterArgs, FormatterOptions } from "./cliArgs";

const MAX_DAEMON_PAYLOAD_BYTES = 64 * 1024 * 1024 + 1024;

const sessions = new Map<string, FormatterDaemonSession>();

/** Stops all pooled formatter JVMs (extension deactivate / settings change). */
export function disposeFormatterDaemons(): void {
  for (const s of sessions.values()) {
    s.dispose();
  }
  sessions.clear();
}

/** Stable cache key for a daemon JVM (java binary + JAR + formatter flags). */
export function daemonSessionKey(javaBin: string, jar: string, opts: FormatterOptions): string {
  return `${javaBin}\n${jar}\n${cliDaemonFormatterArgs(jar, opts).join("\n")}`;
}

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
    // Gradle shadowJar artifact name pattern — matches cli/archivesName in Gradle (see modules/cli/build.gradle.kts).
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
  opts: FormatterOptions,
  source: string,
  token: vscode.CancellationToken,
): Promise<string> {
  const session = getOrCreateSession(javaBin, jar, opts);
  return session.runFormat(source, token);
}

function getOrCreateSession(javaBin: string, jar: string, opts: FormatterOptions): FormatterDaemonSession {
  const key = daemonSessionKey(javaBin, jar, opts);
  let s = sessions.get(key);
  if (s && !s.dead) {
    return s;
  }
  if (s) {
    sessions.delete(key);
  }
  s = new FormatterDaemonSession(javaBin, jar, opts, key);
  sessions.set(key, s);
  s.raw.on("exit", () => {
    if (sessions.get(key) === s) {
      sessions.delete(key);
    }
  });
  return s;
}

class FormatterDaemonSession {
  readonly raw: ChildProcessWithoutNullStreams;
  private readonly key: string;
  private readonly stderrTail: string[] = [];
  /** Serializes framed requests on this process's stdin/stdout. */
  private queueTail: Promise<void> = Promise.resolve();

  constructor(javaBin: string, jar: string, opts: FormatterOptions, key: string) {
    this.key = key;
    this.raw = spawn(javaBin, cliDaemonFormatterArgs(jar, opts), {
      stdio: ["pipe", "pipe", "pipe"],
    });
    this.raw.stderr.setEncoding("utf8");
    this.raw.stderr.on("data", (chunk: string) => {
      if (this.stderrTail.length < 48) {
        this.stderrTail.push(chunk);
      }
    });
  }

  get dead(): boolean {
    return this.raw.exitCode !== null || this.raw.signalCode !== null;
  }

  runFormat(source: string, token: vscode.CancellationToken): Promise<string> {
    const prev = this.queueTail;
    let release!: () => void;
    this.queueTail = new Promise<void>((r) => {
      release = r;
    });
    return prev.then(() => this.runSingleFramedRequest(source, token)).finally(release);
  }

  private async runSingleFramedRequest(source: string, token: vscode.CancellationToken): Promise<string> {
    const sub = token.onCancellationRequested(() => {
      this.dispose();
      sessions.delete(this.key);
    });
    try {
      if (this.dead) {
        throw new Error("formatter daemon exited; retry the format command");
      }
      this.stderrTail.length = 0;
      await writeDaemonRequest(this.raw.stdin, source);
      const header = await readFull(this.raw.stdout, 8);
      const status = header.readUInt32BE(0);
      const payloadLen = header.readUInt32BE(4);
      if (payloadLen > MAX_DAEMON_PAYLOAD_BYTES) {
        throw new Error(`formatter response too large (${payloadLen} bytes)`);
      }
      const payload = await readFull(this.raw.stdout, payloadLen);
      const text = payload.toString("utf8");
      if (status === 0) {
        return text;
      }
      const extra = this.stderrTail.join("").trim();
      const hint = extra.length > 0 ? `${text}\n${extra}` : text;
      throw new Error(hint || `formatter failed (status ${status})`);
    } finally {
      sub.dispose();
    }
  }

  dispose(): void {
    this.raw.kill("SIGTERM");
  }
}

async function writeDaemonRequest(stdin: NodeJS.WritableStream, source: string): Promise<void> {
  const body = Buffer.from(source, "utf8");
  const header = Buffer.allocUnsafe(4);
  header.writeUInt32BE(body.length, 0);
  const frame = Buffer.concat([header, body]);

  await new Promise<void>((resolve, reject) => {
    stdin.once("error", reject);
    const ok = stdin.write(frame);
    const done = () => {
      stdin.off("error", reject);
      resolve();
    };
    if (ok) {
      queueMicrotask(done);
    } else {
      stdin.once("drain", done);
    }
  });
}

async function readFull(stream: Readable, size: number): Promise<Buffer> {
  const chunks: Buffer[] = [];
  let remaining = size;
  while (remaining > 0) {
    const piece = stream.read(remaining);
    if (piece !== null) {
      chunks.push(piece);
      remaining -= piece.length;
      continue;
    }
    await new Promise<void>((resolve, reject) => {
      const onReadable = () => {
        cleanup();
        resolve();
      };
      const onEnd = () => {
        cleanup();
        reject(new Error("Unexpected end of stream while reading formatter response"));
      };
      const onError = (err: Error) => {
        cleanup();
        reject(err);
      };
      function cleanup() {
        stream.off("readable", onReadable);
        stream.off("end", onEnd);
        stream.off("error", onError);
      }
      stream.once("readable", onReadable);
      stream.once("end", onEnd);
      stream.once("error", onError);
    });
  }
  return Buffer.concat(chunks, size);
}
