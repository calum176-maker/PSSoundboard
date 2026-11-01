"""Writes the in-app "What's new" list for this build.

Usage: whats_new.py <version> <previous release tag or ""> <output json>

The list is the titles of pull requests merged since the previous release (the
same changes the GitHub release page lists), minus build-pipeline changes that
testers don't see. Direct commits to main count too.
"""
import json
import re
import subprocess
import sys

MAX_ITEMS = 12
# Changes testers can't see in the app: the build pipeline and docs-only tweaks.
HIDDEN = re.compile(r"^(ci\b|ci/|build\b|docs?\b|readme\b|merge branch)", re.IGNORECASE)


def changes(previous_tag: str) -> list[str]:
    revs = f"{previous_tag}..HEAD" if previous_tag else "HEAD~15..HEAD"
    log = subprocess.run(
        ["git", "log", "--first-parent", "--format=%P%x1f%s%x1f%b%x1e", revs],
        capture_output=True, text=True, check=False,
    ).stdout
    items = []
    for record in log.split("\x1e"):
        parts = record.strip("\n").split("\x1f")
        if len(parts) < 3:
            continue
        parents, subject, body = parts
        if len(parents.split()) > 1:
            # A merged pull request: its title is the first line of the merge message body.
            title = next((line.strip() for line in body.splitlines() if line.strip()), subject)
        else:
            title = subject.strip()
        if title and not HIDDEN.match(title) and title not in items:
            items.append(title)
    return items[:MAX_ITEMS]


def main() -> None:
    version, previous_tag, out_path = sys.argv[1], sys.argv[2], sys.argv[3]
    items = changes(previous_tag)
    with open(out_path, "w", encoding="utf-8") as out:
        json.dump({"version": version, "items": items}, out, ensure_ascii=False, indent=2)
    print(f"What's new for {version} ({len(items)} items since {previous_tag or 'the start'}):")
    for item in items:
        print(f"  - {item}")


if __name__ == "__main__":
    main()
