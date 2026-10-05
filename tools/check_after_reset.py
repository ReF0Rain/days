"""After the anonymous GitHub API rate limit resets, check run #9 and the Release.

Uses very few requests on purpose (the unauthenticated limit is 60/hour and this
machine's IP was already exhausted by polling).

Usage: check_after_reset.py <owner/repo> <min_wait_seconds>
"""
import json
import sys
import time
import urllib.error
import urllib.request

H = {"User-Agent": "dsh", "Accept": "application/vnd.github+json"}


def get(url, attempts=3):
    last = None
    for i in range(1, attempts + 1):
        try:
            return json.load(urllib.request.urlopen(
                urllib.request.Request(url, headers=H), timeout=60))
        except urllib.error.HTTPError as exc:
            last = exc
            if exc.code == 403:
                reset = exc.headers.get("X-RateLimit-Reset")
                if reset:
                    wait = max(5, int(reset) - int(time.time()) + 5)
                    print("  限流中，等待 {} 秒后重试".format(wait), flush=True)
                    time.sleep(min(wait, 900))
                    continue
            print("  HTTP {}: {}".format(exc.code, exc.reason), flush=True)
            time.sleep(20)
        except Exception as exc:  # noqa: BLE001
            last = exc
            print("  错误: {}".format(exc), flush=True)
            time.sleep(20)
    raise last


def main() -> int:
    repo = sys.argv[1] if len(sys.argv) > 1 else "ReF0Rain/days"
    wait = int(sys.argv[2]) if len(sys.argv) > 2 else 0
    if wait > 0:
        print("等待 {} 秒让限流恢复 ...".format(wait), flush=True)
        time.sleep(wait)

    print("=== Releases ===")
    rels = get("https://api.github.com/repos/{}/releases".format(repo))
    if not rels:
        print("  还没有 Release")
    for r in rels:
        print("  tag={} name={} draft={} prerelease={}".format(
            r["tag_name"], r["name"], r["draft"], r["prerelease"]))
        for a in r["assets"]:
            print("    asset: {} {} bytes".format(a["name"], a["size"]))
            print("    下载 : {}".format(a["browser_download_url"]))
        body = r.get("body") or ""
        for line in body.splitlines():
            if "签名" in line:
                print("    签名 : {}".format(line.strip()))

    print()
    print("=== 最近 4 次 run ===")
    runs = get("https://api.github.com/repos/{}/actions/runs?per_page=4".format(repo))["workflow_runs"]
    for r in runs:
        print("  #{} {} {} sha={} {}".format(
            r["run_number"], r["status"], r["conclusion"], r["head_sha"][:7], r["event"]))

    # 找带 release job 的那次
    target = None
    for r in runs:
        if r["event"] == "push" and r["head_sha"].startswith("81c6b22"):
            target = r
            break
    if target is None:
        target = runs[0]

    print()
    print("=== run #{} 步骤 ===".format(target["run_number"]))
    jobs = get(target["jobs_url"])["jobs"]
    for j in jobs:
        print("  JOB: {} -> {}".format(j["name"], j["conclusion"]))
        for s in j["steps"]:
            c = s.get("conclusion")
            mark = "FAIL" if c == "failure" else ("ok" if c == "success" else "--")
            print("    [{:>4}] {:>2}. {:<34} {}".format(mark, s["number"], s["name"], c))
    return 0


if __name__ == "__main__":
    sys.exit(main())
