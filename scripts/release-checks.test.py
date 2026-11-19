#!/usr/bin/env python3
"""Tests of release-checks.py against packages made here with the Android SDK's own tools.

One package is made like a release build and must pass. The others are deliberately bad and must
fail, naming the check and the file, and never printing a secret. Each package is a real APK: aapt2
compiles its manifest and resources, and javac and d8 make its code.

    python3 scripts/release-checks.test.py

Needs what release-checks.py needs, plus javac and an android.jar under ANDROID_HOME/platforms.
"""

from __future__ import annotations

import importlib.util
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

CHECKER = Path(__file__).with_name("release-checks.py")
# The checker's file name has a dash, so it is loaded by path; its finder of aapt2 is reused here.
# No bytecode cache is written next to it.
sys.dont_write_bytecode = True
_spec = importlib.util.spec_from_file_location("release_checks", CHECKER)
release_checks = importlib.util.module_from_spec(_spec)
sys.modules["release_checks"] = release_checks
_spec.loader.exec_module(release_checks)

PROXY = "https://split-vibe-proxy.example.test"

# Made up at run time from pieces, so that no file in the repository looks like a key to the secret
# scan, while the packages hold the whole strings.
FAKE_BODY = "Fa1keKeyBody" * 8
SK_KEY = "sk" + "_" + FAKE_BODY[:32]
ANTHROPIC_KEY = "sk-" + "ant-" + "api03-" + FAKE_BODY
PRIVATE_KEY_JSON = '{"type": "service_account", "private_key": "-----BEGIN ' + 'PRIVATE KEY-----\\n' + FAKE_BODY + '\\n-----END ' + 'PRIVATE KEY-----\\n"}'
CLIENT_SECRET = "GOCSPX" + "-" + FAKE_BODY[:28]
# Public keys that every copy of the app carries, and a certificate, which is public too.
PUBLIC_STRINGS = ("goog" + "_" + FAKE_BODY[:30], "AIza" + "Sy" + FAKE_BODY[:33])
CERTIFICATE = "-----BEGIN " + "CERTIFICATE-----\n" + FAKE_BODY + "\n-----END " + "CERTIFICATE-----\n"

RELEASE_NETWORK_CONFIG = """<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config>
        <domain includeSubdomains="true">example.test</domain>
    </domain-config>
</network-security-config>"""
DEBUG_NETWORK_CONFIG = """<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">127.0.0.1</domain>
    </domain-config>
</network-security-config>"""


def newest(paths: list[Path]) -> Path:
    return max(paths, key=lambda path: tuple(int(number) for number in re.findall(r"\d+", str(path))))


class ReleaseChecksTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.tools = release_checks.build_tools()
        sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or cls.tools.parent.parent)
        jars = list((sdk / "platforms").glob("android-*/android.jar"))
        if not jars:
            raise RuntimeError(f"no android.jar under {sdk / 'platforms'}")
        cls.android_jar = newest(jars)
        cls.work = Path(tempfile.mkdtemp(prefix="release-checks-test-"))

    @classmethod
    def tearDownClass(cls) -> None:
        shutil.rmtree(cls.work, ignore_errors=True)

    def run_tool(self, *command: str | Path) -> None:
        result = subprocess.run([str(part) for part in command], capture_output=True, text=True, check=False)
        self.assertEqual(result.returncode, 0, f"{command[0]} failed: {result.stderr}{result.stdout}")

    def package(
        self,
        name: str,
        application: str = "",
        target_sdk: int = 36,
        network_config: str | None = None,
        code: tuple[str, ...] = (),
        files: dict[str, bytes] | None = None,
        split: str | None = None,
        app: str = "com.example.fixture",
    ) -> Path:
        """Makes an APK with the given manifest settings, code strings and extra files."""
        root = self.work / name
        (root / "res" / "xml").mkdir(parents=True)
        if network_config is not None:
            (root / "res" / "xml" / "network_config.xml").write_text(network_config)
            application += ' android:networkSecurityConfig="@xml/network_config"'
        if not code:
            application += ' android:hasCode="false"'
        split_attribute = f' split="{split}"' if split else ""
        (root / "AndroidManifest.xml").write_text(
            f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{app}"'
            f'{split_attribute}>\n'
            f'    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="{target_sdk}" />\n'
            f"    <application{application} />\n"
            "</manifest>\n"
        )
        apk = self.work / f"{name}.apk"
        link = [self.tools / "aapt2", "link", "-o", apk, "-I", self.android_jar, "--manifest", root / "AndroidManifest.xml"]
        if network_config is not None:
            self.run_tool(self.tools / "aapt2", "compile", "-o", root / "resources.zip", "--dir", root / "res")
            link.append(root / "resources.zip")
        self.run_tool(*link)
        if code:
            # One class whose constants put each string into the dex file's string table.
            literals = ", ".join('"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"' for text in code)
            (root / "java").mkdir()
            (root / "java" / "Strings.java").write_text(f"final class Strings {{ static final String[] ALL = {{{literals}}}; }}\n")
            self.run_tool("javac", "--release", "17", "-d", root / "classes", root / "java" / "Strings.java")
            (root / "dex").mkdir()
            self.run_tool(self.tools / "d8", "--min-api", "26", "--output", root / "dex", root / "classes" / "Strings.class")
        with zipfile.ZipFile(apk, "a") as archive:
            if code:
                archive.write(root / "dex" / "classes.dex", "classes.dex")
            for entry, content in (files or {}).items():
                archive.writestr(entry, content)
        return apk

    def checks(self, *packages: Path, proxy_url: str | None = None) -> subprocess.CompletedProcess[str]:
        arguments = [sys.executable, "-I", str(CHECKER)]
        if proxy_url is not None:
            arguments += ["--proxy-url", proxy_url]
        environment = {**os.environ, "ANDROID_BUILD_TOOLS": str(self.tools), "GITHUB_ACTIONS": "false"}
        return subprocess.run(arguments + [str(package) for package in packages], capture_output=True, text=True, check=False, env=environment)

    def assertSecretsHidden(self, output: str) -> None:
        for secret in (FAKE_BODY[:24], SK_KEY, ANTHROPIC_KEY, CLIENT_SECRET):
            self.assertNotIn(secret, output)

    def test_a_package_made_like_the_release_passes(self) -> None:
        apk = self.package(
            "release",
            network_config=RELEASE_NETWORK_CONFIG,
            code=(PROXY, PROXY + "/v1/session", "http://schemas.android.com/apk/res/android", *PUBLIC_STRINGS),
            files={"assets/ca.pem": CERTIFICATE.encode(), "res/raw/keys.txt": PUBLIC_STRINGS[0].encode("utf-16-le")},
        )
        result = self.checks(apk, proxy_url=PROXY + "/")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        for check in ("Not debuggable", "No plain HTTP", "Proxy address is https", "No secret keys"):
            self.assertIn(f"PASS  {check}", result.stdout)
        self.assertIn("Every release check passed.", result.stdout)

    def test_a_deliberately_bad_package_fails_every_check_and_hides_the_secrets(self) -> None:
        apk = self.package(
            "bad",
            application=' android:debuggable="true" android:usesCleartextTraffic="true"',
            network_config=DEBUG_NETWORK_CONFIG,
            code=("http://split-vibe-proxy.example.test", SK_KEY),
            files={
                "assets/service-account.json": PRIVATE_KEY_JSON.encode(),
                # UTF-16, as a compiled resource may store text.
                "res/raw/settings.bin": ANTHROPIC_KEY.encode("utf-16-le"),
                "lib/arm64-v8a/libsettings.so": b"\x7fELF\x00" + CLIENT_SECRET.encode() + b"\x00",
            },
        )
        result = self.checks(apk, proxy_url=PROXY)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        for check in ("Not debuggable", "No plain HTTP", "Proxy address is https", "No secret keys"):
            self.assertIn(f"FAIL  {check}", result.stdout)
        for detail in (
            "bad.apk sets android:debuggable to true",
            "bad.apk sets android:usesCleartextTraffic to true",
            "sets cleartextTrafficPermitted to true in <domain-config>",
            f"the app's code does not hold {PROXY}",
            "the app's code holds a plain-http address of split-vibe-proxy.example.test",
            "bad.apk!classes.dex holds a secret API key (sk_)",
            "bad.apk!assets/service-account.json holds a private key",
            "bad.apk!res/raw/settings.bin holds an Anthropic API key (sk-ant-)",
            "bad.apk!lib/arm64-v8a/libsettings.so holds a Google OAuth client secret (GOCSPX-)",
        ):
            self.assertIn(detail, result.stdout)
        self.assertIn("Release checks failed: Not debuggable, No plain HTTP, Proxy address is https, No secret keys", result.stdout)
        self.assertSecretsHidden(result.stdout + result.stderr)

    def test_a_target_below_28_allows_plain_http_by_default(self) -> None:
        apk = self.package("old-target", target_sdk=27, code=(PROXY,))
        result = self.checks(apk, proxy_url=PROXY)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("old-target.apk targets SDK 27, below 28, where plain HTTP is allowed by default", result.stdout)

    def test_the_proxy_address_must_be_https(self) -> None:
        apk = self.package("plain-address", code=("http://split-vibe-proxy.example.test",))
        result = self.checks(apk, proxy_url="http://split-vibe-proxy.example.test")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("the proxy address must be an https address with a host, but it is 'http://split-vibe-proxy.example.test'", result.stdout)
        result = self.checks(apk, proxy_url="")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("FAIL  Proxy address is https", result.stdout)

    def test_without_a_proxy_address_that_check_is_skipped(self) -> None:
        apk = self.package("no-address", code=("goog_settings",))
        result = self.checks(apk)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("SKIP  Proxy address is https", result.stdout)

    def test_the_bases_and_splits_of_a_bundle_are_checked_together(self) -> None:
        # bundletool makes a base APK for each range of Android versions, such as base-master.apk
        # and base-master_2.apk, each with its own splits; the code may be in any of them.
        base = self.package("base-master", code=("goog_settings",))
        other_base = self.package("base-master_2", code=(PROXY,), network_config=DEBUG_NETWORK_CONFIG)
        split = self.package(
            "base-arm64_v8a",
            split="config.arm64_v8a",
            files={"lib/arm64-v8a/libfixture.so": b"\x7fELF\x00" + SK_KEY.encode() + b"\x00"},
        )
        result = self.checks(base, other_base, split, proxy_url=PROXY)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("PASS  Proxy address is https", result.stdout)
        self.assertIn("base-master_2.apk: the network security config res/xml/network_config.xml sets cleartextTrafficPermitted to true", result.stdout)
        self.assertIn("base-arm64_v8a.apk!lib/arm64-v8a/libfixture.so holds a secret API key (sk_)", result.stdout)
        self.assertSecretsHidden(result.stdout + result.stderr)
        # Splits alone, or the APKs of two apps at once, are not something the checks can judge.
        result = self.checks(split, proxy_url=PROXY)
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("none of the APKs is a base APK", result.stderr)
        result = self.checks(base, self.package("other-app", code=(PROXY,), app="com.example.other"), proxy_url=PROXY)
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("the APKs belong to more than one app: com.example.fixture, com.example.other", result.stderr)

    def test_a_file_that_is_not_an_apk_cannot_be_checked(self) -> None:
        text = self.work / "notes.apk"
        text.write_text("not a package")
        result = self.checks(text)
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("notes.apk is not an APK", result.stderr)
        result = self.checks(self.work / "missing.apk")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("missing.apk does not exist", result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
