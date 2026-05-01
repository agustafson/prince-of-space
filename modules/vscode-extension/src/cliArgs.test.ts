import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { cliFormatterArgs, FormatterOptions, validateFormatterOptions } from "./cliArgs";

const defaults: FormatterOptions = {
  javaVersion: 21,
  indentStyle: "SPACES",
  indentSize: 4,
  lineLength: 120,
  wrapStyle: "BALANCED",
  closingParenOnNewLine: true,
  trailingCommas: false,
};

describe("cliFormatterArgs", () => {
  it("builds stdin invocation with all default options", () => {
    assert.deepStrictEqual(cliFormatterArgs("/opt/prince.jar", defaults), [
      "-jar", "/opt/prince.jar",
      "--stdin",
      "--java-version", "21",
      "--indent-style", "SPACES",
      "--indent-size", "4",
      "--line-length", "120",
      "--wrap-style", "BALANCED",
    ]);
  });

  it("adds --no-closing-paren-on-new-line when false", () => {
    const args = cliFormatterArgs("/opt/prince.jar", { ...defaults, closingParenOnNewLine: false });
    assert.ok(args.includes("--no-closing-paren-on-new-line"), "expected --no-closing-paren-on-new-line");
  });

  it("omits --no-closing-paren-on-new-line when true", () => {
    const args = cliFormatterArgs("/opt/prince.jar", defaults);
    assert.ok(!args.includes("--no-closing-paren-on-new-line"), "unexpected --no-closing-paren-on-new-line");
  });

  it("adds --trailing-commas when true", () => {
    const args = cliFormatterArgs("/opt/prince.jar", { ...defaults, trailingCommas: true });
    assert.ok(args.includes("--trailing-commas"), "expected --trailing-commas");
  });

  it("omits --trailing-commas when false", () => {
    const args = cliFormatterArgs("/opt/prince.jar", defaults);
    assert.ok(!args.includes("--trailing-commas"), "unexpected --trailing-commas");
  });

  it("forwards custom indent and wrap settings", () => {
    const args = cliFormatterArgs("/opt/prince.jar", {
      ...defaults,
      indentStyle: "TABS",
      indentSize: 2,
      lineLength: 80,
      wrapStyle: "NARROW",
    });
    assert.ok(args.includes("TABS"));
    assert.ok(args.includes("2"));
    assert.ok(args.includes("80"));
    assert.ok(args.includes("NARROW"));
  });
});

describe("validateFormatterOptions", () => {
  it("accepts the defaults", () => {
    assert.equal(validateFormatterOptions(defaults), null);
  });

  it("rejects javaVersion 0", () => {
    const msg = validateFormatterOptions({ ...defaults, javaVersion: 0 });
    assert.ok(msg && msg.includes("javaVersion"), `expected javaVersion error, got ${msg}`);
  });

  it("rejects javaVersion below the minimum supported release", () => {
    assert.ok(validateFormatterOptions({ ...defaults, javaVersion: 7 }));
  });

  it("rejects NaN javaVersion", () => {
    assert.ok(validateFormatterOptions({ ...defaults, javaVersion: Number.NaN }));
  });

  it("rejects non-integer javaVersion", () => {
    assert.ok(validateFormatterOptions({ ...defaults, javaVersion: 17.5 }));
  });
});
