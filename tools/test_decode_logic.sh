#!/usr/bin/env bash
# 验证 CI 里新增的 base64 清洗/补位/解码逻辑，覆盖多种脏数据。
set -uo pipefail

B64_FILE="$1"
PASSWORD="$2"
JAVA_HOME_DIR="$3"

WORK=$(mktemp -d); cd "$WORK" || exit 1
RAW=$(cat "$B64_FILE")

clean_and_decode() {
  local INPUT="$1" LABEL="$2"
  local CLEAN="" BAD=0 BAD_LIST="" i ch
  for (( i=0; i<${#INPUT}; i++ )); do
    ch="${INPUT:i:1}"
    case "$ch" in
      [A-Za-z0-9+/=]) CLEAN="${CLEAN}${ch}" ;;
      *) BAD=$((BAD+1))
         if [ ${#BAD_LIST} -lt 20 ]; then BAD_LIST="${BAD_LIST}$(printf '0x%02X ' "'$ch")"; fi ;;
    esac
  done
  local REM=$(( ${#CLEAN} % 4 ))
  if [ "$REM" -eq 2 ]; then CLEAN="${CLEAN}=="
  elif [ "$REM" -eq 3 ]; then CLEAN="${CLEAN}="
  elif [ "$REM" -eq 1 ]; then echo "$LABEL -> 长度不合法（余 1）"; return 1; fi
  local OUT="$WORK/$LABEL.jks"
  if printf '%s' "$CLEAN" | base64 --decode > "$OUT" 2>/dev/null; then
    local MOD=$(( ${#INPUT} % 4 ))
    echo "$LABEL: 原始 ${#INPUT} -> 清洗 ${#CLEAN} (剔除 $BAD 个非法字符, 原始长度%4=$MOD) -> ${#CLEAN} 字符，解码 $(wc -c < "$OUT") 字节"
    echo "$OUT"
  else
    echo "$LABEL: 解码失败 (清洗后 ${#CLEAN} 字符)"
    return 1
  fi
}

echo "=== 场景覆盖 ==="
clean_and_decode "$RAW" "clean"
clean_and_decode "$(printf '%s\n' "$RAW")" "trailing_newline"
clean_and_decode "$(printf '\357\273\277%s' "$RAW")" "utf8_bom"
clean_and_decode "$(printf '%s' "$RAW" | sed 's/\(.\{80\}\)/\1\n   /g')" "wrapped_and_spaces"
clean_and_decode "$(printf '%s\r\n' "$RAW" | sed 's/$/\r/')" "crlf"
# 末尾补位符被吞掉的情况
clean_and_decode "$(printf '%s' "$RAW" | sed 's/=$//')" "stripped_one_pad"
clean_and_decode "$(printf '%s' "$RAW" | sed 's/==$//')" "stripped_two_pads"

echo
echo "=== 所有成功解出的文件哈希应完全一致 ==="
sha256sum "$WORK"/*.jks 2>/dev/null | awk '{print $1}' | sort -u > "$WORK/hashes.txt"
UNIQ=$(wc -l < "$WORK/hashes.txt")
FILES=$(ls "$WORK"/*.jks 2>/dev/null | wc -l)
echo "解出文件数: $FILES, 唯一哈希数: $UNIQ"
if [ "$UNIQ" -eq 1 ]; then echo "结论: 全部一致 ✓"; else echo "结论: 存在不一致 ✗"; fi

echo
echo "=== 用口令校验解出的 keystore ==="
KT="$JAVA_HOME_DIR/bin/keytool"
if [ -x "$KT" ]; then
  for f in "$WORK"/*.jks; do
    if "$KT" -list -keystore "$f" -storepass "$PASSWORD" >/dev/null 2>&1; then
      echo "  OK   $(basename "$f")"
    else
      echo "  FAIL $(basename "$f")"
    fi
  done
else
  echo "  未找到 keytool: $KT"
fi

rm -rf "$WORK"
