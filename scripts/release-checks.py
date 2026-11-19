#!/usr/bin/env python3
"""Checks a release build of the app before it ships.

    python3 scripts/release-checks.py [--proxy-url URL] PACKAGE.apk [PACKAGE.apk ...]

Give it one APK that holds the whole app, such as the release APK Gradle builds, or every APK that
bundletool makes from the app bundle: the base APKs, one for each range of Android versions, and
their splits, which together are what phones install. It checks that

- no package is debuggable;
- nothing allows plain HTTP: android:usesCleartextTraffic is not true, the network security config,
  if any, permits no cleartext anywhere, and the target SDK is at least 28, below which plain HTTP is
  allowed by default;
- the proxy address the build was made with, given as --proxy-url, is https, is in the app's code,
  and the code holds no plain-http address of the same host. Without --proxy-url this check is
  skipped: a build without a proxy address has AI reading off and reaches no proxy;
- no file holds a string that looks like a secret key: a private key, an Anthropic API key, a secret
  API key that starts with sk_ (RevenueCat's, for one), or a Google OAuth client secret. The public
  keys every copy of the app carries, such as RevenueCat's goog_ key and Firebase's API key, are
  fine. A finding names the file and the kind of key, never the key itself.

The packages are read with aapt2 from the Android SDK's build tools: ANDROID_BUILD_TOOLS names that
directory, otherwise the newest one under ANDROID_HOME or ANDROID_SDK_ROOT is used.

Exit status: 0 when every check passes, 1 when a check fails, 2 when the packages cannot be read.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import urlsplit

ANDROID = "http://schemas.android.com/apk/res/android:"

# Each kind of secret, as bytes. The fixed start of a key (sk_, -----BEGIN ...) is not secret, so a
# finding may name it; what follows it never reaches the output.
SECRET_KINDS = (
    # The body may be on lines of its own, or follow escaped newlines (\n) as in a JSON key file.
    ("a private key", rb"-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY(?: BLOCK)?-----[A-Za-z0-9+/=\s\\]{64,}"),
    ("an Anthropic API key (sk-ant-)", rb"sk-ant-[A-Za-z0-9_-]{20,}"),
    # RevenueCat's secret API keys and Stripe's start with sk_. The look-behind keeps out words that
    # merely end in "sk", such as task_ or disk_.
    ("a secret API key (sk_)", rb"(?<![A-Za-z0-9])sk_(?:live_|test_)?[A-Za-z0-9]{24,}"),
    ("a Google OAuth client secret (GOCSPX-)", rb"GOCSPX-[A-Za-z0-9_-]{24,}"),
)
SECRETS = re.compile(b"|".join(b"(?P<k%d>%s)" % (index, pattern) for index, (_, pattern) in enumerate(SECRET_KINDS)))
# Text stored as UTF-16, as resource tables and compiled XML may store it: printable ASCII, each
# character followed by a zero byte.
UTF16_TEXT = re.compile(rb"(?:[\t\n\r\x20-\x7e]\x00){16,}")
DEX = re.compile(r"classes\d*\.dex")


class UnreadableInput(Exception):
    """A package or a tool that the checks need could not be read or run."""


@dataclass
class Element:
    """An element of a compiled XML file as aapt2 prints it. Attribute names keep their namespace."""

    name: str
    attributes: dict[str, str] = field(default_factory=dict)
    children: list[Element] = field(default_factory=list)

    def child(self, name: str) -> Element | None:
        return next((element for element in self.children if element.name == name), None)

    def walk(self):
        yield self
        for element in self.children:
            yield from element.walk()


@dataclass
class Package:
    path: Path
    manifest: Element
    application: Element | None

    @property
    def is_base(self) -> bool:
        # Split APKs name their split in the manifest; the base APK and a whole-app APK do not.
        return "split" not in self.manifest.attributes


@dataclass
class Result:
    name: str
    status: str  # PASS, FAIL or SKIP
    details: list[str]


def build_tools() -> Path:
    explicit = os.environ.get("ANDROID_BUILD_TOOLS", "").strip()
    if explicit:
        return Path(explicit)
    for variable in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk = os.environ.get(variable, "").strip()
        versions = [path for path in (Path(sdk) / "build-tools").glob("*") if (path / "aapt2").is_file()] if sdk else []
        if versions:
            return max(versions, key=lambda path: tuple(int(number) for number in re.findall(r"\d+", path.name)))
    raise UnreadableInput("aapt2 was not found: set ANDROID_BUILD_TOOLS to a build-tools directory of the Android SDK, or ANDROID_HOME to the SDK")


def aapt2(tools: Path, *arguments: str) -> str:
    tool = tools / "aapt2"
    if not tool.is_file():
        raise UnreadableInput(f"{tool} does not exist")
    result = subprocess.run([str(tool), *arguments], capture_output=True, text=True, check=False)
    if result.returncode != 0:
        message = (result.stderr or result.stdout).strip()
        raise UnreadableInput(f"aapt2 {' '.join(arguments)} failed: {message}")
    return result.stdout


def parse_xmltree(text: str) -> Element:
    """Turns the output of `aapt2 dump xmltree` into elements.

    aapt2 indents an element's attributes and its text two spaces deeper than the element, and its
    child elements four spaces deeper. A namespace line (N:) wraps the elements below it.
    """
    document = Element("#document")
    stack: list[tuple[int, Element]] = [(-1, document)]
    for line in text.splitlines():
        match = re.match(r"( *)([NEAT]): (.*)", line)
        if not match:
            continue
        indent, kind, rest = len(match[1]), match[2], match[3]
        while stack[-1][0] >= indent:
            stack.pop()
        parent = stack[-1][1]
        if kind == "N":
            stack.append((indent, parent))
        elif kind == "E":
            element = Element(rest.split(" (line=")[0])
            parent.children.append(element)
            stack.append((indent, element))
        elif kind == "A":
            name, _, value = rest.partition("=")
            parent.attributes[re.sub(r"\(0x[0-9a-fA-F]+\)$", "", name)] = attribute_value(value)
    return document


def attribute_value(raw: str) -> str:
    # Strings print as "text" (Raw: "text"); booleans, numbers and references (@0x7f...) as they are.
    quoted = re.match(r'"((?:[^"\\]|\\.)*)"', raw)
    return quoted[1] if quoted else raw.strip()


def read_package(tools: Path, path: Path) -> Package:
    if not path.is_file():
        raise UnreadableInput(f"{path} does not exist")
    if not zipfile.is_zipfile(path):
        raise UnreadableInput(f"{path} is not an APK")
    manifest = parse_xmltree(aapt2(tools, "dump", "xmltree", "--file", "AndroidManifest.xml", str(path))).child("manifest")
    if manifest is None:
        raise UnreadableInput(f"{path} has no manifest")
    return Package(path, manifest, manifest.child("application"))


def check_debuggable(packages: list[Package]) -> Result:
    details = []
    for package in packages:
        value = package.application.attributes.get(ANDROID + "debuggable", "false") if package.application else "false"
        if value == "true":
            details.append(f"{package.path.name} sets android:debuggable to true")
        elif value != "false":
            details.append(f"{package.path.name} sets android:debuggable from a resource ({value}), which this check cannot read")
    return Result("Not debuggable", "FAIL" if details else "PASS", details or ["no package sets android:debuggable"])


def check_cleartext(tools: Path, bases: list[Package]) -> Result:
    """Checks every base APK: bundletool makes one for each range of Android versions it targets."""
    details = []
    passed = set()
    for base in bases:
        name = base.path.name
        uses_sdk = base.manifest.child("uses-sdk")
        target = uses_sdk.attributes.get(ANDROID + "targetSdkVersion") if uses_sdk else None
        if target is None or not target.isdigit():
            details.append(f"{name} declares no target SDK, and below 28 plain HTTP is allowed by default")
        elif int(target) < 28:
            details.append(f"{name} targets SDK {target}, below 28, where plain HTTP is allowed by default")
        application = base.application.attributes if base.application else {}
        cleartext = application.get(ANDROID + "usesCleartextTraffic", "false")
        if cleartext != "false":
            details.append(f"{name} sets android:usesCleartextTraffic to {cleartext}")
        config = application.get(ANDROID + "networkSecurityConfig")
        files: list[str] = []
        if config is not None:
            if not config.startswith("@0x"):
                details.append(f"{name} names a network security config ({config}) that this check cannot find")
            else:
                files = resource_files(tools, base.path, config[1:])
                for file in files:
                    tree = parse_xmltree(aapt2(tools, "dump", "xmltree", "--file", file, str(base.path)))
                    for element in tree.walk():
                        permitted = element.attributes.get("cleartextTrafficPermitted", "false")
                        if permitted != "false":
                            details.append(f"{name}: the network security config {file} sets cleartextTrafficPermitted to {permitted} in <{element.name}>")
        passed.add(f"target SDK {target}, " + (f"and the network security config {', '.join(files)} permits none" if files else "and no network security config"))
    return Result("No plain HTTP", "FAIL" if details else "PASS", details or sorted(passed))


def resource_files(tools: Path, path: Path, resource_id: str) -> list[str]:
    """Every file of a resource, one per configuration, from `aapt2 dump resources`."""
    files: list[str] = []
    inside = False
    for line in aapt2(tools, "dump", "resources", str(path)).splitlines():
        entry = re.match(r"\s*resource (0x[0-9a-fA-F]+) ", line)
        if entry:
            inside = entry[1].lower() == resource_id.lower()
        elif inside:
            file = re.search(r"\(file\) (\S+)", line)
            if file:
                files.append(file[1])
    if not files:
        raise UnreadableInput(f"{path.name} names the network security config {resource_id}, but has no file for it")
    return files


def check_proxy(packages: list[Package], proxy_url: str | None) -> Result:
    name = "Proxy address is https"
    if proxy_url is None:
        return Result(name, "SKIP", ["no --proxy-url given: a build without a proxy address reaches no proxy"])
    url = proxy_url.strip().rstrip("/")
    parts = urlsplit(url)
    if parts.scheme != "https" or not parts.hostname:
        return Result(name, "FAIL", [f"the proxy address must be an https address with a host, but it is {url!r}"])
    code = []
    for package in packages:
        with zipfile.ZipFile(package.path) as archive:
            code += [archive.read(entry) for entry in archive.namelist() if DEX.fullmatch(entry)]
    if not code:
        return Result(name, "FAIL", ["the packages hold no app code (classes.dex)"])
    details = []
    if not any(url.encode() in dex for dex in code):
        details.append(f"the app's code does not hold {url}: the build was made with another address, or with none")
    plain = re.compile(rb"http://" + re.escape(parts.hostname.encode()) + rb"(?![A-Za-z0-9.-])", re.IGNORECASE)
    if any(plain.search(dex) for dex in code):
        details.append(f"the app's code holds a plain-http address of {parts.hostname}")
    return Result(name, "FAIL" if details else "PASS", details or [url])


def check_secrets(packages: list[Package]) -> Result:
    found: dict[tuple[str, str], int] = {}
    files = 0
    for package in packages:
        with zipfile.ZipFile(package.path) as archive:
            for entry in archive.infolist():
                if entry.is_dir():
                    continue
                files += 1
                data = archive.read(entry)
                texts = [data, *(run[0][::2] for run in UTF16_TEXT.finditer(data))]
                for text in texts:
                    for match in SECRETS.finditer(text):
                        kind = SECRET_KINDS[int(match.lastgroup[1:])][0]
                        key = (f"{package.path.name}!{entry.filename}", kind)
                        found[key] = found.get(key, 0) + 1
    details = [f"{file} holds {kind}" + (f" ({count} times)" if count > 1 else "") for (file, kind), count in sorted(found.items())]
    return Result("No secret keys", "FAIL" if details else "PASS", details or [f"{files} files searched"])


def annotate(text: str) -> str:
    # The escaping GitHub Actions expects in the message of a workflow command.
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def main(arguments: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Checks a release build of the app before it ships.")
    parser.add_argument("--proxy-url", help="the proxy address the build was made with")
    parser.add_argument("packages", nargs="+", type=Path, metavar="PACKAGE.apk")
    options = parser.parse_args(arguments)

    try:
        tools = build_tools()
        packages = [read_package(tools, path) for path in options.packages]
        apps = sorted({package.manifest.attributes.get("package", "") for package in packages})
        if len(apps) != 1:
            raise UnreadableInput(f"the APKs belong to more than one app: {', '.join(apps)}")
        bases = [package for package in packages if package.is_base]
        if not bases:
            raise UnreadableInput("none of the APKs is a base APK: give the base APKs with their splits, or one whole APK")
        results = [
            check_debuggable(packages),
            check_cleartext(tools, bases),
            check_proxy(packages, options.proxy_url),
            check_secrets(packages),
        ]
    except (UnreadableInput, zipfile.BadZipFile, OSError) as error:
        print(f"Release checks could not run: {error}", file=sys.stderr)
        if os.environ.get("GITHUB_ACTIONS") == "true":
            print(f"::error title=Release checks could not run::{annotate(str(error))}")
        return 2

    print(f"Release checks of {', '.join(package.path.name for package in packages)}")
    for result in results:
        print(f"{result.status}  {result.name}")
        for detail in result.details:
            print(f"      {detail}")
    failed = [result for result in results if result.status == "FAIL"]
    if os.environ.get("GITHUB_ACTIONS") == "true":
        for result in failed:
            print(f"::error title=Release check failed: {result.name}::{annotate('; '.join(result.details))}")
    if failed:
        print(f"Release checks failed: {', '.join(result.name for result in failed)}")
        return 1
    print("Every release check passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
