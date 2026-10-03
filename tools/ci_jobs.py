"""Print per-step status of the latest GitHub Actions run, with retries."""
import json
import sys
import time
import urllib.request

H = {"User-Agent": "dsh", "Accept": "application/vnd.github+json"}
REPO = sys.argv[1] if len(sys.argv) > 1 else "ReF0Rain/days"
RUN_NUMBER = int(sys.argv[2]) if len(sys.argv) > 2 else None


def get(url, timeout=60, attempts=6):
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


runs = get("https://api.github.com/repos/{}/actions/runs?per_page=10".format(REPO))["workflow_runs"]
if RUN_NUMBER is not None:
    run = next((r for r in runs if r["run_number"] == RUN_NUMBER), None)
    if run is None:
        print("run #{} not found".format(RUN_NUMBER))
        sys.exit(2)
else:
    run = runs[0]

print("run:", run["run_number"], run["status"], run["conclusion"])
print("url:", run["html_url"])
print("sha:", run["head_sha"][:7])

jobs = get(run["jobs_url"])["jobs"]
for j in jobs:
    print()
    print("JOB:", j["name"], "->", j["conclusion"])
    for s in j["steps"]:
        c = s.get("conclusion")
        mark = "FAIL" if c == "failure" else ("ok" if c == "success" else "--")
        print("  [{:>4}] {:>2}. {:<34} {}".format(mark, s["number"], s["name"], c))
