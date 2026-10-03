"""Poll a GitHub Actions run until it finishes, then print artifact URLs.

Usage:
    python watch_ci.py ReF0Rain/days [--timeout 1500] [--run 1]
No token needed for public repositories.
"""
import argparse
import json
import sys
import time
import urllib.error
import urllib.request

API = "https://api.github.com"
HEADERS = {"User-Agent": "countdown-ci-watch", "Accept": "application/vnd.github+json"}


def get(url: str, timeout: int = 45):
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.load(resp)


def fmt_duration(start_iso: str, end_iso: str | None) -> str:
    from datetime import datetime, timezone

    def parse(s):
        return datetime.fromisoformat(s.replace("Z", "+00:00"))

    start = parse(start_iso)
    end = parse(end_iso) if end_iso else datetime.now(timezone.utc)
    secs = max(0, int((end - start).total_seconds()))
    return f"{secs // 60:02d}:{secs % 60:02d}"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("repo", help="owner/name")
    ap.add_argument("--timeout", type=int, default=1500, help="max seconds to wait")
    ap.add_argument("--run", type=int, default=None, help="run_number to watch")
    ap.add_argument("--poll", type=int, default=15, help="poll interval seconds")
    args = ap.parse_args()

    base = f"{API}/repos/{args.repo}"
    deadline = time.time() + args.timeout
    last = None

    while True:
        try:
            runs = get(f"{base}/actions/runs?per_page=10")["workflow_runs"]
        except urllib.error.HTTPError as exc:
            print(f"API error {exc.code}: {exc.reason}")
            if exc.code == 404:
                print("仓库不存在或不是公开仓库。")
            return 2

        run = None
        if args.run is not None:
            run = next((r for r in runs if r["run_number"] == args.run), None)
        else:
            run = runs[0] if runs else None

        if run is None:
            print(f"还没有找到 run_number={args.run} 的运行记录。")
            return 2

        line = (
            f"#{run['run_number']} status={run['status']} "
            f"conclusion={run['conclusion']} elapsed={fmt_duration(run['run_started_at'], run['updated_at'])}"
        )
        if line != last:
            print(line, flush=True)
            last = line

        if run["status"] == "completed":
            break
        if time.time() > deadline:
            print(f"\n等待超时（{args.timeout}s），仍在运行：{run['html_url']}")
            return 3
        time.sleep(args.poll)

    print()
    if run["conclusion"] == "success":
        print("构建成功 ✓")
        arts = get(f"{base}/actions/runs/{run['id']}/artifacts")["artifacts"]
        if not arts:
            print("没有生成 artifact。")
            return 1
        print("可下载的产物（需要 GitHub 登录态或 API token）：")
        for a in arts:
            print(f"  {a['name']:<24} {a['size_in_bytes'] / 1048576:6.2f} MB")
            print(f"    浏览器下载: {run['html_url']}")
            print(f"    API 下载   : {a['archive_download_url']}")
        return 0

    print(f"构建未成功：{run['conclusion']}")
    print(f"日志：{run['html_url']}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
