"""Wait for a specific commit's runs, then report the release job outcome.

Deliberately frugal: one API call every 60s, and it stops as soon as the run of
interest is completed. Unauthenticated GitHub API allows only 60 requests/hour.

Usage: wait_runs.py <owner/repo> <sha_prefix> [max_wait_seconds]
"""
import json
import sys
import time
import urllib.error
import urllib.request

H = {"User-Agent": "dsh", "Accept": "application/vnd.github+json"}


def get(url):
    for attempt in range(1, 4):
        try:
            return json.load(urllib.request.urlopen(
                urllib.request.Request(url, headers=H), timeout=60))
        except urllib.error.HTTPError as exc:
            if exc.code == 403:
                reset = exc.headers.get("X-RateLimit-Reset")
                wait = max(30, int(reset) - int(time.time()) + 10) if reset else 300
                print("  限流，等待 {}s".format(wait), flush=True)
                time.sleep(min(wait, 900))
                continue
            print("  HTTP {} {}".format(exc.code, exc.reason), flush=True)
            time.sleep(30)
        except Exception as exc:  # noqa: BLE001
            print("  错误 {}".format(exc), flush=True)
            time.sleep(30)
    raise SystemExit("多次失败，放弃")


def main() -> int:
    repo = sys.argv[1]
    sha = sys.argv[2]
    max_wait = int(sys.argv[3]) if len(sys.argv) > 3 else 2400
    deadline = time.time() + max_wait
    base = "https://api.github.com/repos/{}".format(repo)

    target = None
    while time.time() < deadline:
        runs = get(base + "/actions/runs?per_page=8")["workflow_runs"]
        mine = [r for r in runs if r["head_sha"].startswith(sha)]
        desc = " | ".join("#{} {} {}".format(r["run_number"], r["event"], r["status"]) for r in mine)
        print("[{}] {}".format(time.strftime("%H:%M:%S"), desc or "尚未出现"), flush=True)
        done = [r for r in mine if r["status"] == "completed"]
        # 优先等 tag 触发的那次（它带 release job）
        tag_run = next((r for r in mine if r["event"] == "push" and r["conclusion"] is not None), None)
        if tag_run is not None:
            target = tag_run
            break
        if done and all(r["status"] == "completed" for r in mine):
            target = done[0]
            break
        time.sleep(60)

    if target is None:
        print("超时，未取得结果")
        return 3

    print()
    print("=== run #{} ({} ) {} ===".format(target["run_number"], target["event"], target["conclusion"]))
    print("   ", target["html_url"])
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
