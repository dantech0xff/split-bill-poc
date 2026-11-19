#!/usr/bin/env python3
"""Tests of worker-settings.py, which release.yml runs on the Worker's wrangler.jsonc before any
approval. Each test writes a settings file and runs the script as the workflow does.

    python3 scripts/worker-settings.test.py
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("worker-settings.py")
REPOSITORY_SETTINGS = Path(__file__).resolve().parent.parent / "proxy" / "wrangler.jsonc"

# Settings as the repository writes them, with comments, a // and a /* inside strings, an escaped
# quote and trailing commas, which Wrangler accepts.
SETTINGS = """{
  "$schema": "./node_modules/wrangler/config-schema.json",
  "name": "split-vibe-proxy", // the Worker
  /* Switches and public settings,
     as the deploy sets them. */
  "vars": {
    "AI_ENABLED": "true",
    "MIN_APP_VERSION": "10000",
    "GOOGLE_CLIENT_ID": "123-abc.apps.googleusercontent.com",
    "ANDROID_CERT_SHA256": "AA:BB:CC,DD:EE:FF",
    "TURNSTILE_SITE_KEY": "https://not-a-comment.example/*kept*/",
    "ABUSE_EMAIL": "abuse@example.com",
    "NOTE": "a \\"quoted\\" word // and | a `tick`",
  },
  "triggers": { "crons": ["30 16 * * *",] },
}
"""


class WorkerSettingsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.work = Path(tempfile.mkdtemp(prefix="worker-settings-test-"))
        self.addCleanup(shutil.rmtree, self.work, ignore_errors=True)
        self.summary = self.work / "summary.md"

    def run_script(self, settings: str | Path, summary: bool = True) -> subprocess.CompletedProcess[str]:
        if isinstance(settings, str):
            path = self.work / "wrangler.jsonc"
            path.write_text(settings)
        else:
            path = settings
        arguments = [sys.executable, "-I", str(SCRIPT), *(["--summary", str(self.summary)] if summary else []), str(path)]
        environment = {**os.environ, "GITHUB_ACTIONS": "false"}
        return subprocess.run(arguments, capture_output=True, text=True, check=False, env=environment)

    def test_settings_with_every_required_value_pass_and_show_every_variable(self) -> None:
        result = self.run_script(SETTINGS)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('  MIN_APP_VERSION = "10000"', result.stdout)
        self.assertIn('  TURNSTILE_SITE_KEY = "https://not-a-comment.example/*kept*/"', result.stdout)
        self.assertIn("GOOGLE_CLIENT_ID, ANDROID_CERT_SHA256, ABUSE_EMAIL are set.", result.stdout)
        summary = self.summary.read_text()
        self.assertIn("### The Worker's settings that this release deploys", summary)
        for row in (
            '| `AI_ENABLED` | `"true"` |',
            '| `ANDROID_CERT_SHA256` | `"AA:BB:CC,DD:EE:FF"` |',
            '| `ABUSE_EMAIL` | `"abuse@example.com"` |',
            # A pipe or a backtick in a value neither ends the cell nor the code span.
            '| `NOTE` | `"a \\"quoted\\" word // and \\| a \\u0060tick\\u0060"` |',
        ):
            self.assertIn(row, summary.splitlines())
        self.assertNotIn("The release stops here", summary)

    def test_an_empty_required_value_stops_the_release(self) -> None:
        # Blank counts as empty. The value each test takes away is kept under another name.
        for name, value, consequence in (
            ("GOOGLE_CLIENT_ID", "", "the Worker turns away every session and every sign-in"),
            ("ANDROID_CERT_SHA256", "", "invite and payment links no longer open in the app"),
            ("ABUSE_EMAIL", " ", "have no Report abuse link"),
        ):
            with self.subTest(name):
                self.summary.unlink(missing_ok=True)
                settings = SETTINGS.replace(f'"{name}": "', f'"{name}": "{value}", "_{name}": "')
                result = self.run_script(settings)
                self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                self.assertIn(f"{name} is empty in", result.stdout)
                self.assertIn(consequence, result.stdout)
                # The table still shows what the deploy would have set.
                summary = self.summary.read_text()
                self.assertIn(f'| `{name}` | `"{value}"` |', summary.splitlines())
                self.assertIn(f"**The release stops here:** `{name}` is empty.", summary)

    def test_settings_without_vars_name_every_required_value(self) -> None:
        result = self.run_script('{ "name": "split-vibe-proxy" }')
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        for name in ("GOOGLE_CLIENT_ID", "ANDROID_CERT_SHA256", "ABUSE_EMAIL"):
            self.assertIn(f"{name} is empty in", result.stdout)
        self.assertIn("**The release stops here:** `GOOGLE_CLIENT_ID` and `ANDROID_CERT_SHA256` and `ABUSE_EMAIL` are empty.", self.summary.read_text())

    def test_a_file_that_cannot_be_read_stops_the_release(self) -> None:
        for settings, reason in (
            ('{ "vars": { "AI_ENABLED": "true" }', "cannot be read"),
            ('{ "vars": ["AI_ENABLED"] }', "it has no object of vars"),
            ('{ /* never closed "vars": {} }', "a /* comment is never closed"),
            (self.work / "missing.jsonc", "cannot be read"),
        ):
            with self.subTest(reason):
                result = self.run_script(settings, summary=False)
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertIn(reason, result.stdout)

    def test_the_repository_settings_can_be_read(self) -> None:
        # Whether a release could go ahead depends on the values; the file itself must always read.
        result = self.run_script(REPOSITORY_SETTINGS, summary=False)
        self.assertIn(result.returncode, (0, 1), result.stdout + result.stderr)
        self.assertIn("  AI_ENABLED = ", result.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
