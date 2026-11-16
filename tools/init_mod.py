"""Turns this template into a new mod: renames the mod id, the Java package, the class prefix and the display name.

Usage (from the root of a COPY of the template):
    python tools/init_mod.py --modid mymod --package com.me.mymod --name "My Mod" [--prefix MyMod]

--modid    lowercase id ([a-z][a-z0-9_]*), used for assets/<modid>, the jar name and Identifiers
--package  Java package of the mod (its parent becomes maven_group)
--name     display name (fabric.mod.json, log line)
--prefix   Java class prefix (ExampleMod -> MyMod); derived from --name by default
Run it once, right after copying; then delete it if you like.
"""
from __future__ import annotations

import argparse
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OLD_ID, OLD_PKG, OLD_PREFIX, OLD_NAME = "examplemod", "com.example.examplemod", "ExampleMod", "Example Mod"
TEXT = {".java", ".json", ".gradle", ".properties", ".md", ".py", ".yml", ".mcmeta"}
SKIP = {".git", ".gradle", "build", "run", "node_modules", ".idea"}


def files(root: Path):
    for path in root.rglob("*"):
        if path.is_file() and not SKIP.intersection(path.relative_to(root).parts) and path.suffix in TEXT:
            yield path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--modid", required=True)
    ap.add_argument("--package", required=True)
    ap.add_argument("--name", required=True)
    ap.add_argument("--prefix")
    a = ap.parse_args()
    if not re.fullmatch(r"[a-z][a-z0-9_]{1,63}", a.modid):
        raise SystemExit("--modid must match [a-z][a-z0-9_]{1,63}")
    if not re.fullmatch(r"[a-z_][a-z0-9_]*(\.[a-z_][a-z0-9_]*)+", a.package):
        raise SystemExit("--package must look like com.me.mymod")
    prefix = a.prefix or re.sub(r"[^A-Za-z0-9]", "", a.name.title())
    if not re.fullmatch(r"[A-Z][A-Za-z0-9]*", prefix):
        raise SystemExit("--prefix must be a Java class name, e.g. MyMod")
    if not (ROOT / "src/main/java" / OLD_PKG.replace(".", "/")).exists():
        raise SystemExit("already initialised (com/example/examplemod not found)")

    this = Path(__file__).resolve()
    old_group = OLD_PKG.rsplit(".", 1)[0]
    group = a.package.rsplit(".", 1)[0]
    for path in files(ROOT):
        if path.resolve() == this:
            continue
        text = path.read_text(encoding="utf-8")
        new = (text.replace(OLD_PKG, a.package)
               .replace(OLD_PKG.replace(".", "/"), a.package.replace(".", "/"))
               .replace(OLD_PREFIX, prefix)
               .replace(OLD_NAME, a.name)
               .replace(OLD_ID, a.modid))
        if path.name == "gradle.properties":
            new = new.replace(f"maven_group={old_group}", f"maven_group={group}")
        if new != text:
            path.write_text(new, encoding="utf-8")

    # Move the Java package directories, in every source set.
    for src in ("main", "client", "test", "gametest"):
        base = ROOT / "src" / src / "java"
        old = base / OLD_PKG.replace(".", "/")
        if old.exists():
            dest = base / a.package.replace(".", "/")
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(old), str(dest))
            parent = old.parent
            while parent != base and not any(parent.iterdir()):
                parent.rmdir()
                parent = parent.parent
    # Rename files named after the prefix, and the assets namespace.
    for path in list(ROOT.rglob(f"{OLD_PREFIX}*")):
        if not SKIP.intersection(path.relative_to(ROOT).parts):
            path.rename(path.with_name(path.name.replace(OLD_PREFIX, prefix)))
    assets = ROOT / "src/main/resources/assets" / OLD_ID
    if assets.exists():
        assets.rename(assets.with_name(a.modid))
    print(f"Initialised {a.name} ({a.modid}, {a.package}, {prefix}*)")
    print("Next: python tools/textures/gen_textures.py && ./gradlew runDatagen && ./gradlew build")


if __name__ == "__main__":
    main()
