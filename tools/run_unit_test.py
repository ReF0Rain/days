"""Run the project's pure-JVM unit test locally, without Gradle or the Android SDK.

Why: this machine has no Android SDK and only JDK 25, so `./gradlew test` is
impossible. But CountdownCalculatorTest only needs CountdownCalculator +
CountdownEvent (which pulls in Room annotations), so we can compile exactly those
three files with the real Kotlin compiler and run them with real JUnit.

Downloads (Maven Central):
  - kotlin-compiler-embeddable 1.9.24   (compiler)
  - kotlin-stdlib 1.9.24
  - room-common 2.6.1                   (androidx.room.Entity / ColumnInfo annotations)
  - junit 4.13.2 + hamcrest-core 1.3

Usage: run_unit_test.py <project_root>
"""
import os
import subprocess
import sys
import urllib.request

H = {"User-Agent": "Mozilla/5.0"}
MAVEN = "https://repo1.maven.org/maven2/"
GOOGLE = "https://dl.google.com/dl/android/maven2/"
KOTLIN_DIST = "https://github.com/JetBrains/kotlin/releases/download/v1.9.24/kotlin-compiler-1.9.24.zip"
ARTIFACTS = {
    # name: (base_url, relative_path)
    "kotlin-stdlib": (MAVEN, "org/jetbrains/kotlin/kotlin-stdlib/1.9.24/kotlin-stdlib-1.9.24.jar"),
    # androidx 的构件在 Google Maven，不在 Central
    "room-common": (GOOGLE, "androidx/room/room-common/2.6.1/room-common-2.6.1.jar"),
    "annotation": (GOOGLE, "androidx/annotation/annotation/1.7.1/annotation-1.7.1.jar"),
    "junit": (MAVEN, "junit/junit/4.13.2/junit-4.13.2.jar"),
    "hamcrest-core": (MAVEN, "org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
}


def fetch(name: str, spec, cache: str) -> str:
    base, rel = spec
    dst = os.path.join(cache, name + ".jar")
    if os.path.exists(dst) and os.path.getsize(dst) > 10000:
        return dst
    print("downloading", name, flush=True)
    url = base + rel
    with urllib.request.urlopen(urllib.request.Request(url, headers=H), timeout=600) as resp, \
            open(dst, "wb") as fh:
        while True:
            chunk = resp.read(1 << 20)
            if not chunk:
                break
            fh.write(chunk)
    print("  {} -> {} bytes".format(name, os.path.getsize(dst)))
    return dst


def fetch_kotlinc(cache: str) -> str:
    """下载并解压官方 kotlinc 发行包，返回 kotlinc/lib 目录。"""
    import zipfile

    # 解压后目录结构是 <extract>/kotlinc/lib/...
    extract_root = os.path.join(cache, "kotlinc-extracted")
    lib = os.path.join(extract_root, "kotlinc", "lib")
    if os.path.isdir(lib) and any(f.startswith("kotlin-compiler") for f in os.listdir(lib)):
        return lib
    zip_path = os.path.join(cache, "kotlin-compiler-1.9.24.zip")
    if not (os.path.exists(zip_path) and os.path.getsize(zip_path) > 90_000_000):
        print("downloading kotlin-compiler-1.9.24.zip (~87 MB)", flush=True)
        with urllib.request.urlopen(urllib.request.Request(KOTLIN_DIST, headers=H), timeout=900) as resp, \
                open(zip_path, "wb") as fh:
            while True:
                chunk = resp.read(1 << 20)
                if not chunk:
                    break
                fh.write(chunk)
        print("  -> {} bytes".format(os.path.getsize(zip_path)))
    with zipfile.ZipFile(zip_path) as zf:
        zf.extractall(extract_root)
    if not os.path.isdir(lib):
        raise SystemExit("kotlinc/lib not found at {}".format(lib))
    print("kotlinc lib entries:", len(os.listdir(lib)))
    return lib


def find_java17() -> str:
    """优先使用 tools/fetch_jdk17.py 下载的便携版 JDK 17（JDK 25 跑不了 Kotlin 1.9.24）。"""
    cache = os.path.join(os.environ.get("TEMP", "."), "countdown-jdk17")
    if os.path.isdir(cache):
        for entry in sorted(os.listdir(cache)):
            candidate = os.path.join(cache, entry, "bin", "java.exe")
            if os.path.isfile(candidate):
                return candidate
    env_home = os.environ.get("JAVA_HOME")
    if env_home and os.path.isfile(os.path.join(env_home, "bin", "java.exe")):
        return os.path.join(env_home, "bin", "java.exe")
    raise SystemExit("找不到 JDK 17，先运行: python tools/fetch_jdk17.py")


def main() -> int:
    root = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.getcwd())
    cache = os.path.join(os.environ.get("TEMP", "."), "countdown-testcache")
    os.makedirs(cache, exist_ok=True)

    jars = {k: fetch(k, v, cache) for k, v in ARTIFACTS.items()}
    lib = fetch_kotlinc(cache)
    compiler_cp = os.pathsep.join(
        os.path.join(lib, f) for f in sorted(os.listdir(lib)) if f.endswith(".jar"))
    classpath = os.pathsep.join([jars["kotlin-stdlib"], jars["room-common"],
                                 jars["annotation"], jars["junit"], jars["hamcrest-core"]])

    src = [
        os.path.join(root, "app", "src", "main", "java", "com", "example",
                     "countdown", "data", "CountdownCalculator.kt"),
        os.path.join(root, "app", "src", "main", "java", "com", "example",
                     "countdown", "data", "CountdownEvent.kt"),
        os.path.join(root, "app", "src", "test", "java", "com", "example",
                     "countdown", "data", "CountdownCalculatorTest.kt"),
    ]
    for path in src:
        if not os.path.exists(path):
            print("MISSING SOURCE:", path)
            return 1

    out = os.path.join(cache, "classes")
    os.makedirs(out, exist_ok=True)

    java = find_java17()
    print("using java:", java)

    # 使用官方 kotlinc 发行包里的编译器
    compile_cmd = [
        java, "-cp", compiler_cp,
        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
        "-no-stdlib", "-cp", classpath, "-d", out,
    ] + src
    print("\n=== compiling ===")
    proc = subprocess.run(compile_cmd, capture_output=True, text=True, timeout=900)
    print(proc.stdout[-4000:])
    if proc.returncode != 0:
        print(proc.stderr[-6000:])
        print("COMPILE FAILED rc =", proc.returncode)
        return 1
    print("compile OK")

    print("\n=== running JUnit ===")
    run_cmd = [java, "-cp", os.pathsep.join([out, classpath]),
               "org.junit.runner.JUnitCore",
               "com.example.countdown.data.CountdownCalculatorTest"]
    proc = subprocess.run(run_cmd, capture_output=True, text=True, timeout=300)
    print(proc.stdout)
    print(proc.stderr[-4000:])
    print("TEST rc =", proc.returncode)
    return proc.returncode


if __name__ == "__main__":
    sys.exit(main())
