"""Download a portable Temurin JDK 17 into the temp cache (no system install).

Why: this machine only has JDK 25. Gradle 8.6, the Kotlin 1.9.24 compiler and
AGP 8.4 all refuse to run on it, so every local check needs a real JDK 17.

Usage: fetch_jdk17.py [--force]
Prints the JAVA_HOME path on success (last line).
"""
import json
import os
import sys
import time
import urllib.request
import zipfile

H = {"User-Agent": "Mozilla/5.0"}
API = ("https://api.adoptium.net/v3/assets/latest/17/hotspot"
       "?architecture=x64&image_type=jdk&os=windows&vendor=eclipse")
CACHE = os.path.join(os.environ.get("TEMP", "."), "countdown-jdk17")


def download_with_resume(url: str, path: str, expected: int | None, headers: dict) -> None:
    """断点续传下载：网络中断时从已有字节继续，避免大文件反复重下。"""
    for attempt in range(1, 8):
        have = os.path.getsize(path) if os.path.exists(path) else 0
        if expected and have == expected:
            print("  already complete: {} bytes".format(have))
            return
        hdrs = dict(headers)
        mode = "wb"
        if have and expected and have < expected:
            hdrs["Range"] = "bytes={}-".format(have)
            mode = "ab"
            print("  resuming at {} bytes (attempt {})".format(have, attempt), flush=True)
        else:
            have = 0
            print("  downloading (attempt {})".format(attempt), flush=True)
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=hdrs), timeout=300) as resp, \
                    open(path, mode) as fh:
                while True:
                    chunk = resp.read(1 << 20)
                    if not chunk:
                        break
                    fh.write(chunk)
            print("  -> {} bytes".format(os.path.getsize(path)), flush=True)
        except Exception as exc:  # noqa: BLE001
            print("  interrupted: {}".format(exc), flush=True)


def main() -> int:
    force = "--force" in sys.argv
    os.makedirs(CACHE, exist_ok=True)

    # 已解压过就直接复用
    for entry in os.listdir(CACHE):
        candidate = os.path.join(CACHE, entry)
        if os.path.isdir(candidate) and os.path.exists(os.path.join(candidate, "bin", "java.exe")):
            if not force:
                print("already present")
                print(candidate)
                return 0

    print("querying Adoptium API ...")
    assets = None
    for attempt in range(1, 6):
        try:
            with urllib.request.urlopen(urllib.request.Request(API, headers=H), timeout=90) as resp:
                assets = json.load(resp)
            break
        except Exception as exc:  # noqa: BLE001
            print("  api attempt {} failed: {}".format(attempt, exc), flush=True)
            time.sleep(3 * attempt)
    if not assets:
        # API 不稳定时退回到已知的 Adoptium 重定向地址
        print("  falling back to known redirect URL")
        fallback = ("https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/"
                    "hotspot/normal/eclipse")
        zip_path = os.path.join(CACHE, "jdk17.zip")
        download_with_resume(fallback, zip_path, None, H)
        with zipfile.ZipFile(zip_path) as zf:
            zf.extractall(CACHE)
        for entry in os.listdir(CACHE):
            candidate = os.path.join(CACHE, entry)
            if os.path.isfile(os.path.join(candidate, "bin", "java.exe")):
                print(candidate)
                return 0
        print("java.exe not found after extraction")
        return 1
    pkg = assets[0]["binary"]["package"]
    url, size = pkg["link"], pkg.get("size")
    print("jdk {}  {} bytes".format(assets[0]["version"]["semver"], size))

    zip_path = os.path.join(CACHE, "jdk17.zip")
    download_with_resume(url, zip_path, size, H)
    if size and os.path.getsize(zip_path) != size:
        print("ERROR: size mismatch after download: {} != {}".format(
            os.path.getsize(zip_path), size))
        return 1

    print("extracting ...")
    with zipfile.ZipFile(zip_path) as zf:
        zf.extractall(CACHE)

    for entry in os.listdir(CACHE):
        candidate = os.path.join(CACHE, entry)
        if os.path.isfile(os.path.join(candidate, "bin", "java.exe")):
            print(candidate)
            return 0
    print("java.exe not found after extraction")
    return 1


if __name__ == "__main__":
    sys.exit(main())
