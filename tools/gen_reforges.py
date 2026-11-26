#!/usr/bin/env python3
"""
Генерує reforges.tsv: рефорж -> 4 форми прикметника (ч, ж, с, мн).
Вручну задається лише чоловічий рід; решта утворюється за закінченням
(-ий -> -а/-е/-і, -ій -> -я/-є/-і, -ін -> -іна/-іне/-іні). Нестандартні випадки — в IRREGULAR.

Запуск: python3 tools/gen_reforges.py
"""
import os

MASC = {
    "Ambered": "Бурштиновий", "Ancient": "Стародавній", "Astute": "Проникливий",
    "Auspicious": "Сприятливий", "Awkward": "Незграбний", "Beady": "Бісерний",
    "Bizarre": "Химерний", "Blazing": "Палаючий", "Blended": "Змішаний",
    "Blessed": "Благословенний", "Blood-Soaked": "Закривавлений", "Bloodshot": "Кривавоокий",
    "Bloody": "Кривавий", "Blooming": "Квітучий", "Bountiful": "Щедрий",
    "Brilliant": "Блискучий", "Bulky": "Громіздкий", "Bustling": "Метушливий",
    "Buzzing": "Дзижчачий", "Calcified": "Звапнілий", "Candied": "Зацукрований",
    "Charitable": "Доброчинний", "Chomp": "Кусючий", "Clean": "Чистий",
    "Coldfused": "Холодносплавлений", "Colossal": "Колосальний", "Cozy": "Затишний",
    "Cubic": "Кубічний", "Deadly": "Смертоносний", "Demonic": "Демонічний",
    "Dimensional": "Міжвимірний", "Dirty": "Брудний", "Double-Bit": "Двосічний",
    "Earthy": "Земляний", "Empowered": "Посилений", "Epic": "Епічний",
    "Erratic": "Мінливий", "Erudite": "Ерудований", "Excellent": "Чудовий",
    "Fabled": "Казковий", "Fair": "Справедливий", "Fanged": "Ікластий",
    "Fast": "Швидкий", "Festive": "Святковий", "Fierce": "Лютий",
    "Fine": "Добротний", "Fleet": "Прудкий", "Forceful": "Потужний",
    "Fortified": "Укріплений", "Fortunate": "Щасливий", "Fruitful": "Плідний",
    "Gentle": "Лагідний", "Geometric": "Геометричний", "Giant": "Велетенський",
    "Gilded": "Позолочений", "Glacial": "Льодовиковий", "Glistening": "Мерехтливий",
    "Godly": "Богоподібний", "Grand": "Величний", "Great": "Великий",
    "Green Thumb": "Садівничий", "Groovy": "Ритмічний", "Hasty": "Квапливий",
    "Headstrong": "Упертий", "Heated": "Розпечений", "Heavy": "Важкий",
    "Hefty": "Масивний", "Heroic": "Героїчний", "Honored": "Шанований",
    "Hurtful": "Болючий", "Hyper": "Шалений", "Itchy": "Свербливий",
    "Jaded": "Нефритовий", "Jerry's": "Джеррін", "Keen": "Пильний",
    "Legendary": "Легендарний", "Light": "Легкий", "Loving": "Люблячий",
    "Lucky": "Везучий", "Lumberjack's": "Лісорубський", "Lunar": "Місячний",
    "Lush": "Буйний", "Lustrous": "Сяйливий", "Magnetic": "Магнітний",
    "Majestic": "Величавий", "Mantid": "Богомоловий", "Marshy": "Болотяний",
    "Menacing": "Загрозливий", "Mithraic": "Мітриловий", "Moil": "Натруджений",
    "Moonglade": "Місячногаєвий", "Mossy": "Замшілий", "Mythic": "Міфічний",
    "Neat": "Охайний", "Necrotic": "Некротичний", "Nimble": "Спритний",
    "Odd": "Дивний", "Ominous": "Зловісний", "Overpriced": "Переоцінений",
    "Peasant's": "Селянський", "Perfect": "Досконалий", "Pitchin'": "Закидний",
    "Pleasant": "Приємний", "Polished": "Відполірований", "Precise": "Точний",
    "Pretty": "Гарненький", "Prospector's": "Старательський", "Pure": "Непорочний",
    "Rapid": "Стрімкий", "Refined": "Вишуканий", "Reinforced": "Зміцнений",
    "Renowned": "Славетний", "Rich": "Багатий", "Ridiculous": "Безглуздий",
    "Robust": "Міцний", "Rooted": "Вкорінений", "Royal": "Королівський",
    "Rugged": "Суворий", "Salty": "Солоний", "Scraped": "Пошкрябаний",
    "Shaded": "Затінений", "Sharp": "Гострий", "Shiny": "Блискотливий",
    "Sighted": "Зрячий", "Silky": "Шовковий", "Simple": "Простий",
    "Smart": "Розумний", "Snowy": "Сніжний", "Soft": "М'який",
    "Spicy": "Пекучий", "Spiked": "Шипований", "Spiritual": "Духовний",
    "Squeaky": "Скрипучий", "Stained": "Заплямований", "Stellar": "Зоряний",
    "Sticky": "Липкий", "Stiff": "Тугий", "Strange": "Чудний",
    "Strengthened": "Загартований", "Strong": "Сильний", "Sturdy": "Кремезний",
    "Submerged": "Занурений", "Sunny": "Сонячний", "Superior": "Вищий",
    "Suspicious": "Підозрілий", "Sweaty": "Спітнілий", "Sweet": "Солодкий",
    "Thorny": "Колючий", "Titanic": "Титанічний", "Toil": "Працьовитий",
    "Toxic": "Токсичний", "Trashy": "Сміттєвий", "Treacherous": "Підступний",
    "Undead": "Неупокоєний", "Unpleasant": "Неприємний", "Unreal": "Нереальний",
    "Unyielding": "Непохитний", "Vivid": "Яскравий", "Warped": "Викривлений",
    "Waxed": "Вощений", "Wise": "Мудрий", "Wither": "Візерський",
    "Withered": "Зів'ялий", "Zealous": "Завзятий", "Zooming": "Летючий",
}

IRREGULAR = {}


def forms(m):
    if m in IRREGULAR:
        return IRREGULAR[m]
    out = [m]
    for g in "fnp":
        words = []
        for w in m.split(" "):
            if w.endswith("ий"):
                w = w[:-2] + {"f": "а", "n": "е", "p": "і"}[g]
            elif w.endswith("ій"):
                w = w[:-2] + {"f": "я", "n": "є", "p": "і"}[g]
            elif w.endswith("ін") or w.endswith("їн"):
                w = w + {"f": "а", "n": "е", "p": "і"}[g]
            words.append(w)
        out.append(" ".join(words))
    return out


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    path = os.path.join(root, "src/main/resources/assets/skyblockua/dict/reforges.tsv")
    with open(path, "w", encoding="utf-8") as f:
        f.write("# ЗГЕНЕРОВАНО tools/gen_reforges.py. Стовпці: рефорж, чоловічий, жіночий, середній, множина.\n")
        for en in sorted(MASC):
            f.write(en + "\t" + "\t".join(forms(MASC[en])) + "\n")
    print(len(MASC), "reforges ->", path)


if __name__ == "__main__":
    main()
