"""Print per-step status of the latest GitHub Actions run."""
import json
import sys
import urllib.request

H = {"User-Agent": "dsh"}
REPO = sys.argv[1] if len(sys.argv) > 1 else "ReF0Rain/days"


def get(url):
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=H), timeout=60))


runs = get(f"https://api.github.com/repos/{REPO}/actions/runs?per_page=1")["workflow_runs"]
run = runs[0]
print("run:", run["run_number"], run["status"], run["conclusion"])
print("url:", run["html_url"])
jobs = get(run["jobs_url"])["jobs"]
for j in jobs:
    print()
    print("JOB:", j["name"], "->", j["conclusion"])
    for s in j["steps"]:
        c = s.get("conclusion")
        mark = "FAIL" if c == "failure" else ("ok" if c == "success" else "--")
        print("  [{:>4}] {:>2}. {:<34} {}".format(mark, s["number"], s["name"], c))
