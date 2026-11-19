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
- no file holds a secret key: a private key, also an encrypted one or a PGP key block, an Anthropic
  API key, a secret API key that starts with sk_ (RevenueCat's, for one), or a Google OAuth client
  secret, whether as text, as UTF-16 or in base64; and no file is a Java (JKS) or PKCS#12 key store.
  Zip files inside the packages are searched too. The public keys every copy of the app carries,
  such as RevenueCat's goog_ key and Firebase's API key, are fine. A finding names the file and the
  kind of key, never the key itself;
- every 64-bit native library loads on phones with 16 KB memory pages: each of its loadable
  (PT_LOAD) segments is aligned to 16 KB or more. 32-bit libraries only ever run with 4 KB pages.

Android reads the manifest's attributes by their resource ID and those of the network security
config by their name alone, whatever namespace they are written in, and so do these checks.

The packages are read with aapt2 from the Android SDK's build tools: ANDROID_BUILD_TOOLS names that
directory, otherwise the newest one under ANDROID_HOME or ANDROID_SDK_ROOT is used.

Exit status: 0 when every check passes, 1 when a check fails, 2 when the packages cannot be read.
"""

from __future__ import annotations

import argparse
import base64
import binascii
import io
import os
import re
import struct
import subprocess
import sys
import zipfile
from collections.abc import Iterator
from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import urlsplit

# The resource IDs of the framework attributes these checks read, as aapt2 prints them.
DEBUGGABLE = "0x0101000f"
USES_CLEARTEXT_TRAFFIC = "0x010104ec"
NETWORK_SECURITY_CONFIG = "0x01010527"
TARGET_SDK_VERSION = "0x01010270"

# A line break, as text or escaped (\n) as in a JSON key file.
_BREAK = rb"(?:\r?\n|\\r?\\n)"
# Each kind of secret, as bytes. The fixed start of a key (sk_, -----BEGIN ...) is not secret, so a
# finding may name it; what follows it never reaches the output.
SECRET_KINDS = (
    # Header lines such as Proc-Type and DEK-Info of an encrypted key, or Comment of a PGP key block,
    # may come between the first line and the body.
    (
        "a private key",
        rb"-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY(?: BLOCK)?-----(?:\s|\\[rn])*"
        rb"(?:[A-Za-z][A-Za-z0-9-]*:[^\r\n\\]*" + _BREAK + rb"(?:\s|\\[rn])*)*"
        rb"[A-Za-z0-9+/=\s\\]{64,}",
    ),
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
# A run of base64, in its standard or URL-safe alphabet, long enough to hold a key once decoded.
BASE64_TEXT = re.compile(rb"[A-Za-z0-9+/_-]{32,}")
URL_SAFE_TO_STANDARD = bytes.maketrans(b"-_", b"+/")
# The length of a DER element, in its short, long or indefinite form.
_DER_LENGTH = rb"(?:[\x00-\x7f]|\x80|\x81[\s\S]|\x82[\s\S]{2}|\x83[\s\S]{3}|\x84[\s\S]{4})"
# Key stores, by the bytes they begin with. A PKCS#12 file is a DER sequence of version 3 and then
# PKCS#7 data or signed data.
KEY_STORES = (
    ("a key store (JKS)", re.compile(rb"\A\xfe\xed\xfe\xed")),
    (
        "a key store (PKCS#12)",
        re.compile(rb"\A\x30" + _DER_LENGTH + rb"\x02\x01\x03\x30" + _DER_LENGTH + rb"\x06\x09\x2a\x86\x48\x86\xf7\x0d\x01\x07[\x01\x02]"),
    ),
)
# How deep zip files inside zip files are searched.
NESTED_ZIPS = 3
DEX = re.compile(r"classes\d*\.dex")
PT_LOAD = 1
PAGE_16_KB = 16384


class UnreadableInput(Exception):
    """A package or a tool that the checks need could not be read or run."""


@dataclass
class Attribute:
    """An attribute of a compiled XML element as aapt2 prints it."""

    namespace: str  # the namespace's URI, empty for an attribute without one
    name: str  # the local name
    resource_id: str | None  # such as 0x0101000f, when aapt2 linked the attribute to a resource
    value: str


@dataclass
class Element:
    """An element of a compiled XML file as aapt2 prints it."""

    name: str
    attributes: list[Attribute] = field(default_factory=list)
    children: list[Element] = field(default_factory=list)

    def child(self, name: str) -> Element | None:
        return next((element for element in self.children if element.name == name), None)

    def walk(self):
        yield self
        for element in self.children:
            yield from element.walk()

    def plain(self, name: str) -> str | None:
        """The attribute without a namespace called [name], such as the manifest's package."""
        return next((attribute.value for attribute in self.attributes if not attribute.namespace and attribute.name == name), None)

    def by_id(self, resource_id: str) -> list[str]:
        """Every value of the attribute that Android reads by [resource_id], whatever its namespace."""
        return [attribute.value for attribute in self.attributes if attribute.resource_id == resource_id]

    def by_name(self, name: str) -> list[str]:
        """Every value of an attribute called [name], in any namespace or none."""
        return [attribute.value for attribute in self.attributes if attribute.name == name]


@dataclass
class Package:
    path: Path
    manifest: Element
    application: Element | None

    @property
    def is_base(self) -> bool:
        # Split APKs name their split in the manifest; the base APK and a whole-app APK do not.
        return self.manifest.plain("split") is None


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


# NAMESPACE:NAME(0xID)=VALUE, where only the name and the value are always there. A namespace URI
# may hold colons and equals signs, but a local name holds neither, so the name is what follows
# the last colon after which the rest of the line still reads as an attribute.
ATTRIBUTE = re.compile(r"(?:(?P<namespace>.*):)?(?P<name>[^:=()\s]+)(?:\((?P<id>0x[0-9a-fA-F]+)\))?=(?P<value>.*)")


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
            attribute = ATTRIBUTE.fullmatch(rest)
            if attribute:
                resource_id = attribute["id"].lower() if attribute["id"] else None
                parent.attributes.append(Attribute(attribute["namespace"] or "", attribute["name"], resource_id, attribute_value(attribute["value"])))
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
        for value in package.application.by_id(DEBUGGABLE) if package.application else []:
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
        targets = uses_sdk.by_id(TARGET_SDK_VERSION) if uses_sdk else []
        if not targets or not all(target.isdigit() for target in targets):
            details.append(f"{name} declares no target SDK, and below 28 plain HTTP is allowed by default")
        elif min(int(target) for target in targets) < 28:
            details.append(f"{name} targets SDK {min(int(target) for target in targets)}, below 28, where plain HTTP is allowed by default")
        application = base.application or Element("application")
        for cleartext in application.by_id(USES_CLEARTEXT_TRAFFIC):
            if cleartext != "false":
                details.append(f"{name} sets android:usesCleartextTraffic to {cleartext}")
        files: list[str] = []
        for config in application.by_id(NETWORK_SECURITY_CONFIG):
            if not config.startswith("@0x"):
                details.append(f"{name} names a network security config ({config}) that this check cannot find")
                continue
            for file in resource_files(tools, base.path, config[1:]):
                files.append(file)
                tree = parse_xmltree(aapt2(tools, "dump", "xmltree", "--file", file, str(base.path)))
                for element in tree.walk():
                    for permitted in element.by_name("cleartextTrafficPermitted"):
                        if permitted != "false":
                            details.append(f"{name}: the network security config {file} sets cleartextTrafficPermitted to {permitted} in <{element.name}>")
        passed.add(f"target SDK {', '.join(targets)}, " + (f"and the network security config {', '.join(files)} permits none" if files else "and no network security config"))
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


def base64_decoded(text: bytes) -> Iterator[bytes]:
    """What each run of base64 in [text] decodes to, read from each of the four places where its first
    whole group of four characters may begin."""
    for run in BASE64_TEXT.finditer(text):
        characters = run[0].translate(URL_SAFE_TO_STANDARD)
        for start in range(4):
            part = characters[start:]
            # One character left over decodes to nothing; two or three are padded as base64 is.
            part = part[: len(part) - 1] if len(part) % 4 == 1 else part
            try:
                yield base64.b64decode(part + b"=" * (-len(part) % 4))
            except binascii.Error:
                continue


def secrets_in(data: bytes) -> Iterator[str]:
    """The kind of each secret in a file: the file as a key store, or a key in it as text, as UTF-16
    or in base64."""
    for kind, start in KEY_STORES:
        if start.match(data):
            yield kind
    for text in (data, *(run[0][::2] for run in UTF16_TEXT.finditer(data))):
        for match in SECRETS.finditer(text):
            yield SECRET_KINDS[int(match.lastgroup[1:])][0]
        for decoded in base64_decoded(text):
            for match in SECRETS.finditer(decoded):
                yield SECRET_KINDS[int(match.lastgroup[1:])][0] + " in base64"


def search_archive(archive: zipfile.ZipFile, prefix: str, found: dict[tuple[str, str], int], depth: int = 0) -> int:
    """Searches every file of [archive] for secrets, and the files of the zip files it holds, adding
    each finding to [found] under the file's name after [prefix]. Returns how many files it searched."""
    files = 0
    for entry in archive.infolist():
        if entry.is_dir():
            continue
        name = f"{prefix}!{entry.filename}"
        files += 1
        try:
            data = archive.read(entry)
        except (RuntimeError, NotImplementedError):
            # An encrypted entry, or one compressed in a way Python cannot read, could hide anything.
            key = (name, "content this check cannot read (encrypted, or compressed in an unknown way)")
            found[key] = found.get(key, 0) + 1
            continue
        for kind in secrets_in(data):
            found[(name, kind)] = found.get((name, kind), 0) + 1
        if depth < NESTED_ZIPS and data.startswith(b"PK\x03\x04"):
            try:
                with zipfile.ZipFile(io.BytesIO(data)) as inner:
                    files += search_archive(inner, name, found, depth + 1)
            except zipfile.BadZipFile:
                # Not a zip file after all: its bytes were searched as they are.
                pass
    return files


def check_secrets(packages: list[Package]) -> Result:
    found: dict[tuple[str, str], int] = {}
    files = 0
    for package in packages:
        with zipfile.ZipFile(package.path) as archive:
            files += search_archive(archive, package.path.name, found)
    details = [f"{file} holds {kind}" + (f" ({count} times)" if count > 1 else "") for (file, kind), count in sorted(found.items())]
    return Result("No secret keys", "FAIL" if details else "PASS", details or [f"{files} files searched"])


def load_alignments(data: bytes) -> list[int] | None:
    """The alignment of each loadable (PT_LOAD) segment of a 64-bit ELF file, or None for a 32-bit
    one. Raises ValueError when the file cannot be read as ELF."""
    if len(data) < 64 or not data.startswith(b"\x7fELF"):
        raise ValueError("it is not an ELF file")
    if data[4] == 1:
        return None
    order = {1: "<", 2: ">"}.get(data[5])
    if data[4] != 2 or order is None:
        raise ValueError("its ELF header names no known class or byte order")
    (offset,) = struct.unpack_from(order + "Q", data, 32)
    size, count = struct.unpack_from(order + "HH", data, 54)
    if size < 56 or offset + size * count > len(data):
        raise ValueError("its program headers lie outside the file")
    alignments = []
    for index in range(count):
        start = offset + index * size
        (kind,) = struct.unpack_from(order + "I", data, start)
        if kind == PT_LOAD:
            alignments.append(struct.unpack_from(order + "Q", data, start + 48)[0])
    return alignments


def check_page_alignment(packages: list[Package]) -> Result:
    """Phones with 16 KB memory pages, which run only 64-bit code, load a library only when every
    segment they map is aligned to 16 KB or more."""
    details = []
    libraries = 0
    for package in packages:
        with zipfile.ZipFile(package.path) as archive:
            for entry in archive.infolist():
                if entry.is_dir() or not entry.filename.endswith(".so"):
                    continue
                name = f"{package.path.name}!{entry.filename}"
                try:
                    alignments = load_alignments(archive.read(entry))
                except ValueError as error:
                    details.append(f"{name} cannot be read as a native library: {error}")
                    continue
                if alignments is None:
                    continue
                libraries += 1
                if not alignments:
                    details.append(f"{name} has no loadable segment")
                elif min(alignments) < PAGE_16_KB:
                    details.append(f"{name} has a loadable segment aligned to {min(alignments)} bytes, below the {PAGE_16_KB} that 16 KB pages need")
    passed = f"{libraries} 64-bit native libraries, every loadable segment aligned to 16 KB or more" if libraries else "no 64-bit native library"
    return Result("16 KB page alignment", "FAIL" if details else "PASS", details or [passed])


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
        apps = sorted({package.manifest.plain("package") or "" for package in packages})
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
            check_page_alignment(packages),
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
