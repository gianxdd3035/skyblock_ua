#!/usr/bin/env python3
"""
Перевірка словників: табуляція, дублікати, цифри поза {n} у ключах,
плейсхолдери в перекладі, яких немає в ключі, незакриті §-коди в кінці.
Запуск: python3 tools/check_dict.py
"""
import os, re, sys, glob
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
d = os.path.join(root, "src/main/resources/assets/skyblockua/dict")
NUM = re.compile(r'(?<![A-Za-z0-9])[+-]?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?(?:[kKmMbB](?![A-Za-z]))?%?')
PH = re.compile(r'\{(\d+|item2?|any2?)\}')
problems = 0
index = [l.strip() for l in open(os.path.join(d, "index.txt"), encoding="utf-8") if l.strip() and not l.startswith("#")]
for f in sorted(glob.glob(os.path.join(d, "*"))):
    name = os.path.basename(f)
    if name == "index.txt":
        continue
    if name not in index:
        print(f"{name}: not listed in index.txt"); problems += 1
    if not name.endswith(".tsv"):
        continue
    seen = {}
    for n, line in enumerate(open(f, encoding="utf-8"), 1):
        line = line.rstrip("\n")
        line = re.sub(r'\\u([0-9a-fA-F]{4})', lambda m: chr(int(m.group(1), 16)), line)
        if not line or line.startswith("#"):
            continue
        if "\t" not in line:
            print(f"{name}:{n}: no TAB: {line!r}"); problems += 1; continue
        k, v = line.split("\t", 1)
        k = " ".join(k.split())
        if k in seen and seen[k] != v:
            print(f"{name}:{n}: duplicate key with different value (line {seen[k]}): {k}"); problems += 1
        seen[k] = v
        stripped = PH.sub("", k)
        if NUM.search(stripped) and not name.startswith(("labels", "tokens")):
            print(f"{name}:{n}: digits in key outside {{n}}: {k}"); problems += 1
        kp = set(PH.findall(k)); vp = set(PH.findall(v))
        if not vp <= kp:
            print(f"{name}:{n}: value uses placeholders not in key: {k} -> {v}"); problems += 1
        if kp and vp != kp and not name.startswith(("labels", "tokens")):
            print(f"{name}:{n}: warning, value drops placeholders {kp - vp}: {k}")
        if v.rstrip().endswith("§"):
            print(f"{name}:{n}: dangling § at end"); problems += 1
print("OK" if problems == 0 else f"{problems} problems")
sys.exit(1 if problems else 0)
