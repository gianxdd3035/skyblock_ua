#!/usr/bin/env python3
"""
Витягує з NEU-REPO назви зачарувань і їхні описи (з книги найвищого рівня).
Вихід: work/enchants_work.tsv — "назва<TAB>рядок опису (з §-кодами, іконки як \\uXXXX)".
Запуск: python3 tools/neu_enchants.py <шлях до NEU items> <вихідний файл>
"""
import json, glob, os, re, sys

ROMAN = {'I': 1, 'II': 2, 'III': 3, 'IV': 4, 'V': 5, 'VI': 6, 'VII': 7, 'VIII': 8, 'IX': 9, 'X': 10}
strip = lambda s: re.sub('§.', '', s)
esc = lambda s: ''.join('\\u%04x' % ord(c) if 0xE000 <= ord(c) <= 0xF8FF else c for c in s)

items, out = sys.argv[1], sys.argv[2]
best = {}
for f in glob.glob(os.path.join(items, '*;*.json')):
    d = json.load(open(f, encoding='utf-8'))
    if strip(d.get('displayname', '')) != 'Enchanted Book':
        continue
    lore = d.get('lore', [])
    # рядок назви: перший рядок вигляду "Назва РИМСЬКЕ"
    for i, l in enumerate(lore):
        p = strip(l).strip()
        m = re.match(r'^([A-Z][A-Za-z\'\- ]+?) ([IVX]+)$', p)
        if m and m.group(2) in ROMAN:
            name, lvl = m.group(1), ROMAN[m.group(2)]
            desc = []
            for l2 in lore[i + 1:]:
                if not strip(l2).strip():
                    break
                desc.append(l2)
            if name not in best or lvl > best[name][0]:
                best[name] = (lvl, desc)
            break
with open(out, 'w', encoding='utf-8') as o:
    for name in sorted(best):
        o.write(name + '\n')
        for l in best[name][1]:
            o.write('\t' + esc(l) + '\n')
print(len(best), 'enchants ->', out)
