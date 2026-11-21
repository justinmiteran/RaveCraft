"""Summarize Checkstyle and PMD reports as Markdown, like the "Test results" summary.

Reads build/reports/checkstyle/*.xml and build/reports/pmd/*.xml (written by ./gradlew codeQuality)
and prints a Markdown report: totals per source set, counts per rule, then every violation grouped
by file. With --annotate, also prints GitHub workflow commands so the first violations show up as
annotations on the pull request diff.

Usage:
    python tools/ci/quality_summary.py                 # Markdown on stdout
    python tools/ci/quality_summary.py --annotate      # plus ::error annotations (CI)
"""

import argparse
import collections
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

SOURCE_SETS = ("main", "client", "test", "gametest")
ANNOTATION_LIMIT = 50  # GitHub shows at most 50 annotations per job


class Violation:
    """One rule violation at a file and line."""

    def __init__(self, tool, source_set, path, line, rule, message):
        self.tool = tool
        self.source_set = source_set
        self.path = path
        self.line = line
        self.rule = rule
        self.message = " ".join(message.split())


def relative(path, root):
    """Returns the path relative to the repository root, with forward slashes."""
    try:
        return Path(path).resolve().relative_to(root).as_posix()
    except ValueError:
        return Path(path).as_posix()


def checkstyle_rule(source):
    """Turns com.puppycrawl...NestedIfDepthCheck#id into NestedIfDepth."""
    name = source.split("#")[0].rsplit(".", 1)[-1]
    return name[:-5] if name.endswith("Check") else name


def read_checkstyle(reports, root):
    """Yields the violations of every Checkstyle report."""
    for source_set in SOURCE_SETS:
        report = reports / "checkstyle" / f"{source_set}.xml"
        if not report.exists():
            continue
        for file in ET.parse(report).getroot().iter("file"):
            path = relative(file.get("name"), root)
            for error in file.iter("error"):
                yield Violation("Checkstyle", source_set, path, int(error.get("line", "0")),
                                checkstyle_rule(error.get("source", "")), error.get("message", ""))


def read_pmd(reports, root):
    """Yields the violations of every PMD report."""
    for source_set in SOURCE_SETS:
        report = reports / "pmd" / f"{source_set}.xml"
        if not report.exists():
            continue
        for file in ET.parse(report).getroot().iter():
            if not file.tag.endswith("file"):
                continue
            path = relative(file.get("name"), root)
            for violation in file:
                if violation.tag.endswith("violation"):
                    yield Violation("PMD", source_set, path, int(violation.get("beginline", "0")),
                                    violation.get("rule", ""), violation.text or "")


def escape(text):
    """Escapes a table cell."""
    return text.replace("|", "\\|").replace("<", "&lt;").replace(">", "&gt;")


def markdown(violations, ran):
    """Builds the Markdown report."""
    out = ["## Code rules", ""]
    if not ran:
        out.append(":warning: No report found: `./gradlew codeQuality` did not run.")
        return "\n".join(out)
    if not violations:
        out.append(":white_check_mark: **All code rules pass.**")
        return "\n".join(out)

    out.append(f":x: **{len(violations)} violations** in "
               f"{len({v.path for v in violations})} files.")
    out += ["", "| Source set | Checkstyle | PMD |", "|---|---:|---:|"]
    for source_set in SOURCE_SETS:
        cs = sum(1 for v in violations if v.source_set == source_set and v.tool == "Checkstyle")
        pmd = sum(1 for v in violations if v.source_set == source_set and v.tool == "PMD")
        out.append(f"| {source_set} | {cs} | {pmd} |")

    out += ["", "### By rule", "", "| Rule | Tool | Count |", "|---|---|---:|"]
    rules = collections.Counter((v.rule, v.tool) for v in violations)
    for (rule, tool), count in rules.most_common():
        out.append(f"| {rule} | {tool} | {count} |")

    out += ["", "### By file", ""]
    by_file = collections.defaultdict(list)
    for v in violations:
        by_file[v.path].append(v)
    for path in sorted(by_file, key=lambda p: (-len(by_file[p]), p)):
        items = sorted(by_file[path], key=lambda v: v.line)
        out += [f"<details><summary><code>{path}</code> ({len(items)})</summary>", "",
                "| Line | Rule | Message |", "|---:|---|---|"]
        for v in items:
            out.append(f"| {v.line} | {v.rule} | {escape(v.message)} |")
        out += ["", "</details>", ""]
    return "\n".join(out)


def annotate(violations):
    """Prints GitHub ::error commands for the first violations."""
    for v in violations[:ANNOTATION_LIMIT]:
        message = v.message.replace("%", "%25").replace("\r", "").replace("\n", " ")
        print(f"::error file={v.path},line={v.line},title={v.tool} {v.rule}::{message}")


def main():
    """Reads the reports and prints the summary."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--reports", default="build/reports", help="reports directory")
    parser.add_argument("--annotate", action="store_true", help="print GitHub annotations")
    args = parser.parse_args()

    root = Path.cwd().resolve()
    reports = Path(args.reports)
    ran = any((reports / tool).is_dir() for tool in ("checkstyle", "pmd"))
    violations = list(read_checkstyle(reports, root)) + list(read_pmd(reports, root))
    violations.sort(key=lambda v: (v.path, v.line))

    report = markdown(violations, ran)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write(report + "\n")
    else:
        sys.stdout.reconfigure(encoding="utf-8")
        print(report)
    if args.annotate:
        annotate(violations)


if __name__ == "__main__":
    main()
