"""Generate the release keystore for this app, locally and reproducibly.

What it does:
  1. Writes keystore/countdown.jks (PKCS12, RSA 2048, 10000 days) via keytool
     from the portable JDK 17.
  2. Writes keystore/keystore.properties (gitignored) with the passwords, so
     local release builds pick up the real signing config automatically.
  3. Writes keystore/keystore.base64.txt ready to paste into GitHub Secrets.
  4. Prints the exact values for the four GitHub Secrets.

The keystore/ directory is gitignored on purpose: the private key must never be
committed. Keep a backup somewhere safe - losing it means you can never update
an app that was published with it.

Usage: make_keystore.py <project_root> [--force]
"""
import base64
import os
import secrets
import string
import subprocess
import sys

ALIAS = "countdown"
KEYSTORE_NAME = "countdown.jks"
VALIDITY_DAYS = 10000
DNAME = "CN=Countdown, OU=Mobile, O=Countdown App, L=Beijing, ST=Beijing, C=CN"


def find_jdk17() -> str:
    cache = os.path.join(os.environ.get("TEMP", "."), "countdown-jdk17")
    if os.path.isdir(cache):
        for entry in sorted(os.listdir(cache)):
            home = os.path.join(cache, entry)
            if os.path.isfile(os.path.join(home, "bin", "keytool.exe")):
                return home
    raise SystemExit("需要 JDK 17，先运行: python tools/fetch_jdk17.py .")


def random_password(length: int = 24) -> str:
    # 只用无歧义字符：避免复制粘贴到 GitHub Secrets 时出错，
    # 也避免和 shell/Java 属性文件里的转义字符冲突
    alphabet = string.ascii_letters + string.digits
    return "".join(secrets.choice(alphabet) for _ in range(length))


def main() -> int:
    root = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.getcwd())
    force = "--force" in sys.argv
    jdk = find_jdk17()
    keytool = os.path.join(jdk, "bin", "keytool.exe")

    ks_dir = os.path.join(root, "keystore")
    os.makedirs(ks_dir, exist_ok=True)
    jks = os.path.join(ks_dir, KEYSTORE_NAME)
    props = os.path.join(ks_dir, "keystore.properties")
    b64_path = os.path.join(ks_dir, "keystore.base64.txt")

    if os.path.exists(jks) and not force:
        print("keystore 已存在:", jks)
        print("如需重新生成（会更换签名，已发布的应用将无法覆盖安装）请加 --force")
        if not os.path.exists(b64_path):
            with open(jks, "rb") as fh:
                raw = fh.read()
            with open(b64_path, "w", encoding="ascii") as fh:
                fh.write(base64.b64encode(raw).decode("ascii"))
            print("已补充生成", b64_path)
        return 0

    store_pass = random_password()
    key_pass = store_pass  # PKCS12 要求两个口令一致

    cmd = [
        keytool, "-genkeypair", "-v",
        "-keystore", jks,
        "-storetype", "PKCS12",
        "-alias", ALIAS,
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", str(VALIDITY_DAYS),
        "-storepass", store_pass,
        "-keypass", key_pass,
        "-dname", DNAME,
    ]
    print("生成 keystore ...")
    proc = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
    if proc.returncode != 0:
        print(proc.stdout[-2000:])
        print(proc.stderr[-2000:])
        print("keytool 失败 rc =", proc.returncode)
        return 1
    print("  ->", jks, os.path.getsize(jks), "bytes")

    with open(props, "w", encoding="utf-8") as fh:
        fh.write("# 由 tools/make_keystore.py 生成，已被 .gitignore 忽略，切勿提交\n")
        fh.write("storeFile=keystore/countdown.jks\n")
        fh.write("storePassword={}\n".format(store_pass))
        fh.write("keyAlias={}\n".format(ALIAS))
        fh.write("keyPassword={}\n".format(key_pass))

    with open(jks, "rb") as fh:
        raw = fh.read()
    b64 = base64.b64encode(raw).decode("ascii")
    with open(b64_path, "w", encoding="ascii") as fh:
        fh.write(b64)

    print()
    print("=" * 72)
    print("GitHub Secrets 需要配置这 4 个值（仓库 Settings -> Secrets and variables")
    print("-> Actions -> New repository secret）：")
    print("=" * 72)
    print("KEYSTORE_BASE64   : 见文件 {} （{} 字符）".format(
        os.path.relpath(b64_path, root), len(b64)))
    print("KEYSTORE_PASSWORD : {}".format(store_pass))
    print("KEY_ALIAS         : {}".format(ALIAS))
    print("KEY_PASSWORD      : {}".format(key_pass))
    print("=" * 72)
    print()
    print("或者直接运行一键脚本（需要 gh 已登录）:")
    print("  powershell -File scripts\\setup-github-secrets.ps1 -Repo ReF0Rain/days")
    print()
    print("务必离线备份 keystore/ 目录：私钥丢失后，已发布的应用将永远无法更新。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
