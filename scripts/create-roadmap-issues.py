#!/usr/bin/env python3
"""
Create GitHub (and optionally GitLab) issues from ROADMAP.md.
Idempotent: skips titles that already exist (open or closed).

GitHub:
  GITHUB_TOKEN (or GH_TOKEN) must have repo scope (Actions uses GITHUB_TOKEN automatically).
  GITHUB_REPOSITORY defaults to abinsabu2/GrammPlayer or env.

GitLab (optional mirror):
  Export GITLAB_TOKEN + GITLAB_PROJECT (e.g. "123" or "group/project" URL-encoded)
  + GITLAB_URL (default https://gitlab.com)

Usage:
  GITHUB_TOKEN=ghp_xxx python3 scripts/create-roadmap-issues.py
  GITHUB_TOKEN=ghp_xxx python3 scripts/create-roadmap-issues.py --dry-run
  GITHUB_TOKEN=xxx GITLAB_TOKEN=yyy GITLAB_PROJECT=group%2Fproj python3 scripts/create-roadmap-issues.py
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ROADMAP = ROOT / "ROADMAP.md"
REPO_DEFAULT = "abinsabu2/GrammPlayer"

# title -> (section, description, label_extra)
ITEMS = [
    # Quick wins
    ("Progress bar on cards", "Quick wins", "show `HistoryStore` bookmark (`savedPositionMs`) on Continue Watching / History grids. Leanback card `ProgressBar` overlay, no new deps.", "quick-win"),
    ("Auto-play next", "Quick wins", "queue next `MediaMessage` in same chat on `VlcPlaybackTracker` finish (end-clear 5s). Uses existing `MediaMessageDataProvider` order.", "quick-win"),
    ("Global Search", "Quick wins", "`Leanback SearchFragment` over `JsonSeedStore` + TMDB search + TDLib `searchMessages`. SearchOrb already in MainFragment chrome.", "quick-win"),
    ("Watchlist / Favorites", "Quick wins", "per-login JSON like `HistoryStore` (heart on `MediaDetailsActivity`), new Dashboard row. Reuses `HistoryStore` pattern (`filesDir/history/`).", "quick-win"),
    ("Filters", "Quick wins", "by `ReleaseTitleParser` (year / 1080p/4K / WEB-DL) + TMDB genre; chip row on `MessageGridFragment`.", "quick-win"),
    ("Trailer row", "Quick wins", "expose `PosterFetcher.trailerKey()` → YouTube intent grid on Details. Zero new API.", "quick-win"),
    ("Subtitle picker", "Quick wins", "pass `EXTRA_SUBTITLES` to VLC, auto-fetch via OpenSubtitles + TMDB `imdb_id`. Uses existing `PlayerHelper.play()`.", "quick-win"),
    # Medium
    ("Up Next / Recently Added", "Medium", "Dashboard rows: unwatched + latest by `visitedAt` from `HistoryStore` vs `JsonSeedStore`.", "medium"),
    ("Cast explorer", "Medium", "tap cast chip → TMDB person → their movies grid. Uses `PosterFetcher` cache.", "medium"),
    ("Voice search", "Medium", "`SpeechRecognizer` → same search backend as Global Search. Native TV feature.", "medium"),
    ("Profiles switcher", "Medium", "reuse `history_{login}.json` per-login, switch `UserSession` without logout. UI in Dashboard.", "medium"),
    ("Download manager screen", "Medium", "full `ActiveDownloadManager` + `DownloadProgressTracker` UI (pause/cancel all, storage bar). Reuses dashboard download row logic.", "medium"),
    ("Smart categories", "Medium", "auto rows by TMDB genre/year from `PosterFetcher` cache. No backend.", "medium"),
    ("Parental PIN", "Medium", "lock chats by `Chat.id` via `SettingsDataStore`, gate in `ChatsGridFragment`.", "medium"),
    # Big bets
    ("Recommendations", "Big bets", "\"Because you watched X\" via TMDB `similar`/`recommendations` from top 3 `HistoryStore`.", "big-bet"),
    ("Intro / Skip chapter", "Big bets", "skip 10s / Intro via `VLC EXTRA_START_TIME`. Parse silence + TMDB runtime.", "big-bet"),
    ("Stats", "Big bets", "time watched, top studio, heatmap from `HistoryStore` JSON.", "big-bet"),
]

AREA_MAP = {
    "Progress bar on cards": "Dashboard",
    "Auto-play next": "Playback",
    "Global Search": "TMDB",
    "Watchlist / Favorites": "Dashboard",
    "Filters": "Other",
    "Trailer row": "TMDB",
    "Subtitle picker": "Playback",
    "Up Next / Recently Added": "Dashboard",
    "Cast explorer": "TMDB",
    "Voice search": "Other",
    "Profiles switcher": "Telegram / Sync",
    "Download manager screen": "Storage",
    "Smart categories": "TMDB",
    "Parental PIN": "Other",
    "Recommendations": "TMDB",
    "Intro / Skip chapter": "Playback",
    "Stats": "Other",
}


def api_request(url: str, token: str, method: str = "GET", data: dict | None = None, headers: dict | None = None) -> tuple[int, dict | list | str]:
    req_headers = {"Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"}
    if token:
        req_headers["Authorization"] = f"Bearer {token}"
    if headers:
        req_headers.update(headers)
    body = json.dumps(data).encode() if data is not None else None
    if body is not None:
        req_headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=body, headers=req_headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            txt = resp.read().decode()
            return resp.status, json.loads(txt) if txt else {}
    except urllib.error.HTTPError as e:
        txt = e.read().decode() if e.fp else ""
        try:
            payload = json.loads(txt) if txt else txt
        except Exception:
            payload = txt
        return e.code, payload
    except Exception as e:
        return 0, str(e)


def github_existing_titles(repo: str, token: str) -> set[str]:
    titles: set[str] = set()
    page = 1
    while True:
        url = f"https://api.github.com/repos/{repo}/issues?state=all&per_page=100&page={page}"
        code, data = api_request(url, token)
        if code != 200:
            print(f"WARN list issues {code}: {data}", file=sys.stderr)
            break
        if not isinstance(data, list) or not data:
            break
        for issue in data:
            # skip PRs
            if "pull_request" in issue:
                continue
            titles.add(issue.get("title", "").strip())
        if len(data) < 100:
            break
        page += 1
    return titles


def ensure_github_labels(repo: str, token: str):
    want = {
        "enhancement": ("a2eeef", "Feature request"),
        "quick-win": ("0e8a16", "Quick win (1-3 days)"),
        "medium": ("fbca04", "Medium (1-2 weeks)"),
        "big-bet": ("d93f0b", "Big bet"),
    }
    for name, (color, desc) in want.items():
        url = f"https://api.github.com/repos/{repo}/labels/{urllib.parse.quote(name)}"
        code, _ = api_request(url, token)
        if code == 200:
            continue
        # create
        url = f"https://api.github.com/repos/{repo}/labels"
        code2, data2 = api_request(url, token, method="POST", data={"name": name, "color": color, "description": desc})
        if code2 not in (200, 201):
            print(f"WARN ensure label {name}: {code2} {data2}", file=sys.stderr)


def create_github_issue(repo: str, token: str, title: str, body: str, labels: list[str], dry_run: bool = False) -> bool:
    if dry_run:
        print(f"[dry-run] would create: {title} labels={labels}")
        return True
    url = f"https://api.github.com/repos/{repo}/issues"
    code, data = api_request(url, token, method="POST", data={"title": title, "body": body, "labels": labels})
    if code in (200, 201):
        print(f"created: {title} -> {data.get('html_url','')}")
        return True
    print(f"FAIL {title}: {code} {data}", file=sys.stderr)
    return False


def gitlab_request(base: str, project: str, token: str, method: str = "GET", data: dict | None = None) -> tuple[int, object]:
    proj_enc = urllib.parse.quote(project, safe="")
    url = f"{base.rstrip('/')}/api/v4/projects/{proj_enc}/issues" if method == "POST" else f"{base.rstrip('/')}/api/v4/projects/{proj_enc}/issues?per_page=100&state=all"
    headers = {"PRIVATE-TOKEN": token}
    body = urllib.parse.urlencode(data).encode() if data and method == "POST" else None
    # GitLab expects form-encoded for simple fields; labels comma-separated
    req_headers = dict(headers)
    if body is not None:
        req_headers["Content-Type"] = "application/x-www-form-urlencoded"
    req = urllib.request.Request(url, data=body, headers=req_headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            txt = resp.read().decode()
            return resp.status, json.loads(txt) if txt else {}
    except urllib.error.HTTPError as e:
        txt = e.read().decode() if e.fp else ""
        try:
            payload = json.loads(txt) if txt else txt
        except Exception:
            payload = txt
        return e.code, payload
    except Exception as e:
        return 0, str(e)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--dry-run", action="store_true", help="print without creating")
    p.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY", REPO_DEFAULT))
    args = p.parse_args()

    gh_token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN") or ""
    gl_token = os.environ.get("GITLAB_TOKEN") or ""
    gl_project = os.environ.get("GITLAB_PROJECT") or os.environ.get("CI_PROJECT_ID") or ""
    gl_url = os.environ.get("GITLAB_URL", "https://gitlab.com")

    if not gh_token and not gl_token and not args.dry_run:
        print("Set GITHUB_TOKEN (or GH_TOKEN) for GitHub, or GITLAB_TOKEN+GITLAB_PROJECT for GitLab. Use --dry-run to preview.", file=sys.stderr)
        sys.exit(2)

    created = 0
    skipped = 0
    failed = 0

    if gh_token or args.dry_run:
        repo = args.repo
        print(f"GitHub repo: {repo} {'(dry-run)' if args.dry_run else ''}")
        existing = set() if args.dry_run else github_existing_titles(repo, gh_token)
        if not args.dry_run:
            print(f"Existing issues: {len(existing)}")
            ensure_github_labels(repo, gh_token)
        for title_short, section, desc, label_extra in ITEMS:
            title = f"[Feature] {title_short}"
            if title in existing:
                print(f"skip (exists): {title}")
                skipped += 1
                continue
            area = AREA_MAP.get(title_short, "Other")
            body = (
                f"**Area:** {area}  \n"
                f"**Section:** {section}  \n"
                f"**Source:** `ROADMAP.md`\n\n"
                f"### Description\n{desc}\n\n"
                f"### Acceptance\n- [ ] Implemented  \n- [ ] Tested on TV emulator  \n- [ ] Docs updated\n\n"
                f"> Auto-created from `ROADMAP.md` — edits to this issue do not sync back. Update `ROADMAP.md` and re-run workflow."
            )
            labels = ["enhancement", label_extra]
            ok = create_github_issue(repo, gh_token, title, body, labels, dry_run=args.dry_run)
            if ok:
                created += 1
                existing.add(title)
            else:
                failed += 1

    if gl_token and gl_project:
        print(f"GitLab project: {gl_project} @ {gl_url} {'(dry-run)' if args.dry_run else ''}")
        code, data = gitlab_request(gl_url, gl_project, gl_token, method="GET")
        existing_gl: set[str] = set()
        if code == 200 and isinstance(data, list):
            for iss in data:
                existing_gl.add(iss.get("title", "").strip())
            print(f"Existing GitLab issues: {len(existing_gl)}")
        else:
            print(f"WARN list GitLab issues {code}: {data}", file=sys.stderr)
        for title_short, section, desc, _ in ITEMS:
            title = f"[Feature] {title_short}"
            if title in existing_gl:
                print(f"skip GitLab (exists): {title}")
                skipped += 1
                continue
            area = AREA_MAP.get(title_short, "Other")
            body = f"Area: {area} / Section: {section}\n\n{desc}\n\nSource: ROADMAP.md"
            labels = "enhancement"
            if args.dry_run:
                print(f"[dry-run] would create GitLab: {title}")
                created += 1
            else:
                code2, data2 = gitlab_request(gl_url, gl_project, gl_token, method="POST", data={"title": title, "description": body, "labels": labels})
                if code2 in (200, 201):
                    print(f"created GitLab: {title}")
                    created += 1
                else:
                    print(f"FAIL GitLab {title}: {code2} {data2}", file=sys.stderr)
                    failed += 1

    print(f"Done: created={created} skipped={skipped} failed={failed}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
