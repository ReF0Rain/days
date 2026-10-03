"""Fetch a GitHub Actions job log (with retries) and print the interesting part.

Usage: get_job_log.py <owner/repo> <run_number> [--marker TEXT] [--context N]
"""
import argparse
import io
import json
import sys
import time
import urllib.error
import urllib.request
import zipfile

H = {"User-Agent": "dsh", "Accept": "application/vnd.github+json"}


def get_json(url, attempts=6, timeout=60):
    last = None
    for i in range(1, attempts + 1):
        try:
            return json.load(urllib.request.urlopen(
                urllib.request.Request(url, headers=H), timeout=timeout))
        except Exception as exc:  # noqa: BLE001
            last = exc
            print("  retry {}/{}: {}".format(i, attempts, exc), file=sys.stderr)
            time.sleep(min(3 * i, 15))
    raise last


def fetch_logs(repo, job_id, attempts=6):
    url = "https://api.github.com/repos/{}/actions/jobs/{}/logs".format(repo, job_id)
    last = None
    for i in range(1, attempts + 1):
        try:
            req = urllib.request.Request(url, headers=H)
            with urllib.request.urlopen(req, timeout=180) as resp:
                return resp.read(), resp.headers.get("Content-Type", "")
        except urllib.error.HTTPError as exc:
            if exc.code in (401, 403):
                raise
            last = exc
            time.sleep(min(3 * i, 15))
        except Exception as exc:  # noqa: BLE001
            last = exc
            time.sleep(min(3 * i, 15))
    raise last


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("repo")
    ap.add_argument("run_number", type=int)
    ap.add_argument("--marker", default="=====")
    ap.add_argument("--context", type=int, default=60)
    args = ap.parse_args()

    runs = get_json("https://api.github.com/repos/{}/actions/runs?per_page=20".format(args.repo))["workflow_runs"]
    run = next((r for r in runs if r["run_number"] == args.run_number), None)
    if run is None:
        print("run not found")
        return 2
    job = get_json(run["jobs_url"])["jobs"][0]
    print("job:", job["name"], "id:", job["id"])

    try:
        data, ctype = fetch_logs(args.repo, job["id"])
    except urllib.error.HTTPError as exc:
        print("HTTP {} {} — GitHub 要求登录才能下载日志".format(exc.code, exc.reason))
        print("手动查看:", job["html_url"])
        return 2

    if data[:2] == b"PK":
        zf = zipfile.ZipFile(io.BytesIO(data))
        parts = []
        for n in zf.namelist():
            parts.append("===== {} =====".format(n))
            parts.append(zf.read(n).decode("utf-8", "replace"))
        text = "\n".join(parts)
    else:
        text = data.decode("utf-8", "replace")

    print("log size: {} chars".format(len(text)))
    lines = text.splitlines()
    hits = [i for i, l in enumerate(lines) if args.marker in l]
    if hits:
        for idx in hits:
            lo, hi = max(0, idx - 2), min(len(lines), idx + args.context)
            print("---- around line {} ----".format(idx + 1))
            for l in lines[lo:hi]:
                print(l.rstrip())
    else:
        print("marker not found; tail:")
        for l in lines[-args.context:]:
            print(l.rstrip())
    return 0


if __name__ == "__main__":
    sys.exit(main())
