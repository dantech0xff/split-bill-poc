#!/usr/bin/env python3
"""Shows the Worker's variables that a release deploys, and fails when a required one is empty.

    python3 scripts/worker-settings.py [--summary FILE] proxy/wrangler.jsonc

`wrangler deploy` sets every variable in the "vars" of wrangler.jsonc on the Worker, in place of
whatever value the Cloudflare dashboard holds; only secrets keep the values they were given. This
reads the file as Wrangler does, as JSON with comments and trailing commas, prints every variable,
all of which are public, and appends them as a table to FILE, such as the run's summary in GitHub
Actions. It fails when one of these is empty:

- GOOGLE_CLIENT_ID: without it the Worker turns away every session and every sign-in;
- ANDROID_CERT_SHA256: without it the Worker leaves the app out of its App Links file, so invite
  and payment links no longer open in the app;
- ABUSE_EMAIL: without it the public pages, which show what people wrote on their payment requests
  and bill links, have no "Report abuse" link.

Exit status: 0 when all of them are set, 1 when one is empty, 2 when the file cannot be read.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

# The variables a release needs, with what the service loses while one is empty.
REQUIRED = {
    "GOOGLE_CLIENT_ID": "the Worker turns away every session and every sign-in",
    "ANDROID_CERT_SHA256": "the Worker leaves the app out of its App Links file, so invite and payment links no longer open in the app",
    "ABUSE_EMAIL": "the public pages, which show what people wrote on their payment requests and bill links, have no Report abuse link",
}


def jsonc_value(text: str) -> object:
    """The value of a JSON text with // and /* */ comments and trailing commas, as Wrangler reads it."""
    kept: list[str] = []
    index, end = 0, len(text)
    while index < end:
        character = text[index]
        if character == '"':
            close = index + 1
            while close < end and text[close] != '"':
                close += 2 if text[close] == "\\" else 1
            kept.append(text[index : close + 1])
            index = close + 1
        elif text.startswith("//", index):
            newline = text.find("\n", index)
            index = end if newline < 0 else newline
        elif text.startswith("/*", index):
            close = text.find("*/", index + 2)
            if close < 0:
                raise ValueError("a /* comment is never closed")
            kept.append(" ")
            index = close + 2
        else:
            if character in "]}":
                # A comma right before the end of an object or an array is left out.
                last = len(kept) - 1
                while last >= 0 and kept[last].isspace():
                    last -= 1
                if last >= 0 and kept[last] == ",":
                    del kept[last]
            kept.append(character)
            index += 1
    return json.loads("".join(kept))


def table_cell(value: object) -> str:
    """A value as JSON in a Markdown code span, so that an empty text shows as "" and nothing in it
    can end the cell or the span."""
    return "`" + json.dumps(value, ensure_ascii=False).replace("`", "\\u0060").replace("|", "\\|") + "`"


def annotate(text: str) -> str:
    # The escaping GitHub Actions expects in the message of a workflow command.
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def report(message: str) -> None:
    print(message)
    if os.environ.get("GITHUB_ACTIONS") == "true":
        print(f"::error title=The Worker's settings::{annotate(message)}")


def main(arguments: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Shows the Worker's variables that a release deploys, and fails when a required one is empty.")
    parser.add_argument("--summary", type=Path, help="a Markdown file to append the table to, such as $GITHUB_STEP_SUMMARY")
    parser.add_argument("settings", type=Path, help="the Worker's wrangler.jsonc")
    options = parser.parse_args(arguments)

    try:
        settings = jsonc_value(options.settings.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeDecodeError, ValueError) as error:
        report(f"{options.settings} cannot be read: {error}")
        return 2
    variables = settings.get("vars", {}) if isinstance(settings, dict) else None
    if not isinstance(variables, dict):
        report(f"{options.settings} cannot be read: it has no object of vars")
        return 2

    print(f"The variables in {options.settings}, which the deploy sets on the Worker:")
    for name, value in variables.items():
        print(f"  {name} = {json.dumps(value, ensure_ascii=False)}")
    empty = [name for name in REQUIRED if not isinstance(variables.get(name), str) or not variables[name].strip()]

    if options.summary is not None:
        lines = [
            "### The Worker's settings that this release deploys",
            "",
            f"From `{options.settings}` of this commit. The deploy sets each of these on the Worker, in place of any value"
            " changed in the Cloudflare dashboard; the Worker's secrets stay as they are.",
            "",
            "| Variable | Value |",
            "|---|---|",
            *(f"| `{name}` | {table_cell(value)} |" for name, value in variables.items()),
            "",
        ]
        if empty:
            lines += [f"**The release stops here:** {' and '.join(f'`{name}`' for name in empty)} {'is' if len(empty) == 1 else 'are'} empty.", ""]
        try:
            with options.summary.open("a", encoding="utf-8") as summary:
                summary.write("\n".join(lines) + "\n")
        except OSError as error:
            report(f"The summary {options.summary} cannot be written: {error}")
            return 2

    for name in empty:
        report(f"{name} is empty in {options.settings}, and without it {REQUIRED[name]}. Commit its value to the file on main, then tag that commit.")
    if empty:
        return 1
    print(f"{', '.join(REQUIRED)} are set.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
