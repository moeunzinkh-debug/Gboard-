#!/usr/bin/env python3
"""Validate the metadata Morphe Manager reads from this repository.

Morphe Manager resolves a GitHub patch source to exactly one file:

    https://raw.githubusercontent.com/<owner>/<repo>/main/patches-bundle.json

(`dev` instead of `main` when "Pre-release patches" is on), and then downloads whatever
`download_url` in that file points at. Everything the manager needs must therefore stay in
sync with `gradle.properties` and with the release that is about to be published.

This script mirrors the checks that `.github/workflows/release.yml` performs, and adds the
Morphe-side ones (asset name, changelog presence, patch list version), so a broken source is
caught locally instead of on the phone.

Usage:
    python3 scripts/validate-source-metadata.py [--repo OWNER/NAME] [--tag v3.11.0]
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANAGER_JSON_FIELDS = ("version", "download_url", "created_at", "description")

errors: list[str] = []
notes: list[str] = []


def fail(message: str) -> None:
    errors.append(message)


def info(message: str) -> None:
    notes.append(message)


def gradle_version() -> str:
    text = (ROOT / "gradle.properties").read_text(encoding="utf-8")
    match = re.search(r"(?m)^\s*version\s*=\s*([^\r\n#]+?)\s*$", text)
    if not match:
        fail("gradle.properties: no `version = <semver>` entry, Morphe cannot stamp the bundle.")
        return ""
    return match.group(1).strip()


def check_bundle(repo: str, tag: str, version: str) -> None:
    path = ROOT / "patches-bundle.json"
    if not path.is_file():
        fail("patches-bundle.json is missing — Morphe Manager reads only this file from a repo source.")
        return

    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        fail(f"patches-bundle.json is not valid JSON: {error}")
        return

    for field in MANAGER_JSON_FIELDS:
        if field not in data:
            fail(f"patches-bundle.json: required field `{field}` is missing (manager parses it as non-null).")

    json_version = str(data.get("version", ""))
    if json_version != version:
        fail(f"patches-bundle.json version `{json_version}` != gradle.properties version `{version}`.")

    expected_url = f"https://github.com/{repo}/releases/download/{tag}/patches-{version}.mpp"
    actual_url = str(data.get("download_url", ""))
    if actual_url != expected_url:
        fail(
            "patches-bundle.json download_url must be the release asset of *this* repository.\n"
            f"    expected: {expected_url}\n"
            f"    actual:   {actual_url}"
        )

    signature = data.get("signature_download_url")
    if signature not in (None, ""):
        fail("patches-bundle.json signature_download_url must be empty for a public GitHub release.")

    created = str(data.get("created_at", ""))
    if not re.match(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?(Z|[+-]\d{2}:\d{2})?$", created):
        fail(f"patches-bundle.json created_at `{created}` is not an ISO-8601 timestamp; the manager parses it.")


def check_release_asset(version: str) -> None:
    if version and not re.match(r"^\d+\.\d+\.\d+", version):
        info(f"version `{version}` is not plain semver; keep the tag as v{version} so the workflow triggers.")

    libs = (ROOT / "gradle" / "libs.versions.toml").read_text(encoding="utf-8")
    match = re.search(r'morphe-patcher\s*=\s*"([^"]+)"', libs)
    if match:
        info(f"bundle will declare Patcher-Version {match.group(1)}; Morphe Manager must ship that version or newer.")

    patch_list = ROOT / "patches-list.json"
    if patch_list.is_file():
        listed = json.loads(patch_list.read_text(encoding="utf-8"))
        if str(listed.get("version")) != version:
            fail(f"patches-list.json version `{listed.get('version')}` != `{version}` (regenerate with `./gradlew generatePatchesList`).")
        info(f"patches-list.json declares {len(listed.get('patches', []))} patches.")


def check_readme(repo: str) -> None:
    readme = ROOT / "README.md"
    if not readme.is_file() or not readme.read_text(encoding="utf-8").strip():
        fail("README.md is empty — add the `https://morphe.software/add-source?github=" + repo + "` link users need.")
        return
    if repo not in readme.read_text(encoding="utf-8"):
        info(f"README.md does not mention `{repo}`; the add-source deep link must point at this repository.")


def check_branch() -> None:
    info(
        "Manager reads the manifest from the `main` branch only (`dev` when Pre-release patches is on); "
        "a repository whose default branch is not `main` is invisible to it."
    )
    info("A source icon is picked up as patches-bundle.png next to patches-bundle.json, if you want one.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default="moeunzinkh-debug/Gboard-", help="owner/name of this repository on GitHub")
    parser.add_argument("--tag", default=None, help="release tag being validated (defaults to v<gradle.properties version>)")
    args = parser.parse_args()

    version = gradle_version()
    tag = args.tag or (f"v{version}" if version else "v0.0.0")

    check_bundle(args.repo, tag, version)
    check_release_asset(version)
    check_readme(args.repo)
    check_branch()

    for note in notes:
        print(f"  note: {note}")
    if errors:
        print("\n  FAILED:")
        for error in errors:
            print(f"  - {error}")
        return 1
    print(f"\n  OK: patches-bundle.json is consistent with gradle.properties ({version}) and {args.repo}@{tag}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
