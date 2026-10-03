"""Build this project locally, working around two machine-specific blockers.

Blocker 1: the project path contains non-ASCII characters ("新建文件夹"), and AGP
refuses to configure in that case:
    "Your project path contains non-ASCII characters."
Blocker 2: the only system JDK is 25; Gradle 8.6 / AGP 8.4 / Kotlin 1.9.24 all
refuse to run on it.

So: copy the source tree to an ASCII temp path, point local.properties at the
portable Android SDK, and run Gradle with the portable JDK 17.

Usage: local_build.py [--task TASK] [--clean] [--tail N] [--exclude PATTERN]
Default task: :app:assembleDebug
"""
import argparse
import os
import shutil
import subprocess
import sys

TEMP = os.environ.get("TEMP", ".")
SDK = os.path.join(TEMP, "android-sdk")
JDK_CACHE = os.path.join(TEMP, "countdown-jdk17")
DEST = os.path.join(TEMP, "CountdownApp")
SKIP_DIRS = {".git", "build", ".gradle", ".idea", "schemas"}
SKIP_FILES = {"local.properties"}


def find_jdk17() -> str:
    if os.path.isdir(JDK_CACHE):
        for entry in sorted(os.listdir(JDK_CACHE)):
            home = os.path.join(JDK_CACHE, entry)
            if os.path.isfile(os.path.join(home, "bin", "java.exe")):
                return home
    raise SystemExit("没有便携版 JDK 17，先运行: python tools/fetch_jdk17.py")


def copy_tree(src: str, dst: str) -> int:
    if os.path.isdir(dst):
        shutil.rmtree(dst, ignore_errors=True)
    count = 0
    for root, dirs, files in os.walk(src):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        rel = os.path.relpath(root, src)
        target_dir = dst if rel == "." else os.path.join(dst, rel)
        os.makedirs(target_dir, exist_ok=True)
        for name in files:
            if name in SKIP_FILES:
                continue
            shutil.copy2(os.path.join(root, name), os.path.join(target_dir, name))
            count += 1
    return count


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--task", nargs="+", default=[":app:assembleDebug"],
                    help="一个或多个 Gradle 任务，例如: --task :app:assembleDebug :app:testDebugUnitTest")
    ap.add_argument("--src", default=os.path.join(os.path.dirname(os.path.dirname(
        os.path.abspath(__file__)))))
    ap.add_argument("--clean", action="store_true", help="先删掉临时工程再复制")
    ap.add_argument("--tail", type=int, default=60)
    ap.add_argument("--errors-only", action="store_true")
    args = ap.parse_args()

    jdk = find_jdk17()
    if not os.path.isdir(SDK):
        raise SystemExit("没有 Android SDK，先运行: python tools/setup_android_sdk.py .")

    if args.clean or not os.path.isdir(DEST):
        n = copy_tree(args.src, DEST)
        print("copied {} files -> {}".format(n, DEST))
    else:
        n = copy_tree(args.src, DEST)
        print("refreshed {} files -> {}".format(n, DEST))

    with open(os.path.join(DEST, "local.properties"), "w", encoding="ascii") as fh:
        fh.write("sdk.dir={}\n".format(SDK.replace("\\", "/")))

    env = dict(os.environ)
    env["JAVA_HOME"] = jdk
    env["ANDROID_HOME"] = SDK
    env["ANDROID_SDK_ROOT"] = SDK
    env["GRADLE_OPTS"] = "-Dorg.gradle.internal.http.connectionTimeout=180000 " \
                         "-Dorg.gradle.internal.http.socketTimeout=180000"

    cmd = [os.path.join(DEST, "gradlew.bat")] + list(args.task) + [
        "--console=plain", "--no-daemon", "--stacktrace",
        "-Dorg.gradle.internal.http.connectionTimeout=180000",
        "-Dorg.gradle.internal.http.socketTimeout=180000",
        "-Dorg.gradle.internal.repository.max.retries=8"]
    print("running:", " ".join(cmd), flush=True)
    proc = subprocess.run(cmd, cwd=DEST, env=env, capture_output=True, text=True,
                          encoding="utf-8", errors="replace", timeout=5400)
    out = (proc.stdout or "") + "\n" + (proc.stderr or "")
    log = os.path.join(TEMP, "cd-local-build.log")
    with open(log, "w", encoding="utf-8") as fh:
        fh.write(out)
    print("full log:", log)

    if args.errors_only:
        for line in out.splitlines():
            if line.startswith(("e:", "w: ")) or "error:" in line or "FAILED" in line \
                    or "BUILD" in line or "What went wrong" in line:
                print(line.rstrip())
    else:
        for line in out.splitlines()[-args.tail:]:
            print(line.rstrip())

    print("EXIT =", proc.returncode)
    return proc.returncode


if __name__ == "__main__":
    sys.exit(main())
