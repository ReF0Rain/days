"""Install a portable Android SDK into the temp cache, plus a local.properties.

Downloads (all from dl.google.com, which this machine can reach reliably):
  - commandlinetools-win (latest)
  - platform-tools, platforms;android-34, build-tools;34.0.0

Then writes <project>/local.properties with sdk.dir so Gradle can build locally.

Usage: setup_android_sdk.py <project_root> [--skip-packages]
Prints the SDK root on success.
"""
import os
import re
import shutil
import subprocess
import sys
import urllib.request
import zipfile

H = {"User-Agent": "Mozilla/5.0"}
CACHE = os.path.join(os.environ.get("TEMP", "."), "android-sdk")
CLT_ZIP = os.path.join(CACHE, "_cmdline-tools.zip")

PACKAGES = [
    "platform-tools",
    "platforms;android-34",
    "build-tools;34.0.0",
]


def find_jdk17() -> str:
    cache = os.path.join(os.environ.get("TEMP", "."), "countdown-jdk17")
    if os.path.isdir(cache):
        for entry in sorted(os.listdir(cache)):
            home = os.path.join(cache, entry)
            if os.path.isfile(os.path.join(home, "bin", "java.exe")):
                return home
    raise SystemExit("need JDK 17 first: python tools/fetch_jdk17.py")


def download(url: str, path: str, expected: int | None = None) -> None:
    for attempt in range(1, 8):
        have = os.path.getsize(path) if os.path.exists(path) else 0
        if expected and have == expected:
            return
        hdrs = dict(H)
        mode = "wb"
        if have and (expected is None or have < expected):
            hdrs["Range"] = "bytes={}-".format(have)
            mode = "ab"
            print("  resume at {} bytes (attempt {})".format(have, attempt), flush=True)
        else:
            have = 0
            print("  download attempt {}".format(attempt), flush=True)
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
    root = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.getcwd())
    skip_packages = "--skip-packages" in sys.argv
    os.makedirs(CACHE, exist_ok=True)
    jdk = find_jdk17()
    print("JDK 17:", jdk)

    # ---- 1) command-line tools ----
    sdkmanager = os.path.join(CACHE, "cmdline-tools", "latest", "bin", "sdkmanager.bat")
    if not os.path.exists(sdkmanager):
        url = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
        print("downloading command-line tools")
        download(url, CLT_ZIP)
        with zipfile.ZipFile(CLT_ZIP) as zf:
            zf.extractall(CACHE)
        src = os.path.join(CACHE, "cmdline-tools")
        # 解压出来的是 cmdline-tools/bin，sdkmanager 要求放在 cmdline-tools/latest/
        if os.path.isdir(os.path.join(src, "bin")) and not os.path.isdir(os.path.join(src, "latest")):
            staging = os.path.join(CACHE, "_clt_staging")
            shutil.move(src, staging)
            os.makedirs(src, exist_ok=True)
            shutil.move(staging, os.path.join(src, "latest"))
        if not os.path.exists(sdkmanager):
            print("sdkmanager not found at", sdkmanager)
            return 1
    print("sdkmanager:", sdkmanager)

    # ---- 2) 接受许可 + 安装包 ----
    if not skip_packages:
        env = dict(os.environ)
        env["JAVA_HOME"] = jdk
        env["ANDROID_HOME"] = CACHE
        env["ANDROID_SDK_ROOT"] = CACHE

        print("accepting licenses ...")
        yes = subprocess.run(
            [sdkmanager, "--sdk_root=" + CACHE, "--licenses"],
            input="y\n" * 60, capture_output=True, text=True, env=env, timeout=900)
        print("  rc =", yes.returncode)
        tail = (yes.stdout or "")[-800:]
        if tail.strip():
            print("  ", tail.replace("\n", "\n   "))

        print("installing packages:", ", ".join(PACKAGES))
        inst = subprocess.run(
            [sdkmanager, "--sdk_root=" + CACHE] + PACKAGES,
            capture_output=True, text=True, env=env, timeout=3600)
        print("  rc =", inst.returncode)
        print((inst.stdout or "")[-1500:])
        if inst.returncode != 0:
            print((inst.stderr or "")[-2000:])
            return 1

    # ---- 3) 写 local.properties ----
    props = os.path.join(root, "local.properties")
    sdk_dir = CACHE.replace("\\", "\\\\")
    with open(props, "w", encoding="utf-8") as fh:
        fh.write("# 由 tools/setup_android_sdk.py 生成（本机临时 SDK，勿提交）\n")
        fh.write("sdk.dir={}\n".format(sdk_dir))
    print("wrote", props)

    # 验证关键目录
    for rel in ["platforms/android-34/android.jar", "build-tools/34.0.0/aapt2.exe",
                "platform-tools/adb.exe"]:
        full = os.path.join(CACHE, rel.replace("/", os.sep))
        print("  {}: {}".format("OK " if os.path.exists(full) else "MISSING", rel))

    print(CACHE)
    return 0


if __name__ == "__main__":
    sys.exit(main())
