#!/usr/bin/env python3
"""
Генерує рядки рідкості предмета: "LEGENDARY SWORD" -> "ЛЕГЕНДАРНИЙ МЕЧ".
Прикметник рідкості узгоджується з родом типу предмета (ч/ж/с/мн), тому всі комбінації
простіше згенерувати, ніж описувати правилом у моді.

Запуск: python3 tools/gen_rarity.py  (перезаписує src/main/resources/assets/skyblockua/dict/rarity.tsv)
"""
import os

# рідкість -> форми (чоловічий, жіночий, середній, множина)
RARITIES = {
    "COMMON": ("ЗВИЧАЙНИЙ", "ЗВИЧАЙНА", "ЗВИЧАЙНЕ", "ЗВИЧАЙНІ"),
    "UNCOMMON": ("НЕЗВИЧАЙНИЙ", "НЕЗВИЧАЙНА", "НЕЗВИЧАЙНЕ", "НЕЗВИЧАЙНІ"),
    "RARE": ("РІДКІСНИЙ", "РІДКІСНА", "РІДКІСНЕ", "РІДКІСНІ"),
    "EPIC": ("ЕПІЧНИЙ", "ЕПІЧНА", "ЕПІЧНЕ", "ЕПІЧНІ"),
    "LEGENDARY": ("ЛЕГЕНДАРНИЙ", "ЛЕГЕНДАРНА", "ЛЕГЕНДАРНЕ", "ЛЕГЕНДАРНІ"),
    "MYTHIC": ("МІФІЧНИЙ", "МІФІЧНА", "МІФІЧНЕ", "МІФІЧНІ"),
    "DIVINE": ("БОЖЕСТВЕННИЙ", "БОЖЕСТВЕННА", "БОЖЕСТВЕННЕ", "БОЖЕСТВЕННІ"),
    "SUPREME": ("ВЕРХОВНИЙ", "ВЕРХОВНА", "ВЕРХОВНЕ", "ВЕРХОВНІ"),
    "SPECIAL": ("ОСОБЛИВИЙ", "ОСОБЛИВА", "ОСОБЛИВЕ", "ОСОБЛИВІ"),
    "VERY SPECIAL": ("ДУЖЕ ОСОБЛИВИЙ", "ДУЖЕ ОСОБЛИВА", "ДУЖЕ ОСОБЛИВЕ", "ДУЖЕ ОСОБЛИВІ"),
    "ULTIMATE": ("УЛЬТИМАТИВНИЙ", "УЛЬТИМАТИВНА", "УЛЬТИМАТИВНЕ", "УЛЬТИМАТИВНІ"),
    "ADMIN": ("АДМІНСЬКИЙ", "АДМІНСЬКА", "АДМІНСЬКЕ", "АДМІНСЬКІ"),
}

# тип -> (переклад, рід: m/f/n/p)
TYPES = {
    "ITEM": ("ПРЕДМЕТ", "m"),
    "COSMETIC": ("КОСМЕТИКА", "f"),
    "ACCESSORY": ("АКСЕСУАР", "m"),
    "PET": ("УЛЮБЛЕНЕЦЬ", "m"),
    "HELMET": ("ШОЛОМ", "m"),
    "CHESTPLATE": ("НАГРУДНИК", "m"),
    "LEGGINGS": ("ПОНОЖІ", "p"),
    "BOOTS": ("ЧОБОТИ", "p"),
    "TROPHY": ("ТРОФЕЙ", "m"),
    "SWORD": ("МЕЧ", "m"),
    "LONGSWORD": ("ДОВГИЙ МЕЧ", "m"),
    "SLAYER SWORD": ("МЕЧ ВБИВЦІ", "m"),
    "BOW": ("ЛУК", "m"),
    "ARROW": ("СТРІЛА", "f"),
    "ARROW POISON": ("ОТРУТА ДЛЯ СТРІЛ", "f"),
    "WAND": ("ЖЕЗЛ", "m"),
    "GAUNTLET": ("ЛАТНА РУКАВИЦЯ", "f"),
    "PET ITEM": ("ПРЕДМЕТ УЛЮБЛЕНЦЯ", "m"),
    "REFORGE STONE": ("КАМІНЬ ПЕРЕКУВАННЯ", "m"),
    "POWER STONE": ("КАМІНЬ СИЛИ", "m"),
    "SEA CREATURE": ("МОРСЬКА ІСТОТА", "f"),
    "DYE": ("БАРВНИК", "m"),
    "SACK": ("МІШОК", "m"),
    "GEMSTONE": ("САМОЦВІТ", "m"),
    "RABBIT": ("КРОЛИК", "m"),
    "FARMING TOOL": ("ФЕРМЕРСЬКИЙ ІНСТРУМЕНТ", "m"),
    "BELT": ("ПОЯС", "m"),
    "NECKLACE": ("НАМИСТО", "n"),
    "CLOAK": ("ПЛАЩ", "m"),
    "GLOVES": ("РУКАВИЧКИ", "p"),
    "BRACELET": ("БРАСЛЕТ", "m"),
    "MUTATION": ("МУТАЦІЯ", "f"),
    "PORTAL": ("ПОРТАЛ", "m"),
    "ORE": ("РУДА", "f"),
    "BLOCK": ("БЛОК", "m"),
    "TRAVEL SCROLL": ("СУВІЙ ПОДОРОЖІ", "m"),
    "ABILITY SCROLL": ("СУВІЙ ЗДІБНОСТІ", "m"),
    "FISHING ROD": ("ВУДКА", "f"),
    "ROD PART": ("ДЕТАЛЬ ВУДКИ", "f"),
    "BAIT": ("НАЖИВКА", "f"),
    "FISHING NET": ("РИБАЛЬСЬКА СІТКА", "f"),
    "AXE": ("СОКИРА", "f"),
    "PICKAXE": ("КИРКА", "f"),
    "DRILL": ("БУР", "m"),
    "SHOVEL": ("ЛОПАТА", "f"),
    "HOE": ("МОТИКА", "f"),
    "SHEARS": ("НОЖИЦІ", "p"),
    "CHISEL": ("ЗУБИЛО", "n"),
    "MEMENTO": ("ПАМ'ЯТКА", "f"),
    "DEPLOYABLE": ("РОЗГОРТАНИЙ ПРЕДМЕТ", "m"),
    "DWARVEN METAL": ("ГНОМСЬКИЙ МЕТАЛ", "m"),
    "GARDEN CHIP": ("ЧІП САДУ", "m"),
    "BOOSTER": ("ПІДСИЛЮВАЧ", "m"),
    "RIFT TIMECHARM": ("ЧАСОВИЙ ОБЕРІГ РИФТУ", "m"),
    "SALT": ("СІЛЬ", "f"),
    "CARNIVAL MASK": ("КАРНАВАЛЬНА МАСКА", "f"),
    "VACUUM": ("ПИЛОСОС", "m"),
    "WATERING CAN": ("ЛІЙКА", "f"),
    "TRAP": ("ПАСТКА", "f"),
    "LASSO": ("ЛАСО", "n"),
    "CAPSULE": ("КАПСУЛА", "f"),
}

GENDER = {"m": 0, "f": 1, "n": 2, "p": 3}

# Типи, які трапляються з приставкою DUNGEON ("LEGENDARY DUNGEON SWORD").
DUNGEON = "ПІДЗЕМЕЛЬ"


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, "src/main/resources/assets/skyblockua/dict/rarity.tsv")
    rows = []
    # Тип окремо (так його показують мітки Iconographic: "dungeon sword", "chestplate").
    # Переклад зі звичайним регістром; якщо оригінал великими літерами, мод сам зробить великі.
    for t_en, (t_ua, g) in TYPES.items():
        rows.append((t_en, t_ua.capitalize()))
        rows.append((f"DUNGEON {t_en}", f"{t_ua.capitalize()} {DUNGEON.lower()}"))
    for r_en, forms in RARITIES.items():
        rows.append((r_en, forms[0]))
        for t_en, (t_ua, g) in TYPES.items():
            adj = forms[GENDER[g]]
            rows.append((f"{r_en} {t_en}", f"{adj} {t_ua}"))
            rows.append((f"{r_en} DUNGEON {t_en}", f"{adj} {t_ua} {DUNGEON}"))
    with open(out, "w", encoding="utf-8") as f:
        f.write("# ЗГЕНЕРОВАНО tools/gen_rarity.py — не редагуй вручну, змінюй скрипт.\n")
        for k, v in rows:
            f.write(f"{k}\t{v}\n")
    print(f"{len(rows)} rows -> {out}")


if __name__ == "__main__":
    main()
