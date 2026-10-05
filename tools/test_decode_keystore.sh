#!/usr/bin/env bash
# 模拟 CI 里 "Decode signing keystore" 这一步，用真实的 base64.txt 验证解码逻辑。
# 同时测三种"脏数据"场景：带换行、带空格、带 UTF-8 BOM。
set -uo pipefail

B64_FILE="$1"
PASSWORD="$2"
JAVA_HOME_DIR="$3"

WORK=$(mktemp -d)
cd "$WORK" || exit 1

echo "=== 场景 1: 原始文件内容（含行尾换行） ==="
RAW=$(cat "$B64_FILE")
echo "Secret 原始长度: ${#RAW}"
CLEAN=$(printf '%s' "$RAW" | tr -d '[:space:]\357\273\277')
echo "清洗后长度: ${#CLEAN}"
printf '%s' "$CLEAN" | base64 --decode > a.jks && echo "解码 OK -> $(wc -c < a.jks) bytes" || echo "解码 FAIL"

echo
echo "=== 场景 2: 混入换行 + 空格 ==="
DIRTY=$(printf '%s' "$RAW" | sed 's/\(.\{100\}\)/\1\n/g; s/MII/M II /')
echo "脏数据长度: ${#DIRTY}"
CLEAN2=$(printf '%s' "$DIRTY" | tr -d '[:space:]\357\273\277')
printf '%s' "$CLEAN2" | base64 --decode > b.jks && echo "解码 OK -> $(wc -c < b.jks) bytes" || echo "解码 FAIL"

echo
echo "=== 场景 3: 开头带 UTF-8 BOM ==="
printf '\357\273\277' > bom.txt
printf '%s' "$RAW" >> bom.txt
BOM=$(cat bom.txt)
CLEAN3=$(printf '%s' "$BOM" | tr -d '[:space:]\357\273\277')
printf '%s' "$CLEAN3" | base64 --decode > c.jks && echo "解码 OK -> $(wc -c < c.jks) bytes" || echo "解码 FAIL"

echo
echo "=== 场景 4: 故意用 echo（老写法的 bug 来源） ==="
if printf '%s' "$RAW" | base64 --decode > /dev/null 2>&1; then
  echo "printf 方式: OK"
fi
if echo "$RAW" | base64 --decode > /dev/null 2>&1; then
  echo "echo 方式: OK（本机 base64 容忍行尾）"
else
  echo "echo 方式: FAIL（这正是 CI 上挂掉的可能原因）"
fi

echo
echo "=== keystore 口令校验（keytool 是否可用） ==="
if command -v keytool >/dev/null 2>&1; then
  echo "keytool 在 PATH: $(command -v keytool)"
elif [ -x "$JAVA_HOME_DIR/bin/keytool" ]; then
  echo "keytool 不在 PATH，但在 JAVA_HOME: $JAVA_HOME_DIR/bin/keytool"
else
  echo "keytool 找不到"
fi

KEYTOOL="$JAVA_HOME_DIR/bin/keytool"
if [ -x "$KEYTOOL" ]; then
  if "$KEYTOOL" -list -keystore a.jks -storepass "$PASSWORD" >/dev/null 2>&1; then
    echo "keytool 校验通过（口令正确）"
  else
    echo "keytool 校验失败"
  fi
fi

echo
echo "=== 三个文件哈希是否一致（证明清洗不影响内容） ==="
sha256sum a.jks b.jks c.jks 2>/dev/null | awk '{print $1}' | sort -u | wc -l | xargs -I{} echo "唯一哈希数: {} （应为 1）"

rm -rf "$WORK"
