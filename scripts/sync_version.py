#!/usr/bin/env python3
"""
One source of truth for what the apps tell each other.

version.json is what the console and the teacher phone download to discover a
newer build, so a stale field there silently blocks updates: the app reads
"teacher": "1.20.1" and downloads teacherDownload - which used to still say
1.20.0 and answered 404. Every URL here is now derived from the build files.

    python3 scripts/sync_version.py            rewrite version.json
    python3 scripts/sync_version.py --check    exit 1 if it is out of date
"""
import json
import re
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RELEASES = "https://github.com/asiimwe3/school-report-maker/releases/latest/download"


def read(pattern: str, path: str) -> str:
    text = (ROOT / path).read_text(encoding="utf-8")
    m = re.search(pattern, text)
    if not m:
        sys.exit(f"could not find {pattern!r} in {path}")
    return m.group(1)


def main() -> int:
    teacher = read(r'versionName\s*=\s*"([^"]+)"', "teacher-app/build.gradle.kts")
    console = read(r'val consoleVersion\s*=\s*"([^"]+)"', "desktop-admin/build.gradle.kts")

    path = ROOT / "version.json"
    current = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}

    wanted = {
        "console": console,
        "consoleDownload": f"{RELEASES}/SchoolReportMaker-{console}.msi",
        "teacher": teacher,
        "teacherDownload": f"{RELEASES}/srs-teacher-{teacher}.apk",
        "apkDownload": f"{RELEASES}/srs-teacher-{teacher}.apk",
        "released": current.get("released") or date.today().isoformat(),
        "notes": current.get("notes", ""),
    }

    check = "--check" in sys.argv
    if check:
        wrong = {k: (current.get(k), v) for k, v in wanted.items() if current.get(k) != v}
        if wrong:
            for k, (have, want) in wrong.items():
                print(f"version.json stale: {k} is {have!r}, build files say {want!r}")
            print("run: python3 scripts/sync_version.py")
            return 1
        print(f"version.json matches the builds (console {console}, teacher {teacher})")
        return 0

    # keep the file's key order stable and readable
    order = ["console", "consoleDownload", "teacher", "teacherDownload", "apkDownload", "released", "notes"]
    merged = {k: wanted[k] for k in order if k in wanted}
    for k, v in current.items():
        merged.setdefault(k, v)
    path.write_text(json.dumps(merged, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"version.json -> console {console}, teacher {teacher}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
