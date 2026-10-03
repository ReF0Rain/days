"""Install the official Gradle wrapper files (gradlew, gradlew.bat, gradle-wrapper.jar).

Source of truth: the gradle/gradle repository tag v8.6.0, so scripts and jar stay
version matched, plus the official distribution checksum.

Local `gradle wrapper` cannot be used on this machine: the only JDK is 25 and
Gradle 8.6 refuses to run on it ("What went wrong: 25.0.1"). That is also why the
Gradle version is pinned in gradle/wrapper/gradle-wrapper.properties.

Usage: fetch_wrapper.py [project_root]
"""
import hashlib
import io
import os
import sys
import urllib.request
import zipfile

TAG = "v8.6.0"
RAW = f"https://raw.githubusercontent.com/gradle/gradle/{TAG}/"
DIST = "https://services.gradle.org/distributions/gradle-8.6-bin.zip"
DIST_SHA256 = "https://services.gradle.org/distributions/gradle-8.6-bin.zip.sha256"
H = {"User-Agent": "Mozilla/5.0"}
WRAPPER_MAIN = "org/gradle/wrapper/GradleWrapperMain.class"


def fetch(url: str, timeout: int = 180) -> bytes:
    with urllib.request.urlopen(urllib.request.Request(url, headers=H), timeout=timeout) as resp:
        return resp.read()


def main() -> int:
    root = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.getcwd())
    wrapper_dir = os.path.join(root, "gradle", "wrapper")
    os.makedirs(wrapper_dir, exist_ok=True)

    # ---- 1) wrapper jar ----
    jar = fetch(RAW + "gradle/wrapper/gradle-wrapper.jar")
    if jar[:2] != b"PK":
        print("ERROR: jar is not a zip archive")
        return 1
    with zipfile.ZipFile(io.BytesIO(jar)) as zf:
        if WRAPPER_MAIN not in zf.namelist():
            print("ERROR: {} missing from jar".format(WRAPPER_MAIN))
            return 1
    jar_path = os.path.join(wrapper_dir, "gradle-wrapper.jar")
    with open(jar_path, "wb") as fh:
        fh.write(jar)
    print("wrote {} ({} bytes, sha256={})".format(
        jar_path, len(jar), hashlib.sha256(jar).hexdigest()))

    # ---- 2) launcher scripts ----
    for name in ("gradlew", "gradlew.bat"):
        data = fetch(RAW + name)
        if len(data) < 1000:
            print("ERROR: {} looks truncated ({} bytes)".format(name, len(data)))
            return 1
        dst = os.path.join(root, name)
        with open(dst, "wb") as fh:
            fh.write(data)
        print("wrote {} ({} bytes)".format(dst, len(data)))

    try:
        os.chmod(os.path.join(root, "gradlew"), 0o755)
        print("chmod 755 gradlew")
    except OSError as exc:
        print("chmod skipped:", exc)

    # ---- 3) 记录官方发行包校验和，供 gradle-wrapper.properties 使用 ----
    official = fetch(DIST_SHA256).decode().strip()
    print("official {} sha256: {}".format(DIST.split('/')[-1], official))
    print("put this in gradle-wrapper.properties as distributionSha256Sum")
    return 0


if __name__ == "__main__":
    sys.exit(main())
