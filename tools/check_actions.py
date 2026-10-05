"""Read the GitHub Actions run list from the web page (no API, avoids rate limits).

Usage: check_actions.py <owner/repo>
"""
import gzip
import re
import sys
import urllib.request

H = {"User-Agent": "Mozilla/5.0", "Accept-Encoding": "gzip"}


def fetch(url: str) -> str:
    with urllib.request.urlopen(urllib.request.Request(url, headers=H), timeout=60) as resp:
        raw = resp.read()
        if resp.headers.get("Content-Encoding") == "gzip":
            raw = gzip.decompress(raw)
        return raw.decode("utf-8", "replace")


def main() -> int:
    repo = sys.argv[1] if len(sys.argv) > 1 else "ReF0Rain/days"
    url = f"https://github.com/{repo}/actions"
    html = fetch(url)
    print("页面大小:", len(html))

    # 每个 run 的链接形如 /owner/repo/actions/runs/<id>
    run_ids = []
    for m in re.finditer(r'/actions/runs/(\d+)', html):
        if m.group(1) not in run_ids:
            run_ids.append(m.group(1))
    print("可见 run id:", run_ids[:8])

    # 状态关键词
    for kw, label in (
        ("completed successfully", "成功"),
        ("has failed", "失败"),
        ("is currently running", "运行中"),
        ("queued", "排队"),
        ("in progress", "进行中"),
        ("cancelled", "已取消"),
    ):
        n = html.count(kw)
        if n:
            print(f"  {label:6} x{n}")

    # 抓取页面里形如 "#123" 的 run 编号 + 附近文案
    print()
    print("=== run 标题片段（去标签） ===")
    text = re.sub(r"<script.*?</script>", " ", html, flags=re.S)
    text = re.sub(r"<style.*?</style>", " ", text, flags=re.S)
    text = re.sub(r"<[^>]+>", "\n", text)
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    keep = []
    for i, l in enumerate(lines):
        if re.match(r"^#\d+$", l) or "Android CI" in l:
            keep.append(l)
            if i + 1 < len(lines):
                keep.append("   -> " + lines[i + 1][:80])
    for l in keep[:40]:
        print(l)
    return 0


if __name__ == "__main__":
    sys.exit(main())
