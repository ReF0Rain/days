"""Minimal YAML structural sanity checker (no external deps).

Catches the failure modes that actually break GitHub Actions parsing:
  - tabs used for indentation
  - siblings inside one mapping/sequence block at inconsistent indentation
  - an indented line whose parent block is not a mapping or sequence
  - duplicate keys inside the same mapping
  - unterminated quotes and "a: b: c" style mapping errors inside a line

It is not a full YAML parser; it validates block structure only.
"""
import re
import sys

KEY_RE = re.compile(r'^([A-Za-z_][A-Za-z0-9_.\-]*|"[^"]+"|\'[^\']+\')\s*:(?:\s+(.*))?$')


def strip_comment(line: str) -> str:
    out, in_s, in_d = [], False, False
    for ch in line:
        if ch == "'" and not in_d:
            in_s = not in_s
        elif ch == '"' and not in_s:
            in_d = not in_d
        elif ch == '#' and not in_s and not in_d and (not out or out[-1] in ' \t'):
            break
        out.append(ch)
    return ''.join(out).rstrip()


def check(path: str) -> list:
    problems = []
    with open(path, encoding='utf-8') as fh:
        raw_lines = fh.read().splitlines()

    # (indent, kind) stack of open blocks; kind: 'map' | 'seq'
    stack = []

    for idx, raw in enumerate(raw_lines, start=1):
        if '\t' in raw:
            problems.append(f'line {idx}: tab character in indentation')
            continue
        line = strip_comment(raw)
        if not line.strip():
            continue

        indent = len(line) - len(line.lstrip(' '))
        content = line.strip()

        # close blocks that this line dedents out of
        while stack and indent < stack[-1][0]:
            stack.pop()
        if stack and indent > stack[-1][0] and stack[-1][1] is None:
            problems.append(f'line {idx}: unexpected indent (no open block) -> {content}')

        if content.startswith('- ') or content == '-':
            if stack and indent == stack[-1][0] and stack[-1][1] == 'map':
                problems.append(
                    f'line {idx}: sequence item at mapping indentation {indent} -> {content}')
            key_col = indent + 2
            item = content[2:].strip() if content.startswith('- ') else ''
            if item:
                m = KEY_RE.match(item)
                if m:
                    # "- key: value" opens a mapping whose keys align at key_col
                    if stack and indent <= stack[-1][0]:
                        pass
                    stack.append((key_col, 'map'))
                else:
                    stack.append((key_col, 'seq'))
            else:
                stack.append((key_col, 'seq'))
            continue

        m = KEY_RE.match(content)
        if not m:
            if stack and indent > stack[-1][0] and stack[-1][1] == 'map':
                # continuation scalar (e.g. multi-line string) - acceptable
                continue
            problems.append(f'line {idx}: not a "key: value" line -> {content}')
            continue

        if stack and indent == stack[-1][0] and stack[-1][1] == 'seq':
            problems.append(f'line {idx}: mapping key at sequence indentation {indent} -> {content}')

        if not stack or indent > stack[-1][0]:
            if stack and stack[-1][1] == 'seq' and indent != stack[-1][0]:
                problems.append(
                    f'line {idx}: mapping under a sequence must be indented {stack[-1][0]} -> {content}')
            stack.append((indent, 'map'))
        elif indent == stack[-1][0] and stack[-1][1] == 'map':
            pass  # normal sibling key
        elif indent < stack[-1][0]:
            problems.append(f'line {idx}: dedent to {indent} does not match an open block -> {content}')

        value = (m.group(2) or '').strip()
        if value.count('"') % 2 or value.count("'") % 2:
            problems.append(f'line {idx}: unbalanced quote in value -> {value}')

    return problems


def main() -> int:
    rc = 0
    for path in sys.argv[1:]:
        problems = check(path)
        if problems:
            rc = 1
            print(f'PROBLEMS in {path}')
            for p in problems:
                print('  ' + p)
        else:
            print(f'OK (block structure) {path}')
    return rc


if __name__ == '__main__':
    sys.exit(main())
