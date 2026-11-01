"""Writes the in-app "What's new" list for this build.

Usage: whats_new.py <version> <previous release tag or ""> <output json>

For this build and the last few releases before it, the list holds the titles of pull requests
merged in that release (the same changes the GitHub release pages list), minus build-pipeline and
docs changes that testers don't see. The app shows every release newer than the one the person last
saw, so skipping builds doesn't hide what changed. Direct commits to main count too.
"""
import json
import re
import subprocess
import sys

MAX_RELEASES = 10
MAX_ITEMS = 12
# Changes testers can't see in the app: the build pipeline and docs-only tweaks.
HIDDEN = re.compile(r"^(ci\b|ci/|build\b|docs?\b|readme\b|merge branch)", re.IGNORECASE)
RELEASE_TAG = re.compile(r"^v(\d+)\.(\d+)\.(\d+)$")


def changes(revs: str) -> list[str]:
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


def release_tags() -> list[str]:
    """Release tags (v1.0.N), oldest first."""
    tags = subprocess.run(["git", "tag", "--list", "v*"], capture_output=True, text=True, check=False).stdout.split()
    matched = [(tuple(int(n) for n in m.groups()), tag) for tag in tags if (m := RELEASE_TAG.match(tag))]
    return [tag for _, tag in sorted(matched)]


def main() -> None:
    version, previous_tag, out_path = sys.argv[1], sys.argv[2], sys.argv[3]
    tags = release_tags()
    if previous_tag and previous_tag in tags:
        tags = tags[: tags.index(previous_tag) + 1]

    # This build first, then each earlier release, newest first.
    releases = [{"version": version, "items": changes(f"{previous_tag}..HEAD" if previous_tag else "HEAD~15..HEAD")}]
    for older, newer in zip(reversed(tags[:-1]), reversed(tags[1:])):
        if len(releases) >= MAX_RELEASES:
            break
        releases.append({"version": newer.removeprefix("v"), "items": changes(f"{older}..{newer}")})
    releases = [r for r in releases if r["items"]]

    with open(out_path, "w", encoding="utf-8") as out:
        json.dump({"version": version, "releases": releases}, out, ensure_ascii=False, indent=2)
    for release in releases:
        print(f"{release['version']}:")
        for item in release["items"]:
            print(f"  - {item}")


if __name__ == "__main__":
    main()
