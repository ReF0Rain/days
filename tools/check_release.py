"""Check whether the GitHub Release for a tag exists, and what it contains.

Uses plain HTTPS page fetches (no API) so it keeps working while the anonymous
API rate limit is exhausted.

Usage: check_release.py <owner/repo> <tag>
"""
import gzip
import re
import sys
import urllib.error
import urllib.request

H = {"User-Agent": "Mozilla/5.0", "Accept-Encoding": "gzip"}


def fetch(url: str) -> tuple[int, str, str | None]:
    """Returns (status, html, redirect_location)."""
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            raise urllib.error.HTTPError(req.full_url, code, "redirect", headers, fp)

    opener = urllib.request.build_opener(NoRedirect)
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=H), timeout=60) as resp:
            raw = resp.read()
            if resp.headers.get("Content-Encoding") == "gzip":
                raw = gzip.decompress(raw)
            return resp.status, raw.decode("utf-8", "replace"), None
    except urllib.error.HTTPError as exc:
        if exc.code in (301, 302, 303, 307, 308):
            return exc.code, "", exc.headers.get("Location")
        body = ""
        try:
            body = exc.read().decode("utf-8", "replace")
        except Exception:
            pass
        return exc.code, body, None


def main() -> int:
    repo = sys.argv[1] if len(sys.argv) > 1 else "ReF0Rain/days"
    tag = sys.argv[2] if len(sys.argv) > 2 else "v1.0.0"

    base = f"https://github.com/{repo}"

    # /releases/latest 只有在存在正式 Release 时才 302 到具体 tag
    status, _, loc = fetch(f"{base}/releases/latest")
    print(f"/releases/latest -> HTTP {status} {loc or ''}")
    if status in (301, 302) and loc and "/releases/tag/" in loc:
        print("=> 存在正式 Release，latest 指向", loc.rsplit("/", 1)[-1])
    else:
        print("=> 没有正式 Release（latest 回落到列表页）")

    status, html, loc = fetch(f"{base}/releases/tag/{tag}")
    print(f"/releases/tag/{tag} -> HTTP {status} {loc or ''}")
    if not html:
        return 1

    print("页面大小:", len(html))
    print()
    print("=== APK 附件 ===")
    assets = sorted(set(re.findall(r"/[^\"'\s]*releases/download/[^\"'\s]+", html)))
    if assets:
        for a in assets:
            print("  ", a)
    else:
        print("   （无）")

    print()
    print("=== 关键内容 ===")
    for kw in ("正式 keystore", "debug 签名", "自动构建", "Assets", "Source code"):
        print("  {:14} {}".format(kw, "找到" if kw in html else "未找到"))

    print()
    m = re.search(r"([\d,]+)\s+downloads?", html)
    if m:
        print("下载量:", m.group(1))

    # 说明区片段
    body = re.search(r'<div[^>]*data-view-component="true"[^>]*class="[^"]*markdown-body[^"]*"[^>]*>(.*?)</div>',
                     html, re.S)
    if body:
        text = re.sub(r"<[^>]+>", " ", body.group(1))
        text = re.sub(r"\s+", " ", text).strip()
        print()
        print("说明片段:", text[:400])
    return 0


if __name__ == "__main__":
    sys.exit(main())
