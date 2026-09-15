#!/usr/bin/env python3
"""Static checks that need no toolchain, no IDE and no network.

Everything the Gradle build proves (compilation, tests, plugin verification) needs the IntelliJ
Platform dependencies, which is why those stay on a developer machine. What is left is cheap enough
to run anywhere in a second, and it catches the mistakes that would otherwise only show up when the
IDE loads the plugin:

* descriptors and colour schemes are well formed XML;
* every class a descriptor points at exists on disk;
* every colour key a scheme fragment styles is one the plugin declares;
* the light and dark fragments style the same keys;
* the descriptor keeps pointing at resources that are shipped;
* no build output or token is tracked by git.

Run it with: python3 tools/check.py
"""

from __future__ import annotations

import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "src/main"
KOTLIN = MAIN / "kotlin"
RESOURCES = MAIN / "resources"

failures: list[str] = []


def check(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)


def parse(path: Path) -> ET.Element:
    try:
        return ET.parse(path).getroot()
    except ET.ParseError as error:
        failures.append(f"{path.relative_to(ROOT)} is not well formed XML: {error}")
        return ET.Element("empty")


def class_path(binary_name: str) -> Path:
    return KOTLIN / (binary_name.replace(".", "/") + ".kt")


def attributes_of(element: ET.Element) -> dict[str, str]:
    return {key.split("}")[-1]: value for key, value in element.attrib.items()}


def check_descriptors() -> None:
    plugin = RESOURCES / "META-INF/plugin.xml"
    check(plugin.is_file(), "META-INF/plugin.xml is missing")
    root = parse(plugin)

    check(
        root.find("id") is None,
        "plugin.xml declares <id>, but the id lives in build.gradle.kts (single source of truth)",
    )

    for depends in root.iter("depends"):
        config = attributes_of(depends).get("config-file")
        if config is not None:
            check(
                (RESOURCES / "META-INF" / config).is_file(),
                f"plugin.xml depends on config-file {config}, which is not shipped",
            )

    for element in root.iter():
        attributes = attributes_of(element)
        for key in ("class", "implementation", "instance", "serviceImplementation"):
            name = attributes.get(key)
            if name is None or not name.startswith("io.github.leolaurindo.tunnelvision."):
                continue
            check(class_path(name).is_file(), f"{element.tag} points at {name}, which does not exist")

    for config in RESOURCES.glob("META-INF/tunnelvision-*.xml"):
        parse(config)


def check_color_fragments() -> None:
    declared = set(re.findall(r'createTextAttributesKey\("([A-Z_]+)"\)', (KOTLIN / "io/github/leolaurindo/tunnelvision/ui/FocusColors.kt").read_text()))
    check(bool(declared), "no colour keys found in FocusColors.kt")

    fragments = sorted(RESOURCES.glob("colorSchemes/*.xml"))
    check(len(fragments) >= 2, "expected a light and a dark colour fragment")

    styled: dict[str, set[str]] = {}
    for fragment in fragments:
        # Only the keys directly under <list>: the options nested in <value> are attributes.
        keys = {attributes_of(option)["name"] for option in parse(fragment).findall("option")}
        styled[fragment.name] = keys
        for key in keys:
            check(
                key in declared,
                f"{fragment.name} styles {key}, which FocusColors.kt does not declare",
            )

    if len(styled) > 1:
        names = sorted(styled)
        for other in names[1:]:
            check(
                styled[names[0]] == styled[other],
                f"{names[0]} and {other} style different keys: {styled[names[0]] ^ styled[other]}",
            )


def check_build_configuration() -> None:
    build = (ROOT / "build.gradle.kts").read_text()
    for expected in ('id = "io.github.leolaurindo.tunnelvision"', 'sinceBuild = "252"'):
        check(expected in build, f"build.gradle.kts is missing {expected}")


def check_tracked_files() -> None:
    if not (ROOT / ".git").exists():
        return
    tracked = subprocess.run(
        ["git", "ls-files"], cwd=ROOT, capture_output=True, text=True, check=True
    ).stdout.split()
    for path in tracked:
        check(
            not re.match(r"^(build|\.gradle|\.intellijPlatform|\.kotlin)/", path),
            f"{path} is a build output and should not be tracked",
        )
    check("gradle/wrapper/gradle-wrapper.jar" in tracked, "the Gradle wrapper is not tracked")

    for path in tracked:
        if Path(path).suffix in {".kt", ".kts", ".xml", ".md", ".properties"}:
            text = (ROOT / path).read_text(errors="ignore")
            # A Marketplace token is `perm:` followed by a long base64-ish secret; the docs
            # legitimately spell out the property name and a placeholder.
            check(
                re.search(r"perm:[A-Za-z0-9+/=]{20,}", text) is None,
                f"{path} looks like it contains a Marketplace token",
            )


def main() -> int:
    check_descriptors()
    check_color_fragments()
    check_build_configuration()
    check_tracked_files()

    if failures:
        print(f"{len(failures)} check(s) failed:")
        for failure in failures:
            print(f"  - {failure}")
        return 1

    print("static checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
