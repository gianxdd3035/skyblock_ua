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
    private static final Pattern LABEL = Pattern.compile("^([A-Za-z][A-Za-z0-9 '\\-]{0,40}?):(?= |$)");
    private static final Pattern VALUE_FIRST = Pattern.compile("^[+-]?\\d[\\d,.]*[%x]?[^A-Za-z]{0,3}?\\s*([A-Za-z][A-Za-z ']*[A-Za-z])$");
    private static final Pattern PET_LEVEL = Pattern.compile("^\\[Lvl \\d+] ");
    private static final Pattern RECOMB = Pattern.compile("^a (.+) a$");
    private static final Pattern BULLET = Pattern.compile("^(?:- |• |▶ )");
    private static final Pattern ROMAN_SUFFIX = Pattern.compile(" \\([IVX]+(?:-[IVX]+)?\\)$");
    /* Лічильник повторів, який дописують моди на стискання чату: "(2)", "(×3)" */
    private static final Pattern CHAT_COUNTER = Pattern.compile(" \\((?:\u00D7|x)?\\d+\\)$");
    /* Капсовий префікс повідомлення: "RARE DROP! ...", "TROPHY FROG! ...", "SLAYER QUEST STARTED!" */
    private static final Pattern CAPS_PREFIX = Pattern.compile("^([A-Z0-9][A-Z0-9'\u2019\\- ]{1,30}[!:]) (.+)$");
    private static final Pattern ROMAN_WORD = Pattern.compile(" (?:Mk\\. )?[IVXL]+$");
    /* "Прикраси" в кінці: зірки ✪, ➊–➎ та інші символи, що не є літерами, цифрами чи дужками. */
    private static final Pattern TRAILING_DECOR = Pattern.compile("[^\\p{L}\\p{N})\\]!?.'\"]+$");

    /* Перекладена назва предмета разом із її родом. */
    private record NameHit(String text, char gender, boolean fromArmor) {
    }

    private final StyleOps<S> ops;
    private final Missing missing = new Missing();
    private volatile Dict dict = new Dict();

    public volatile boolean translateNames = true;
    public volatile boolean collectMissing = true;

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

        /* 1б. Шаблони з підстановками ("You dropped {0}x {item}!") */
        if (kind != Kind.NAME) {
            r = patternLookup(line, start, end, core, kind, d);
            if (r != null) return r;
        }

        /* 2. Без прикрас */
        r = peeled(line, start, end, core, kind, d);
        if (r != null) return applyTokens(r, d);

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
                if (r != null) return r;
            }
        }

        /* 4. Підпис перед двокрапкою + токени (у чаті токени не застосовуємо: вийшли б напівперекладені фрази) */
        if (kind == Kind.CHAT) {
            List<Seg<S>> labelled = labelRule(line, start, end, core, d);
            if (labelled != null) return labelled;
        } else if (kind != Kind.NAME) {
            List<Seg<S>> labelled = labelRule(line, start, end, core, d);
            List<Seg<S>> base = labelled != null ? labelled : line;
            List<Seg<S>> tokens = applyTokens(base, d);
            if (labelled != null || tokens != base) return tokens;
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
        String filled = t.fill(value);
        if (filled == null) return null;
        if (kind == Kind.CHAT && filled.indexOf('\u00A7') < 0) {
            return Lines.replaceRange(line, s, e, filled, ops, Lines.dominantStyle(line, s, e));
        }
        return Lines.replaceRange(line, s, e, filled, ops);
    }

    /*
     * Назви предметів шукаються в розділі names (якщо назви увімкнені) і в lines
     * (там лежать назви кнопок меню: "Close", "Go Back" — їх перекладаємо завжди).
     */
    private String lookup(Kind kind, String key, Dict d) {
        if (kind == Kind.NAME) {
            if (translateNames) {
                String v = d.get(Dict.Section.NAMES, key);
                if (v != null) return v;
            }
            return d.get(Dict.Section.LINES, key);
        }
        return d.get(Dict.Section.LINES, key);
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
                if (hit == null) return null;
                String form = d.reforgeForm(reforge, hit.gender());
                String restText = hit.fromArmor() ? decapitalize(hit.text()) : hit.text();
                String value = (form == null || form.isEmpty() ? reforge : form) + " " + restText;
                return Lines.replaceRange(line, s, e, value, ops);
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
        for (PatternEntry p : d.patterns()) {
            Matcher m = p.regex.matcher(text);
            if (!m.matches()) continue;
            S base = literalStyle(line, s, m, p.groups.size());
            String baseCodes = ops.legacy(base);
            String value = p.value;
            for (int i = 0; i < p.groups.size(); i++) {
                String name = p.groups.get(i);
                String captured = m.group(i + 1);
                String replacement = captured;
                if (name.startsWith("item")) {
                    NameHit hit = resolveName(captured.strip(), d);
                    if (hit != null) replacement = hit.text();
                    else {
                        String v = d.get(Dict.Section.LINES, captured.strip());
                        if (v != null) replacement = v;
                    }
                }
                S own = Lines.styleAt(line, s + m.start(i + 1));
                String styled = ops.legacy(own) + replacement + baseCodes;
                value = value.replace("{" + name + "}", styled);
            }
            List<Seg<S>> r = Lines.replaceRange(line, s, e, baseCodes + value, ops, base);
            if (r != null) return r;
        }
        return null;
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
                if (rel < 0 || rel >= m.end() || !Character.isLetter(t.charAt(i))) continue;
                boolean inGroup = false;
                for (int g = 1; g <= groupCount; g++) {
                    if (rel >= m.start(g) && rel < m.end(g)) {
                        inGroup = true;
                        break;
                    }
                }
                if (!inGroup) letters.merge(seg.style(), 1, Integer::sum);
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

    private List<Seg<S>> labelRule(List<Seg<S>> line, int start, int end, String core, Dict d) {
        Matcher m = LABEL.matcher(core);
        if (m.find()) {
            String label = m.group(1);
            String ua = d.get(Dict.Section.LABELS, label.strip());
            if (ua == null) return null;
            return Lines.replaceRange(line, start, start + label.length(), ua, ops);
        }
        /* Стат після числа: "+57.85 Strength", "+2☯ Combat Wisdom" */
        m = VALUE_FIRST.matcher(core);
        if (m.matches()) {
            String ua = d.get(Dict.Section.LABELS, m.group(1));
            if (ua == null) return null;
            return Lines.replaceRange(line, start + m.start(1), start + m.end(1), ua, ops);
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
