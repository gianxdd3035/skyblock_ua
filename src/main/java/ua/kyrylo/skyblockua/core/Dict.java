package ua.kyrylo.skyblockua.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/*
 * Словник перекладів. Завантажується з TSV-файлів (стовпці розділені табуляцією).
 *
 * Розділ визначається назвою файлу:
 *  - names*.tsv        — назви предметів: "англійською<TAB>українською[<TAB>рід]".
 *                        Рід (m/f/n/p) потрібен, щоб узгодити рефорж; якщо його немає,
 *                        мод визначає рід за першим словом перекладу (UaGender).
 *  - labels*.tsv       — підписи перед двокрапкою: "Strength" -> "Сила";
 *  - tokens*.tsv       — слова, які замінюються будь-де в рядку: "RIGHT CLICK" -> "ПКМ";
 *  - reforges*.tsv     — рефоржі: "Fierce<TAB>Лютий<TAB>Люта<TAB>Люте<TAB>Люті" (ч, ж, с, мн);
 *  - armor_pieces*.tsv — частини броні: "Helmet<TAB>шолом<TAB>m";
 *  - patterns*.tsv     — шаблони з підстановками: "You dropped {0}x {item}!<TAB>Тобі випало {0}x {item}!".
 *                        {item} — назва предмета (перекладається словником назв), {any} — будь-який текст
 *                        (ім'я гравця, моба), лишається як є; {0}...{9} — числа;
 *  - armor_sets*.tsv   — назви сетів: "Superior Dragon<TAB>Вищого Дракона" (ставиться після частини)
 *                        або "Diamond<TAB>adj:Алмазний" (прикметник, узгоджується з частиною);
 *  - усі інші *.tsv    — цілі рядки: описи, меню, чат, заголовки.
 *
 * Числа в ключах треба писати як {0}, {1} (див. Template).
 */
public final class Dict {

    public enum Section { LINES, NAMES, LABELS, TOKENS, ARMOR_SETS, NPCS, STATS }

    /* Частина броні: український іменник і його рід. */
    public record Piece(String english, String ukrainian, char gender) {
    }

    private final Map<Section, Map<String, String>> exact = new HashMap<>();
    private final Map<Section, Map<String, String>> lower = new HashMap<>();
    private final Map<String, Character> nameGender = new HashMap<>();
    private final Map<String, String[]> reforges = new HashMap<>();
    private final List<Piece> pieces = new ArrayList<>();
    private final List<PatternEntry> patterns = new ArrayList<>();
    private int invalid;

    public Dict() {
        for (Section s : Section.values()) {
            exact.put(s, new HashMap<>());
            lower.put(s, new HashMap<>());
        }
    }

    /* До якого розділу належить файл, за його назвою. */
    public static Section sectionFor(String fileName) {
        String f = fileName.toLowerCase(Locale.ROOT);
        if (f.startsWith("names")) return Section.NAMES;
        if (f.startsWith("labels")) return Section.LABELS;
        if (f.startsWith("tokens")) return Section.TOKENS;
        if (f.startsWith("armor_sets")) return Section.ARMOR_SETS;
        if (f.startsWith("npcs")) return Section.NPCS;
        if (f.startsWith("stats")) return Section.STATS;
        return Section.LINES;
    }

    /*
     * Прочитати один файл. Пізніші записи перезаписують ранніші,
     * тому файли користувача завантажуються після вбудованих.
     */
    public void load(String fileName, Reader reader) throws IOException {
        String f = fileName.toLowerCase(Locale.ROOT);
        BufferedReader br = new BufferedReader(reader);
        Section section = sectionFor(fileName);
        String l;
        while ((l = br.readLine()) != null) {
            if (!l.isEmpty() && l.charAt(0) == '﻿') l = l.substring(1);
            if (l.isBlank() || l.startsWith("#")) continue;
            /* іконки Hypixel (символи приватної області Unicode) можна писати як \uE010 */
            if (l.indexOf("\\u") >= 0) l = unescape(l);
            String[] cols = l.split("\t", -1);
            if (cols.length < 2) {
                invalid++;
                continue;
            }
            String key = Template.normalizeSpaces(cols[0]);
            if (key.isEmpty() || cols[1].isBlank()) {
                invalid++;
                continue;
            }
            if (f.startsWith("reforges")) {
                if (cols.length < 5) {
                    invalid++;
                    continue;
                }
                reforges.put(key.toLowerCase(Locale.ROOT),
                        new String[]{cols[1].strip(), cols[2].strip(), cols[3].strip(), cols[4].strip()});
            } else if (f.startsWith("patterns")) {
                PatternEntry p = PatternEntry.compile(key, cols[1].strip());
                if (p == null) invalid++;
                else patterns.add(p);
            } else if (f.startsWith("armor_pieces")) {
                char g = cols.length >= 3 ? UaGender.parse(cols[2]) : 0;
                if (g == 0) {
                    invalid++;
                    continue;
                }
                pieces.removeIf(p -> p.english().equalsIgnoreCase(key));
                pieces.add(new Piece(key, cols[1].strip(), g));
                pieces.sort((a, b) -> Integer.compare(b.english().length(), a.english().length()));
            } else {
                put(section, key, cols[1].strip());
                if (section == Section.NAMES && cols.length >= 3) {
                    char g = UaGender.parse(cols[2]);
                    if (g != 0) nameGender.put(key.toLowerCase(Locale.ROOT), g);
                }
            }
        }
    }

    private static final java.util.regex.Pattern ESCAPE = java.util.regex.Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    static String unescape(String s) {
        java.util.regex.Matcher m = ESCAPE.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1), 16))));
        m.appendTail(sb);
        return sb.toString();
    }

    public void put(Section section, String key, String value) {
        if (section == Section.TOKENS) sortedTokens = null;
        exact.get(section).put(key, value);
        lower.get(section).put(key.toLowerCase(Locale.ROOT), value);
    }

    /* Пошук: спершу точний збіг, потім без урахування регістру. */
    public String get(Section section, String key) {
        String v = exact.get(section).get(key);
        if (v != null) return v;
        return lower.get(section).get(key.toLowerCase(Locale.ROOT));
    }

    /* Рід, явно вказаний у names*.tsv (або 0, якщо не вказано). */
    public char nameGender(String key) {
        Character g = nameGender.get(key.toLowerCase(Locale.ROOT));
        return g == null ? 0 : g;
    }

    private List<Map.Entry<String, String>> sortedTokens;

    /*
     * Токени від найдовшого до найкоротшого: інакше "RIGHT CLICK" замінився б
     * усередині "SNEAK RIGHT CLICK" раніше, ніж довша фраза.
     */
    public synchronized List<Map.Entry<String, String>> tokensLongestFirst() {
        if (sortedTokens == null) {
            List<Map.Entry<String, String>> list = new ArrayList<>(exact.get(Section.TOKENS).entrySet());
            list.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
            sortedTokens = list;
        }
        return sortedTokens;
    }

    public boolean isReforge(String words) {
        return reforges.containsKey(words.toLowerCase(Locale.ROOT));
    }

    /* Форма рефоржу для роду g, або null, якщо рефорж невідомий. */
    public String reforgeForm(String words, char g) {
        String[] forms = reforges.get(words.toLowerCase(Locale.ROOT));
        if (forms == null) return null;
        return forms[UaGender.index(g)];
    }

    public List<Piece> pieces() {
        return pieces;
    }

    public List<PatternEntry> patterns() {
        return patterns;
    }

    public int size() {
        int n = reforges.size() + pieces.size() + patterns.size();
        for (Map<String, String> m : exact.values()) n += m.size();
        return n;
    }

    public int size(Section section) {
        return exact.get(section).size();
    }

    public int invalidLines() {
        return invalid;
    }
}
