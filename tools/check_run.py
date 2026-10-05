"""Fetch a specific Actions run page and print per-job status (no API).

Usage: check_run.py <owner/repo> <run_id>
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
    repo = sys.argv[1]
    run_id = sys.argv[2]
    url = f"https://github.com/{repo}/actions/runs/{run_id}"
    html = fetch(url)
    print("run page:", url)
    print("页面大小:", len(html))

    # 总体结论
    for kw, label in (
        ("completed successfully", "整体成功"),
        ("has failed", "整体失败"),
        ("in progress", "进行中"),
        ("queued", "排队中"),
        ("cancelled", "已取消"),
    ):
        n = html.count(kw)
        if n:
            print(f"  {label} x{n}")

    print()
    print("=== 文本摘要 ===")
    text = re.sub(r"<script.*?</script>", " ", html, flags=re.S)
    text = re.sub(r"<style.*?</style>", " ", text, flags=re.S)
    text = re.sub(r"<[^>]+>", "\n", text)
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    # 打印包含关键字的行及其上下文
    keys = ("签名", "keystore", "Release", "releases/", "APK", "success", "failure",
            "Publish", "Build", "Signing", "decode", "Decode", "错误", "error")
    shown = 0
    for i, l in enumerate(lines):
        if any(k.lower() in l.lower() for k in keys) and len(l) < 200:
            print("  ", l[:160])
            shown += 1
            if shown > 60:
                break
    return 0


if __name__ == "__main__":
    sys.exit(main())
