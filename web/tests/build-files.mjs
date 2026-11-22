// Folders and files for the tests of the build's scripts, which run in Node. The tests' types leave
// out Node's, as the pages they test run in a browser, so those tests reach the file system through
// these, as the stylesheet tests do through source-text.mjs.
import { mkdtempSync, readFileSync, readdirSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

/** A new, empty folder among the system's temporary files. */
export function temporaryFolder() {
  return mkdtempSync(join(tmpdir(), "split-vibe-build-"));
}

/** The names of the files in the folder at [parts], joined. */
export function filesIn(...parts) {
  return readdirSync(join(...parts));
}

/** The bytes of the file at [parts], joined. */
export function bytesOf(...parts) {
  return new Uint8Array(readFileSync(join(...parts)));
}

/** Deletes the folder [folder] and all it holds. */
export function removeFolder(folder) {
  rmSync(folder, { recursive: true, force: true });
}
