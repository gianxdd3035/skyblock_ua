package ua.kyrylo.skyblockua.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Рушій перекладу одного рядка.
 *
 * Порядок спроб (зупиняємося на першій, що спрацювала):
 *  1. Увесь рядок цілком (числа замінено на {0}, {1}...). Для назв — ще й назва броні,
 *     складена з частини й сету ("Superior Dragon Helmet" -> "Шолом Вищого Дракона").
 *  2. Рядок без "прикрас": зірок апгрейду, "a ... a" рекомбобулятора, "[Lvl 100]" пета, римського рівня,
 *     а для назв — без рефоржу на початку. Рефорж тоді перекладається в роді предмета:
 *     "Ancient Superior Dragon Boots" -> "Стародавні чоботи Вищого Дракона".
 *  3. Діалог NPC: "[NPC] Ім'я: текст" — перекладається тільки текст.
 *  4. Підпис перед двокрапкою ("Strength: +50 (+10)" -> "Сила: +50 (+10)") і окремі слова-токени ("RIGHT CLICK").
 *  5. Шматок за шматком, якщо рядок складається з кількох частин зі своїми кліками (кнопки в чаті).
 * Якщо нічого не спрацювало — рядок записується в список неперекладених.
 */
public final class Translator<S> {

    /*
     * Звідки рядок: від цього залежить, які розділи словника дивитися.
     * ANY — рядок тултіпа, про який невідомо, назва це чи опис (так буває, коли тултіп малює інший мод).
     */
    public enum Kind { NAME, LORE, CHAT, TITLE, ANY }

    private static final Pattern NPC_PREFIX = Pattern.compile("^\\[NPC] [^:]{1,40}: ");
    private static final Pattern LABEL = Pattern.compile("^([A-Za-z0-9][A-Za-z0-9 '\\-.()&/]{0,40}?):(?= |$)");
    private static final Pattern VALUE_FIRST = Pattern.compile("^[+-]?\\d[\\d,.]*[%x]?[^A-Za-z]{0,3}?\\s*([A-Za-z][A-Za-z ']*[A-Za-z])$");
    /* Номер сторінки меню: "(2/5) Pets" */
    private static final Pattern PAGE_PREFIX = Pattern.compile("^\\(\\d+/\\d+\\) ");
    private static final String CRUMB = " \u279C ";
    private static final Pattern PET_LEVEL = Pattern.compile("^\\[Lvl \\d+] ");
    private static final Pattern RECOMB = Pattern.compile("^a (.+) a$");
    private static final Pattern BULLET = Pattern.compile("^(?:- |• |▶ )");
    private static final Pattern ROMAN_SUFFIX = Pattern.compile(" \\([IVX]+(?:-[IVX]+)?\\)$");
    /* Лічильник повторів, який дописують моди на стискання чату: "(2)", "(×3)" */
    private static final Pattern CHAT_COUNTER = Pattern.compile(" \\((?:\u00D7|x)?\\d+\\)$");
    /* Капсовий префікс повідомлення: "RARE DROP! ...", "TROPHY FROG! ...", "SLAYER QUEST STARTED!" */
    private static final Pattern CAPS_PREFIX = Pattern.compile("^([A-Z0-9][A-Z0-9'\u2019\\- ]{1,30}[!:]) (.+)$");
    /* "Прикраси" на початку, без пробілу після них: "✦MYTHIC✦" */
    private static final Pattern LEADING_DECOR = Pattern.compile("^[^\\p{L}\\p{N}\\[(\\s\"'\\-]+\\s*(?=\\p{L})");
    /* Рядок зачарувань: "Sharpness VI, Critical VI, Cubism V" (як у Iconographic) */
    private static final Pattern ENCHANT_LIST = Pattern.compile("^(?:[A-Z][A-Za-z'\\-]*(?: [A-Za-z'\\-]+)* [IVXLCDM]+(?:\u2192[IVXLCDM]+)?(?: [\\d.,]+)?(?:, |$))+$");
    private static final Pattern ENCHANT_ITEM = Pattern.compile("(?<=^|, )([A-Z][A-Za-z'\\-]*(?: [A-Za-z'\\-]+)*) ([IVXLCDM]+(?:\u2192[IVXLCDM]+)?)(?: [\\d.,]+)?(?=,|$)");
    private static final Pattern ROMAN_TAIL = Pattern.compile(" [IVXLCDM]+(?:\u2192[IVXLCDM]+)?$");
    private static final Pattern ROMAN_WORD = Pattern.compile(" (?:Mk\\. )?[IVXL]+$");
    /* "Прикраси" в кінці: зірки ✪, ➊–➎ та інші символи, що не є літерами, цифрами чи дужками. */
    /* Тег зачарування від Iconographic: назва, далі службові значки шрифту з уставленим рівнем ("VII") */
    private static final Pattern GLYPH_TAIL = Pattern.compile("^([^\uE000-\uF8FF]*?\\p{L})([\uE000-\uF8FF][\uE000-\uF8FF\\sIVXLCDM0-9{},./:+\\-]*)$");
    /* Підписи, після яких іде назва здібності чи бонусу: "Ability: Wither Impact  RIGHT CLICK" */
    private static final Pattern ABILITY_LABEL = Pattern.compile(".*(?:Ability|Bonus)$");
    private static final Pattern ABILITY_TAIL = Pattern.compile(
            "(?:\\s+(?:(?:SNEAK|SHIFT|DOUBLE|HOLD|ON)\\s+)*(?:(?:LEFT/RIGHT|RIGHT|LEFT|MIDDLE)\\s+)?(?:CLICK|TAP JUMP|DIG|SHOOT)(?:\\s+HOLD)?)?(?:\\s+\\(\\d+/\\d+\\))?[^\\p{L}\\p{N}!?'.)]*$");
    private static final Pattern VALUE_LAST = Pattern.compile("^[^\\p{L}]*?([A-Za-z][A-Za-z ']*[A-Za-z]) [+-]?[\\d,.]+%?$");
    private static final Pattern ROMAN_LABEL = Pattern.compile(" [IVXLCDM]+$");
    private static final Pattern ENGLISH_WORD = Pattern.compile("(?<![A-Za-z])[A-Za-z]{3,}(?![A-Za-z])");
    private static final Pattern TRAILING_DECOR = Pattern.compile("[^\\p{L}0-9)\\]!?.'\"]+$");

    /* Перекладена назва предмета разом із її родом. */
    private record NameHit(String text, char gender, boolean fromArmor) {
    }

    private final StyleOps<S> ops;
    private final Missing missing = new Missing();
    private volatile Dict dict = new Dict();

    public volatile boolean translateNames = true;
    public volatile boolean collectMissing = true;
    public volatile boolean translateNpcs = true;

    public Translator(StyleOps<S> ops) {
        this.ops = ops;
    }

    public void setDict(Dict dict) {
        this.dict = dict;
    }

    public Dict dict() {
        return dict;
    }

    public Missing missing() {
        return missing;
    }

    /* Перекласти рядок. Повертає новий рядок або null, якщо перекладати нічого. */
    public List<Seg<S>> translate(List<Seg<S>> line, Kind kind) {
        if (line.isEmpty()) return null;
        String plain = Lines.plain(line);
        int start = 0;
        int end = plain.length();
        while (start < end && Character.isWhitespace(plain.charAt(start))) start++;
        while (end > start && Character.isWhitespace(plain.charAt(end - 1))) end--;
        if (end - start < 2) return null;
        String core = plain.substring(start, end);
        if (!hasLatinLetter(core) || hasCyrillic(core)) return null;
        Dict d = dict;
        boolean nameLike = kind == Kind.NAME || kind == Kind.ANY;

        /* 1. Увесь рядок */
        List<Seg<S>> r = wholeLookup(line, start, end, core, kind, d);
        if (r != null) return applyTokens(r, d);

        /* 1б. Шаблони з підстановками ("You dropped {0}x {item}!", "Enchanted Book ({item})") */
        r = patternLookup(line, start, end, core, kind, d);
        if (r != null) return r;

        /* 2. Без прикрас */
        r = peeled(line, start, end, core, kind, d);
        if (r != null) return applyTokens(r, d);

        /* 2б. Шлях у меню: "Bestiary ➜ Spider's Den" — кожну частину окремо, невідомі (імена мобів) лишаються */
        if (core.contains(CRUMB)) {
            r = breadcrumb(line, start, core, d);
            if (r != null) return r;
        }

        /* 3. Чат: без лічильника повторів, капсовий префікс окремо, діалог NPC */
        if (kind == Kind.CHAT) {
            Matcher counter = CHAT_COUNTER.matcher(core);
            if (counter.find() && counter.start() > 0) {
                int cutEnd = start + counter.start();
                List<Seg<S>> head = new ArrayList<>();
                List<Seg<S>> tail = new ArrayList<>();
                splitAt(line, cutEnd, head, tail);
                List<Seg<S>> translatedHead = translateQuiet(head, kind);
                if (translatedHead != null) {
                    translatedHead.addAll(tail);
                    return translatedHead;
                }
            }
            r = capsPrefix(line, start, end, core, kind, d);
            if (r != null) return r;
        }
        if (kind == Kind.CHAT) {
            Matcher m = NPC_PREFIX.matcher(core);
            if (m.find()) {
                int bodyStart = start + m.end();
                r = rangeLookup(line, bodyStart, end, plain.substring(bodyStart, end), kind, d);
                /* ім'я NPC у префіксі "[NPC] Jacob:" */
                List<Seg<S>> named = npcPrefixName(r != null ? r : line, start, core, d);
                if (r != null) return named != null ? named : r;
                if (named != null) {
                    if (collectMissing) missing.record(kind, Template.of(core).key, Lines.legacy(line, ops));
                    return named;
                }
            }
        }

        /* 3б. Рядок зачарувань: перекладаємо кожну назву окремо, рівні й кольори лишаються */
        if (kind == Kind.LORE || kind == Kind.ANY) {
            r = enchantList(line, start, core, d);
            if (r != null) return r;
        }

        /* 4. Підпис перед двокрапкою + токени (у чаті токени не застосовуємо: вийшли б напівперекладені фрази) */
        if (kind == Kind.CHAT) {
            List<Seg<S>> labelled = labelRule(line, start, end, core, d);
            if (labelled != null) return labelled;
        } else if (kind != Kind.NAME) {
            List<Seg<S>> labelled = labelRule(line, start, end, core, d);
            List<Seg<S>> base = labelled != null ? labelled : line;
            List<Seg<S>> tokens = applyTokens(base, d);
            if (labelled != null || tokens != base) {
                /* переклад частковий (лишились англійські слова) — теж збираємо, щоб доперекласти */
                if (collectMissing && ENGLISH_WORD.matcher(Lines.plain(tokens)).find()) {
                    missing.record(kind, Template.of(core).key, Lines.legacy(line, ops));
                }
                return tokens;
            }
        } else {
            List<Seg<S>> tokens = applyTokens(line, d);
            if (tokens != line) return tokens;
        }

        /*
         * 5. Шматок за шматком — лише для чату (кнопки на кшталт "[Yes] [No]" з окремими кліками).
         * В описах так не робимо: вийшли б напівперекладені рядки.
         */
        if (kind == Kind.CHAT) {
            r = segmentWise(line, kind, d);
            if (r != null) return r;
        }

        if (collectMissing && !(nameLike && kind == Kind.ANY && core.length() < 3)) {
            missing.record(kind, Template.of(core).key, Lines.legacy(line, ops));
        }
        return null;
    }

    /*
     * Пошук цілого фрагмента. Для назв спершу пробуємо назву предмета (з родом), потім рядки меню;
     * для решти — навпаки.
     */
    private List<Seg<S>> wholeLookup(List<Seg<S>> line, int s, int e, String text, Kind kind, Dict d) {
        if (kind == Kind.NAME) {
            NameHit hit = resolveName(text, d);
            if (hit != null) return Lines.replaceRange(line, s, e, hit.text(), ops);
            return rangeLookup(line, s, e, text, kind, d);
        }
        List<Seg<S>> r = rangeLookup(line, s, e, text, kind, d);
        if (r != null || kind != Kind.ANY) return r;
        NameHit hit = resolveName(text, d);
        return hit == null ? null : Lines.replaceRange(line, s, e, hit.text(), ops);
    }

    /* Знайти переклад для тексту з діапазону [s, e) і вставити його на місце. */
    private List<Seg<S>> rangeLookup(List<Seg<S>> line, int s, int e, String text, Kind kind, Dict d) {
        Template t = Template.of(text);
        String value = lookup(kind, t.key, d);
        if (value == null) return null;
        /*
         * Переклад без §-кодів, а оригінал різнокольоровий ("Grants +5 ❣ Charm Chance."):
         * числа, значки статів і перенесені англійські слова зберігають свої кольори.
         */
        if (value.indexOf('\u00A7') < 0 && !uniformStyle(line, s, e)) {
            String v = isAllCaps(text) ? value.toUpperCase(Locale.ROOT) : value;
            Restyled<S> rs = restyle(line, s, e, text, v);
            if (rs != null) {
                List<Seg<S>> r = Lines.replaceRange(line, s, e, rs.text(), ops, rs.base(), rs.slots());
                if (r != null) return r;
            }
        }
        String filled = t.fill(value);
        if (filled == null) return null;
        /* Оригінал великими літерами ("LEGION", "DUNGEON SWORD") — переклад теж великими */
        if (isAllCaps(text)) filled = filled.toUpperCase(Locale.ROOT);
        if (kind == Kind.CHAT && filled.indexOf('\u00A7') < 0) {
            return Lines.replaceRange(line, s, e, filled, ops, Lines.dominantStyle(line, s, e));
        }
        return Lines.replaceRange(line, s, e, filled, ops);
    }

    private record Restyled<S>(String text, S base, List<List<Seg<S>>> slots) {}

    private boolean uniformStyle(List<Seg<S>> line, int s, int e) {
        String first = null;
        int pos = 0;
        for (Seg<S> seg : line) {
            String t = seg.text();
            int a = Math.max(s, pos), b = Math.min(e, pos + t.length());
            pos += t.length();
            if (a >= b || t.substring(a - (pos - t.length()), b - (pos - t.length())).isBlank()) continue;
            String codes = ops.legacy(seg.style());
            if (first == null) first = codes;
            else if (!first.equals(codes)) return false;
        }
        return true;
    }

    /* Значок стату Hypixel або Iconographic: ❤ ❈ ❁ ✎ ☣ ❣ ☘ ⸕, символи шрифту (U+E000-U+F8FF), стрілка ➜ */
    private static boolean isIcon(char c) {
        int type = Character.getType(c);
        return type == Character.OTHER_SYMBOL || type == Character.PRIVATE_USE || c == '\u2E15' || c == '\u2726';
    }

    /*
     * Розфарбувати переклад за оригіналом:
     * - основний колір — той, яким написано більшість літер поза числами й кольоровими "значок + назва";
     * - кожне {n} — число з оригіналу з усіма його кольорами;
     * - значок (❣) і стільки ж слів після нього, скільки їх було в оригіналі тим самим кольором;
     * - англійські слова, перенесені без перекладу (SkyBlock, імена), — своїм кольором.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Restyled<S> restyle(List<Seg<S>> line, int s, int e, String text, String value) {
        List<int[]> nums = new ArrayList<>();
        Matcher nm = Template.NUMBER.matcher(text);
        while (nm.find()) nums.add(new int[]{s + nm.start(), s + nm.end()});

        /* кольорові відрізки "значок + слова" в оригіналі не впливають на основний колір */
        boolean[] excluded = new boolean[e - s];
        for (int[] n : nums) for (int i = n[0]; i < n[1]; i++) excluded[i - s] = true;
        for (int i = 0; i < text.length(); i++) {
            if (!isIcon(text.charAt(i))) continue;
            String codes = ops.legacy(Lines.styleAt(line, s + i));
            int j = i;
            while (j < text.length() && ops.legacy(Lines.styleAt(line, s + j)).equals(codes)
                    && (j == i || !isIcon(text.charAt(j)))) {
                excluded[j] = true;
                j++;
            }
        }
        java.util.Map<String, Integer> letters = new java.util.LinkedHashMap<>();
        java.util.Map<String, S> styles = new java.util.HashMap<>();
        for (int i = 0; i < text.length(); i++) {
            if (excluded[i] || !Character.isLetter(text.charAt(i))) continue;
            S st = Lines.styleAt(line, s + i);
            String codes = ops.legacy(st);
            letters.merge(codes, 1, Integer::sum);
            styles.putIfAbsent(codes, st);
        }
        S base = Lines.styleAt(line, s);
        int max = -1;
        for (java.util.Map.Entry<String, Integer> en : letters.entrySet()) {
            if (en.getValue() > max) {
                max = en.getValue();
                base = styles.get(en.getKey());
            }
        }
        String baseCodes = ops.legacy(base);

        /* текст без §-кодів (основний стиль іде від base) + слоти з оригінальними шматками */
        StringBuilder out = new StringBuilder();
        List<List<Seg<S>>> slots = new ArrayList<>();
        java.util.Map<Character, Integer> iconSeen = new java.util.HashMap<>();
        String plainLine = Lines.plain(line);
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            Matcher pm = Template.PLACEHOLDER.matcher(value);
            if (c == '{' && pm.find(i) && pm.start() == i) {
                int idx = Integer.parseInt(pm.group(1));
                if (idx >= nums.size()) return null;
                int ns = nums.get(idx)[0], ne = nums.get(idx)[1];
                int after = pm.end();
                /* множник "2x" / "x2" фарбуємо разом із числом */
                boolean xAfter = after < value.length() && value.charAt(after) == 'x' && ne < e && plainLine.charAt(ne) == 'x';
                boolean xBefore = out.length() > 0 && out.charAt(out.length() - 1) == 'x' && ns > s && plainLine.charAt(ns - 1) == 'x';
                if (xBefore) {
                    out.setLength(out.length() - 1);
                    ns--;
                }
                if (xAfter) {
                    ne++;
                    after++;
                }
                out.append(slot(slots, Lines.slice(line, ns, ne)));
                i = after;
                continue;
            }
            if (isIcon(c)) {
                int occurrence = iconSeen.merge(c, 1, Integer::sum);
                int pos = nthIndex(text, c, occurrence);
                if (pos >= 0) {
                    S iconStyle = Lines.styleAt(line, s + pos);
                    String codes = ops.legacy(iconStyle);
                    if (!codes.equals(baseCodes)) {
                        int words = 0;
                        boolean inWord = false;
                        for (int k = pos + 1; k < text.length() && ops.legacy(Lines.styleAt(line, s + k)).equals(codes); k++) {
                            boolean letter = Character.isLetter(text.charAt(k));
                            if (letter && !inWord) words++;
                            inWord = letter || (inWord && (text.charAt(k) == '\'' || text.charAt(k) == '-'));
                        }
                        int j = i + 1;
                        int counted = 0;
                        inWord = false;
                        while (j < value.length()) {
                            char ch = value.charAt(j);
                            if (".,;:!?)(".indexOf(ch) >= 0 || ch == '{' || isIcon(ch)) break;
                            boolean letter = Character.isLetter(ch) || (inWord && (ch == '\'' || ch == '-'));
                            if (letter && !inWord) {
                                if (counted == words) break;
                                counted++;
                            }
                            inWord = letter;
                            j++;
                        }
                        while (j > i + 1 && value.charAt(j - 1) == ' ') j--;
                        List<Seg<S>> run = new ArrayList<>();
                        run.add(new Seg<>(value.substring(i, j), iconStyle));
                        out.append(slot(slots, run));
                        i = j;
                        continue;
                    }
                }
            }
            if (c < 128 && Character.isLetter(c) && (i == 0 || !Character.isLetter(value.charAt(i - 1)))) {
                int j = i;
                while (j < value.length() && value.charAt(j) < 128 && Character.isLetter(value.charAt(j))) j++;
                String word = value.substring(i, j);
                Matcher wm = Pattern.compile("(?<![A-Za-z])" + Pattern.quote(word) + "(?![A-Za-z])").matcher(text);
                if (word.length() >= 2 && wm.find()
                        && !ops.legacy(Lines.styleAt(line, s + wm.start())).equals(baseCodes)) {
                    out.append(slot(slots, Lines.slice(line, s + wm.start(), s + wm.end())));
                } else {
                    out.append(word);
                }
                i = j;
                continue;
            }
            out.append(c);
            i++;
        }
        if (slots.size() > 32) return null;
        return new Restyled(out.toString(), base, slots);
    }

    /* Додати слот і повернути його позначку для тексту перекладу */
    private static <T> char slot(List<List<Seg<T>>> slots, List<Seg<T>> segs) {
        slots.add(segs);
        return (char) (Lines.SLOT0 + slots.size() - 1);
    }

    private static int nthIndex(String text, char c, int n) {
        int idx = -1;
        for (int k = 0; k < n; k++) {
            int next = text.indexOf(c, idx + 1);
            if (next < 0) return idx;
            idx = next;
        }
        return idx;
    }

    /*
     * Назви предметів шукаються в розділі names (якщо назви увімкнені) і в lines
     * (там лежать назви кнопок меню: "Close", "Go Back" — їх перекладаємо завжди).
     */
    private String lookup(Kind kind, String key, Dict d) {
        String v = null;
        if (kind == Kind.NAME && translateNames) v = d.get(Dict.Section.NAMES, key);
        if (v == null) v = d.get(Dict.Section.LINES, key);
        /* імена NPC у меню (відвідувачі Саду, контакти Abiphone, заголовки крамниць) */
        if (v == null && translateNpcs && kind != Kind.CHAT && kind != Kind.LORE) v = d.get(Dict.Section.NPCS, key);
        return v;
    }

    /*
     * Перекласти назву предмета й визначити її рід.
     * Спершу словник назв, потім збірна назва броні "<сет> <частина>".
     */
    private NameHit resolveName(String text, Dict d) {
        if (!translateNames) return null;
        Template t = Template.of(text);
        String v = d.get(Dict.Section.NAMES, t.key);
        if (v != null) {
            String filled = t.fill(v);
            if (filled == null) return null;
            char g = d.nameGender(t.key);
            if (g == 0) g = UaGender.guess(filled);
            return new NameHit(filled, g, false);
        }
        for (Dict.Piece piece : d.pieces()) {
            String suffix = " " + piece.english();
            if (text.length() <= suffix.length() || !text.regionMatches(true, text.length() - suffix.length(), suffix, 0, suffix.length())) {
                continue;
            }
            String set = text.substring(0, text.length() - suffix.length());
            String setUa = d.get(Dict.Section.ARMOR_SETS, set);
            if (setUa == null) continue;
            String name;
            if (setUa.startsWith("adj:")) {
                /* прикметник перед частиною: "Алмазні чоботи" */
                name = capitalize(UaGender.adjective(setUa.substring(4).strip(), piece.gender())) + " " + piece.ukrainian();
            } else {
                /* сет після частини в родовому відмінку: "Чоботи Вищого Дракона" */
                name = capitalize(piece.ukrainian()) + " " + setUa;
            }
            return new NameHit(name, piece.gender(), true);
        }
        return null;
    }

    private List<Seg<S>> peeled(List<Seg<S>> line, int start, int end, String core, Kind kind, Dict d) {
        int s = start;
        int e = end;
        String mid = core;
        boolean changed = false;
        boolean nameLike = kind == Kind.NAME || kind == Kind.ANY;

        Matcher m = RECOMB.matcher(mid);
        if (m.matches()) {
            s += 2;
            e -= 2;
            mid = m.group(1);
            changed = true;
        }
        m = GLYPH_TAIL.matcher(mid);
        if (m.matches()) {
            e -= mid.length() - m.end(1);
            mid = m.group(1);
            changed = true;
        }
        m = PAGE_PREFIX.matcher(mid);
        if (m.find() && m.end() < mid.length()) {
            s += m.end();
            mid = mid.substring(m.end());
            changed = true;
        }
        m = PET_LEVEL.matcher(mid);
        if (m.find()) {
            s += m.end();
            mid = mid.substring(m.end());
            changed = true;
        }
        if (mid.startsWith("⚚ ")) {
            s += 2;
            mid = mid.substring(2);
            changed = true;
        }
        /* Маркер пункту списку: "- Sword", "▶ ..." */
        m = BULLET.matcher(mid);
        if (m.find() && m.end() < mid.length()) {
            s += m.end();
            mid = mid.substring(m.end());
            changed = true;
        }
        m = LEADING_DECOR.matcher(mid);
        if (m.find() && m.end() < mid.length()) {
            s += m.end();
            mid = mid.substring(m.end());
            changed = true;
        }
        m = TRAILING_DECOR.matcher(mid);
        if (m.find() && m.start() > 0) {
            int cut = m.start();
            while (cut > 0 && mid.charAt(cut - 1) == ' ') cut--;
            if (cut > 0) {
                e -= mid.length() - cut;
                mid = mid.substring(0, cut);
                changed = true;
            }
        }
        /* Рівні в дужках у кінці: "Bazaar (I-V)" -> шукаємо "Bazaar", дужки лишаються як є */
        m = ROMAN_SUFFIX.matcher(mid);
        if (m.find() && m.start() > 0) {
            e -= mid.length() - m.start();
            mid = mid.substring(0, m.start());
            changed = true;
        }
        /* Римський рівень у кінці назви: "Cobblestone Minion XI" -> шукаємо "Cobblestone Minion" */
        if (nameLike) {
            m = ROMAN_WORD.matcher(mid);
            if (m.find() && m.start() > 0) {
                List<Seg<S>> r = wholeLookup(line, s, e - (mid.length() - m.start()), mid.substring(0, m.start()), kind, d);
                if (r != null) return r;
            }
        }
        if (changed && mid.length() >= 2) {
            List<Seg<S>> r = wholeLookup(line, s, e, mid, kind, d);
            if (r != null) return r;
            r = patternLookup(line, s, e, mid, kind, d);
            if (r != null) return r;
        }

        /*
         * Рефорж — перше (або перші два) слова назви. Відкидаємо його лише якщо решта назви
         * перекладається, і тоді ставимо рефорж у роді перекладеної назви.
         */
        if (nameLike && translateNames) {
            String[] words = mid.split(" ");
            for (int n = Math.min(2, words.length - 1); n >= 1; n--) {
                String reforge = String.join(" ", java.util.Arrays.copyOfRange(words, 0, n));
                if (!d.isReforge(reforge)) continue;
                String rest = mid.substring(reforge.length() + 1);
                NameHit hit = resolveName(rest, d);
                /* другий префікс: "Shiny Heroic Hyperion" -> "Shiny" + "Heroic" + "Hyperion" */
                String second = null;
                if (hit == null) {
                    int sp = rest.indexOf(' ');
                    if (sp > 0 && d.isReforge(rest.substring(0, sp))) {
                        second = rest.substring(0, sp);
                        hit = resolveName(rest.substring(sp + 1), d);
                    }
                }
                if (hit == null) return null;
                String form = d.reforgeForm(reforge, hit.gender());
                StringBuilder value = new StringBuilder(form == null || form.isEmpty() ? reforge : form);
                if (second != null) {
                    String form2 = d.reforgeForm(second, hit.gender());
                    value.append(' ').append(form2 == null || form2.isEmpty() ? second : decapitalize(form2));
                }
                value.append(' ').append(hit.fromArmor() ? decapitalize(hit.text()) : hit.text());
                return Lines.replaceRange(line, s, e, value.toString(), ops);
            }
        }
        return null;
    }

    /*
     * "RARE DROP! You dropped 2x Enchanted Carrot!" — префікс і решту перекладаємо окремо,
     * кожну частину в її власному кольорі.
     */
    private List<Seg<S>> capsPrefix(List<Seg<S>> line, int start, int end, String core, Kind kind, Dict d) {
        Matcher m = CAPS_PREFIX.matcher(core);
        if (!m.matches()) return null;
        String prefix = m.group(1);
        int restStart = start + m.start(2);
        String rest = m.group(2);
        /* спершу решта (справа), потім префікс — так індекси префікса не зсуваються */
        List<Seg<S>> current = line;
        boolean any = false;
        List<Seg<S>> r = rangeLookup(current, restStart, end, rest, kind, d);
        if (r == null) r = patternLookup(current, restStart, end, rest, kind, d);
        if (r == null) {
            Matcher counter = CHAT_COUNTER.matcher(rest);
            if (counter.find() && counter.start() > 0) {
                r = rangeLookup(current, restStart, restStart + counter.start(), rest.substring(0, counter.start()), kind, d);
            }
        }
        if (r != null) {
            current = r;
            any = true;
        }
        String prefixUa = d.get(Dict.Section.LINES, prefix);
        if (prefixUa != null) {
            List<Seg<S>> p = Lines.replaceRange(current, start, start + prefix.length(), prefixUa, ops);
            if (p != null) {
                current = p;
                any = true;
            }
        }
        return any ? current : null;
    }

    /*
     * Шаблон із підстановками. Кожна підставлена частина зберігає свій колір:
     * перед нею вставляються §-коди її стилю, після — коди основного тексту.
     */
    private List<Seg<S>> patternLookup(List<Seg<S>> line, int s, int e, String text, Kind kind, Dict d) {
        patterns:
        for (PatternEntry p : d.patterns()) {
            Matcher m = p.regex.matcher(text);
            if (!m.matches()) continue;
            S base = literalStyle(line, s, m, p.groups.size());
            String value = p.value;
            List<List<Seg<S>>> slots = new ArrayList<>();
            for (int i = 0; i < p.groups.size(); i++) {
                String name = p.groups.get(i);
                int gs = s + m.start(i + 1), ge = s + m.end(i + 1);
                String captured = m.group(i + 1);
                List<Seg<S>> segs = null;
                if (name.startsWith("item") || name.startsWith("known")) {
                    /* назва може починатися з маркера списку чи значка ("▶ ", "✦") — його лишаємо як є */
                    Matcher lead = BULLET.matcher(captured);
                    int skip = lead.lookingAt() ? lead.end() : 0;
                    Matcher decor = LEADING_DECOR.matcher(captured.substring(skip));
                    if (decor.lookingAt()) skip += decor.end();
                    String core = captured.substring(skip).strip();
                    String tr = lookupItem(core, d);
                    if (tr == null) {
                        /* "Smite VI" -> "Кара VI" */
                        Matcher roman = ROMAN_TAIL.matcher(core);
                        if (roman.find() && roman.start() > 0) {
                            String head = lookupItem(core.substring(0, roman.start()), d);
                            if (head != null) tr = head + core.substring(roman.start());
                        }
                    }
                    if (tr != null) {
                        segs = new ArrayList<>(Lines.slice(line, gs, gs + skip));
                        Lines.appendLegacy(segs, tr, Lines.styleAt(line, gs + skip), ops);
                    } else if (name.startsWith("known")) {
                        /* загальний шаблон ("{known} or higher!") лише для відомих назв, інакше вийде напівпереклад */
                        continue patterns;
                    }
                }
                if (name.startsWith("stat")) {
                    /* "\u2741 Strength" -> значок лишається, назва — у родовому відмінку ("сили") */
                    int skip = 0;
                    while (skip < captured.length() && !Character.isLetter(captured.charAt(skip))) skip++;
                    String stat = d.get(Dict.Section.STATS, captured.substring(skip).strip());
                    if (stat == null) continue patterns;
                    segs = new ArrayList<>(Lines.slice(line, gs, gs + skip));
                    segs.add(new Seg<>(stat, Lines.styleAt(line, Math.min(gs + skip, ge - 1))));
                }
                /* числа, {any} і невідомі назви — без змін, разом з усіма їхніми стилями */
                if (segs == null) segs = Lines.slice(line, gs, ge);
                value = value.replace("{" + name + "}", String.valueOf(slot(slots, segs)));
            }
            List<Seg<S>> r = Lines.replaceRange(line, s, e, value, ops, base, slots);
            if (r != null) return r;
        }
        return null;
    }

    private List<Seg<S>> npcPrefixName(List<Seg<S>> line, int start, String core, Dict d) {
        if (!translateNpcs) return null;
        int nameStart = "[NPC] ".length();
        int colon = core.indexOf(": ", nameStart);
        if (colon <= nameStart) return null;
        String ua = d.get(Dict.Section.NPCS, core.substring(nameStart, colon).strip());
        if (ua == null) return null;
        return Lines.replaceRange(line, start + nameStart, start + colon, ua, ops);
    }

    /*
     * Підпис над головою (голограма NPC): лише точний збіг з іменем NPC або короткою підказкою ("CLICK").
     * Імена мобів і гравців не чіпаємо й у файл неперекладеного не пишемо.
     */
    public List<Seg<S>> translateTag(List<Seg<S>> line) {
        Dict d = dict;
        if (d == null) return null;
        String plain = Lines.plain(line);
        int start = 0, end = plain.length();
        while (start < end && Character.isWhitespace(plain.charAt(start))) start++;
        while (end > start && Character.isWhitespace(plain.charAt(end - 1))) end--;
        if (end - start < 2) return null;
        String core = plain.substring(start, end);
        if (core.startsWith("[NPC] ")) {
            start += 6;
            core = core.substring(6);
        }
        String ua = translateNpcs ? d.get(Dict.Section.NPCS, core) : null;
        if (ua == null) ua = d.get(Dict.Section.LINES, core);
        if (ua != null) return Lines.replaceRange(line, start, end, ua, ops);
        return translateNpcs ? mobTag(line, plain, start, end, d) : null;
    }

    private static final Pattern MOB_LEVEL = Pattern.compile("\\[Lv\\d+]");
    private static final Pattern MOB_NAME = Pattern.compile("[A-Za-z][A-Za-z'\u2019\\-]*(?: [A-Za-z][A-Za-z'\u2019\\-]*)*");

    /*
     * Підпис моба: "[Lv235] Kuudra Berserker 1.2M/2M❤", "☠ Revenant Horror IV 900k❤".
     * Перекладається лише ім'я (найдовша відома послідовність слів) і "Lv"; здоров'я, значки й кольори лишаються.
     */
    private List<Seg<S>> mobTag(List<Seg<S>> line, String plain, int start, int end, Dict d) {
        String core = plain.substring(start, end);
        Matcher lv = MOB_LEVEL.matcher(core);
        boolean hasLevel = lv.find();
        if (!hasLevel && core.indexOf('\u2764') < 0) return null;
        Matcher run = MOB_NAME.matcher(core);
        if (!run.find(hasLevel ? lv.end() : 0)) return null;
        String[] words = run.group().split(" ");
        for (int len = words.length; len >= 1; len--) {
            for (int i = 0; i + len <= words.length; i++) {
                String cand = String.join(" ", java.util.Arrays.copyOfRange(words, i, i + len));
                String ua = d.get(Dict.Section.NAMES, cand);
                /* боси вбивці й підземель лежать серед рядків: "Revenant Horror" */
                if (ua == null && len >= 2) ua = d.get(Dict.Section.LINES, cand);
                if (ua == null) continue;
                int s = run.start();
                for (int k = 0; k < i; k++) s += words[k].length() + 1;
                int e = s + cand.length();
                List<Seg<S>> r = Lines.replaceRange(line, start + s, start + e, ua, ops);
                if (r == null) return null;
                /* "Lv" стоїть перед ім'ям, тож його позиція після заміни не зсунулася */
                if (hasLevel) {
                    List<Seg<S>> r2 = Lines.replaceRange(r, start + lv.start() + 1, start + lv.start() + 3, "\u0420\u0456\u0432.", ops);
                    if (r2 != null) r = r2;
                }
                return r;
            }
        }
        return null;
    }

    private List<Seg<S>> breadcrumb(List<Seg<S>> line, int start, String core, Dict d) {
        List<int[]> parts = new ArrayList<>();
        int from = 0;
        Matcher page = PAGE_PREFIX.matcher(core);
        if (page.find()) from = page.end();
        while (true) {
            int idx = core.indexOf(CRUMB, from);
            parts.add(new int[]{from, idx < 0 ? core.length() : idx});
            if (idx < 0) break;
            from = idx + CRUMB.length();
        }
        List<Seg<S>> current = line;
        boolean any = false;
        for (int k = parts.size() - 1; k >= 0; k--) {
            String part = core.substring(parts.get(k)[0], parts.get(k)[1]).strip();
            if (part.isEmpty()) continue;
            String ua = d.get(Dict.Section.LINES, part);
            if (ua == null) ua = lookupItem(part, d);
            if (ua == null) continue;
            List<Seg<S>> next = Lines.replaceRange(current, start + parts.get(k)[0], start + parts.get(k)[1], ua, ops);
            if (next != null) {
                current = next;
                any = true;
            }
        }
        return any ? current : null;
    }

    private String lookupItem(String text, Dict d) {
        NameHit hit = resolveName(text, d);
        if (hit != null) return hit.text();
        return d.get(Dict.Section.LINES, text);
    }

    /*
     * Переклад окремої назви без кольорів (назва зачарування для Iconographic, який
     * будує тег сам). Повертає null, якщо назви немає в словнику.
     */
    public String lookupPlain(String text) {
        Dict d = dict;
        if (d == null || text == null) return null;
        String v = d.get(Dict.Section.LINES, text.strip());
        if (v == null) v = d.get(Dict.Section.NAMES, text.strip());
        return v == null ? null : v.replaceAll("\u00A7.", "");
    }

    /*
     * Стиль незмінної частини шаблону: той, яким написано найбільше літер поза підстановками.
     * Інакше довга назва предмета "перефарбувала" б усе речення у свій колір.
     */
    private S literalStyle(List<Seg<S>> line, int s, Matcher m, int groupCount) {
        java.util.Map<S, Integer> letters = new java.util.LinkedHashMap<>();
        int pos = 0;
        for (Seg<S> seg : line) {
            String t = seg.text();
            for (int i = 0; i < t.length(); i++) {
                int abs = pos + i;
                int rel = abs - s;
                char ch = t.charAt(i);
                if (rel < 0 || rel >= m.end() || Character.isWhitespace(ch)) continue;
                boolean inGroup = false;
                for (int g = 1; g <= groupCount; g++) {
                    if (rel >= m.start(g) && rel < m.end(g)) {
                        inGroup = true;
                        break;
                    }
                }
                /* літера важить більше за розділовий знак: "|" враховується, лише коли літер нема */
                if (!inGroup) letters.merge(seg.style(), Character.isLetter(ch) ? 1000 : 1, Integer::sum);
            }
            pos += t.length();
        }
        S best = Lines.styleAt(line, s);
        int max = -1;
        for (java.util.Map.Entry<S, Integer> en : letters.entrySet()) {
            if (en.getValue() > max) {
                max = en.getValue();
                best = en.getKey();
            }
        }
        return best;
    }

    /* Перекласти без запису в список неперекладених (для частин рядка). */
    private List<Seg<S>> translateQuiet(List<Seg<S>> part, Kind kind) {
        boolean saved = collectMissing;
        collectMissing = false;
        try {
            List<Seg<S>> r = translate(part, kind);
            return r == null ? null : new ArrayList<>(r);
        } finally {
            collectMissing = saved;
        }
    }

    /* Розрізати рядок на дві частини за позицією символу. */
    private static <S> void splitAt(List<Seg<S>> line, int at, List<Seg<S>> head, List<Seg<S>> tail) {
        int pos = 0;
        for (Seg<S> seg : line) {
            String t = seg.text();
            int segStart = pos;
            pos += t.length();
            if (pos <= at) head.add(seg);
            else if (segStart >= at) tail.add(seg);
            else {
                head.add(new Seg<>(t.substring(0, at - segStart), seg.style()));
                tail.add(new Seg<>(t.substring(at - segStart), seg.style()));
            }
        }
    }

    private List<Seg<S>> enchantList(List<Seg<S>> line, int start, String core, Dict d) {
        List<int[]> ranges = new ArrayList<>();
        List<String> values = new ArrayList<>();
        if (core.startsWith("\u203A ")) {
            /* список від Skyblocker: "› Divine Gift, Knockback" або "› Cleave V→VI, Critical VI→VII" */
            int pos = start + 2;
            for (String item : core.substring(2).split(", ", -1)) {
                String name = ROMAN_TAIL.matcher(item).replaceFirst("");
                String ua = d.get(Dict.Section.LINES, name);
                if (ua != null) {
                    ranges.add(new int[]{pos, pos + name.length()});
                    values.add(ua);
                }
                pos += item.length() + 2;
            }
        } else {
            if (!ENCHANT_LIST.matcher(core).matches()) return null;
            Matcher m = ENCHANT_ITEM.matcher(core);
            while (m.find()) {
                String ua = d.get(Dict.Section.LINES, m.group(1));
                if (ua == null) continue;
                if (isAllCaps(m.group(1))) ua = ua.toUpperCase(Locale.ROOT);
                ranges.add(new int[]{start + m.start(1), start + m.end(1)});
                values.add(ua);
            }
        }
        if (ranges.isEmpty()) return null;
        List<Seg<S>> current = line;
        for (int i = ranges.size() - 1; i >= 0; i--) {
            List<Seg<S>> next = Lines.replaceRange(current, ranges.get(i)[0], ranges.get(i)[1], values.get(i), ops);
            if (next != null) current = next;
        }
        return current == line ? null : current;
    }

    private static boolean isAllCaps(String s) {
        boolean letters = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLowerCase(c)) return false;
            if (Character.isUpperCase(c)) letters = true;
        }
        return letters;
    }

    private List<Seg<S>> labelRule(List<Seg<S>> line, int start, int end, String core, Dict d) {
        Matcher m = LABEL.matcher(core);
        if (m.find()) {
            String label = m.group(1);
            String ua = d.get(Dict.Section.LABELS, label.strip());
            if (ua == null) {
                /* підпис із римським рівнем: "Progress to Tier XV", "Compact VII", "Expertise IX" */
                Matcher rw = ROMAN_LABEL.matcher(label);
                if (rw.find() && rw.start() > 0) {
                    String head = label.substring(0, rw.start());
                    String t = d.get(Dict.Section.LABELS, head);
                    if (t == null) t = d.get(Dict.Section.LINES, head);
                    if (t != null) ua = t + label.substring(rw.start());
                }
            }
            if (ua == null) return null;
            List<Seg<S>> base = line;
            /* Назва здібності чи бонусу після підпису: шукаємо її без кнопки миші в кінці */
            if (ABILITY_LABEL.matcher(label).matches()) {
                String value = core.substring(m.end());
                int lead = 0;
                while (lead < value.length() && value.charAt(lead) == ' ') lead++;
                String rest = value.substring(lead);
                Matcher tail = ABILITY_TAIL.matcher(rest);
                int nameEnd = tail.find() ? tail.start() : rest.length();
                String name = rest.substring(0, nameEnd);
                String uaName = name.isBlank() ? null : d.get(Dict.Section.LINES, name.strip());
                if (uaName == null && !name.isBlank()) uaName = d.get(Dict.Section.NAMES, name.strip());
                if (uaName != null) {
                    int ns = start + m.end() + lead;
                    List<Seg<S>> r = Lines.replaceRange(line, ns, ns + name.length(), uaName, ops);
                    if (r != null) base = r;
                }
            }
            return Lines.replaceRange(base, start, start + label.length(), ua, ops);
        }
        /* Стат після числа: "+57.85 Strength", "+2☯ Combat Wisdom" */
        m = VALUE_FIRST.matcher(core);
        if (m.matches()) {
            String ua = d.get(Dict.Section.LABELS, m.group(1));
            if (ua == null) return null;
            return Lines.replaceRange(line, start + m.start(1), start + m.end(1), ua, ops);
        }
        /* Стат перед числом без двокрапки (таблиці статів у модах): "❁ Strength 849.48" */
        m = VALUE_LAST.matcher(core);
        if (m.matches()) {
            String ua = d.get(Dict.Section.LABELS, m.group(1));
            if (ua != null) return Lines.replaceRange(line, start + m.start(1), start + m.end(1), ua, ops);
        }
        /* Підпис окремим фрагментом (так його малюють інші моди, наприклад Iconographic): "Strength" */
        String ua = d.get(Dict.Section.LABELS, core);
        if (ua != null) return Lines.replaceRange(line, start, end, ua, ops);
        return null;
    }

    /*
     * Замінити токени (окремі слова й фрази) будь-де в рядку.
     * Повертає той самий об'єкт, якщо нічого не замінено.
     */
    private List<Seg<S>> applyTokens(List<Seg<S>> line, Dict d) {
        List<Map.Entry<String, String>> tokens = d.tokensLongestFirst();
        if (tokens.isEmpty()) return line;
        List<Seg<S>> current = line;
        for (Map.Entry<String, String> token : tokens) {
            String key = token.getKey();
            String plain = Lines.plain(current);
            int idx = plain.lastIndexOf(key);
            while (idx >= 0) {
                if (isBoundary(plain, idx - 1) && isBoundary(plain, idx + key.length())) {
                    List<Seg<S>> next = Lines.replaceRange(current, idx, idx + key.length(), token.getValue(), ops);
                    if (next != null) current = next;
                }
                idx = idx == 0 ? -1 : plain.lastIndexOf(key, idx - 1);
            }
        }
        return current;
    }

    private List<Seg<S>> segmentWise(List<Seg<S>> line, Kind kind, Dict d) {
        int nonEmpty = 0;
        for (Seg<S> seg : line) if (!seg.text().isBlank()) nonEmpty++;
        if (nonEmpty < 2) return null;

        List<Seg<S>> out = new ArrayList<>(line.size());
        boolean changed = false;
        for (Seg<S> seg : line) {
            String t = seg.text();
            String trimmed = t.strip();
            String filled = null;
            if (trimmed.length() >= 2 && hasLatinLetter(trimmed)) {
                Template tp = Template.of(trimmed);
                String v = lookup(kind, tp.key, d);
                if (v != null) filled = tp.fill(v);
            }
            if (filled == null) {
                out.add(seg);
                continue;
            }
            int lead = t.indexOf(trimmed);
            String text = t.substring(0, lead) + filled + t.substring(lead + trimmed.length());
            Lines.appendLegacy(out, text, seg.style(), ops);
            changed = true;
        }
        return changed ? out : null;
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private static String decapitalize(String s) {
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toLowerCase(Locale.ROOT) + s.substring(1);
    }

    private static boolean isBoundary(String s, int index) {
        if (index < 0 || index >= s.length()) return true;
        return !Character.isLetterOrDigit(s.charAt(index));
    }

    static boolean hasLatinLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return true;
        }
        return false;
    }

    static boolean hasCyrillic(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'Ѐ' && c <= 'ӿ') return true;
        }
        return false;
    }
}
