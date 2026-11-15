#!/usr/bin/env python3
"""Fast, offline checks for Director X release configuration."""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[1]
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
workflow = (root / ".github/workflows/build-apk.yml").read_text(encoding="utf-8")

def one(pattern, label):
    matches = re.findall(pattern, gradle, flags=re.MULTILINE)
    if len(matches) != 1:
        raise ValueError(f"Expected exactly one {label}, got {len(matches)}")
    return matches[0]

try:
    package = one(r'^\s*applicationId\s*=\s*"([^"]+)"', "applicationId")
    version = one(r'^\s*versionName\s*=\s*"([^"]+)"', "versionName")
    code = one(r'^\s*versionCode\s*=\s*(\d+)\s*$', "versionCode")
    if package != "com.pearparinya.automovie":
        raise ValueError(f"Unexpected package: {package}")
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+)*", version):
        raise ValueError(f"Invalid versionName: {version}")
    if int(code) < 1:
        raise ValueError("versionCode must be positive")
    for required in (
        "Verify signed APK",
        "Verify signed APK metadata",
        "gh release download",
        "cmp -s",
        "ANDROID_KEYSTORE_BASE64",
        "ANDROID_KEYSTORE_PASSWORD",
    ):
        if required not in workflow:
            raise ValueError(f"Release safety step missing: {required}")
except (ValueError, OSError) as exc:
    print(f"RELEASE CONFIG FAILED: {exc}", file=sys.stderr)
    sys.exit(1)

print(f"Release config OK: {package} R{version}-B{code}")
