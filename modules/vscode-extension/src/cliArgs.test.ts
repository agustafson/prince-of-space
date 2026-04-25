import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { cliFormatterArgs } from "./cliArgs";

describe("cliFormatterArgs", () => {
  it("builds stdin invocation for the configured Java release", () => {
    assert.deepStrictEqual(cliFormatterArgs("/opt/prince.jar", 21), [
      "-jar",
      "/opt/prince.jar",
      "--stdin",
      "--java-version",
      "21",
    ]);
  });
});
