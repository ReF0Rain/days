"""Download a GitHub Actions artifact and print test report contents.

Usage: fetch_artifact.py <owner/repo> <artifact_name> [run_number]
"""
import io
import json
import sys
import urllib.request
import zipfile

H = {"User-Agent": "dsh", "Accept": "application/vnd.github+json"}


def get_json(url):
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=H), timeout=60))


def main() -> int:
    repo, name = sys.argv[1], sys.argv[2]
    runs = get_json(f"https://api.github.com/repos/{repo}/actions/runs?per_page=10")["workflow_runs"]
    run = None
    if len(sys.argv) > 3:
        want = int(sys.argv[3])
        run = next((r for r in runs if r["run_number"] == want), None)
    else:
        run = runs[0]
    if run is None:
        print("run not found")
        return 1
    print("run #{} {}".format(run["run_number"], run["html_url"]))

    arts = get_json(f"https://api.github.com/repos/{repo}/actions/runs/{run['id']}/artifacts")["artifacts"]
    art = next((a for a in arts if a["name"] == name), None)
    if art is None:
        print("artifact '{}' not found; available: {}".format(name, [a["name"] for a in arts]))
        return 1
    print("artifact: {} ({} bytes)".format(art["name"], art["size_in_bytes"]))

    url = art["archive_download_url"]
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=H), timeout=180) as resp:
            data = resp.read()
    except Exception as exc:  # noqa: BLE001
        print("download failed: {}".format(exc))
        print("(GitHub 需要登录态才能下载 artifact)")
        return 2

    print("downloaded {} bytes".format(len(data)))
    if data[:2] != b"PK":
        print("not a zip; head:", data[:200])
        return 1

    with zipfile.ZipFile(io.BytesIO(data)) as zf:
        names = zf.namelist()
        print("entries:", len(names))
        interesting = [n for n in names if n.endswith((".xml", ".txt", ".html"))]
        for n in interesting[:20]:
            print("---- {} ----".format(n))
            text = zf.read(n).decode("utf-8", "replace")
            if n.endswith(".xml") and "<testcase" in text:
                # 只打印失败的测试
                import re
                for m in re.finditer(r"<testcase[^>]*name=\"([^\"]+)\"[^>]*>(.*?)</testcase>", text, re.S):
                    if "<failure" in m.group(2) or "<error" in m.group(2):
                        print("FAILED:", m.group(1))
                        print(m.group(2)[:3000])
                if "<failure" not in text:
                    print(text[:1500])
            else:
                print(text[:1500])
    return 0


if __name__ == "__main__":
    sys.exit(main())
