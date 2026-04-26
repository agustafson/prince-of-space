"use strict";

/** Matches package.json engines.node (major only). */
var MIN_NODE_MAJOR = 22;
var major = parseInt(process.versions.node.split(".")[0], 10);
if (major < MIN_NODE_MAJOR || Number.isNaN(major)) {
  console.error(
    "Prince of Space VS Code extension: Node.js " +
      MIN_NODE_MAJOR +
      "+ is required to run TypeScript 6 / npm scripts (see package.json engines and .nvmrc). " +
      "Current: " +
      process.version +
      ".",
  );
  process.exit(1);
}
