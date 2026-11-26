package ua.kyrylo.skyblockua.core;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/*
 * Збирає рядки, для яких не знайшлося перекладу.
 * Потім їх можна вивантажити у файл (/ukr dump), перекласти й додати до словника.
 * Так поступово закриваються меню, діалоги та повідомлення, яких немає у відкритих базах.
 */
public final class Missing {

    private static final int LIMIT_PER_KIND = 20000;

    /*
     * Чат інших гравців не збираємо: це не тексти гри, і їх не варто нікуди зберігати.
     * Шаблон ловить "[MVP+] Нік: ...", "Нік: ...", а також гільдію, паті, кооп і приватні повідомлення.
     */
    private static final Pattern PLAYER_CHAT = Pattern.compile(
            "^(?:(?:Guild|G|Officer|O|Party|P|Co-op|C) > .*|(?:From|To) .*|(?:\\[[^\\]]{1,12}] )*[A-Za-z0-9_]{3,16}(?: \\[[^\\]]{1,12}])?: .*)$");

    private static final class Entry {
        final String example;
        int count;

        Entry(String example) {
            this.example = example;
        }
    }

    private final Map<Translator.Kind, LinkedHashMap<String, Entry>> data = new EnumMap<>(Translator.Kind.class);

    public Missing() {
        for (Translator.Kind k : Translator.Kind.values()) data.put(k, new LinkedHashMap<>());
    }

    /* Записати неперекладений рядок. key — шаблон, example — оригінал із §-кодами. */
    public synchronized void record(Translator.Kind kind, String key, String example) {
        if (key.length() < 2 || key.length() > 400) return;
        if (!hasLetters(key)) return;
        /* уже українське (наприклад, повторно прочитаний перекладений рядок) — не збираємо */
        if (key.chars().anyMatch(ch -> Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CYRILLIC)) return;
        if (kind == Translator.Kind.CHAT && !key.startsWith("[NPC]") && PLAYER_CHAT.matcher(key).matches()) return;
        Map<String, Entry> map = data.get(kind);
        Entry e = map.get(key);
        if (e == null) {
            if (map.size() >= LIMIT_PER_KIND) return;
            e = new Entry(example);
            map.put(key, e);
        }
        e.count++;
    }

    public synchronized int size() {
        int n = 0;
        for (Map<String, Entry> m : data.values()) n += m.size();
        return n;
    }

    public synchronized void clear() {
        for (Map<String, Entry> m : data.values()) m.clear();
    }

    /*
     * Записати у файл: "вид<TAB>скільки разів<TAB>ключ<TAB>приклад з кольорами".
     * Сортування — від найчастіших, щоб спершу перекладати те, що видно найбільше.
     */
    public synchronized void write(Writer w) throws IOException {
        w.write("# kind\tcount\tkey\texample\n");
        for (Map.Entry<Translator.Kind, LinkedHashMap<String, Entry>> kindEntry : data.entrySet()) {
            List<Map.Entry<String, Entry>> list = new ArrayList<>(kindEntry.getValue().entrySet());
            list.sort((a, b) -> Integer.compare(b.getValue().count, a.getValue().count));
            for (Map.Entry<String, Entry> e : list) {
                w.write(kindEntry.getKey().name());
                w.write('\t');
                w.write(Integer.toString(e.getValue().count));
                w.write('\t');
                w.write(oneLine(e.getKey()));
                w.write('\t');
                w.write(oneLine(e.getValue().example));
                w.write('\n');
            }
        }
    }

    /* Багаторядкові повідомлення (наприклад, від інших модів) записуємо в один рядок файлу */
    private static String oneLine(String s) {
        return s.replace('\t', ' ').replace("\r", "").replace("\n", " \u23CE ");
    }

    private static boolean hasLetters(String s) {
        int letters = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                if (++letters >= 2) return true;
            }
        }
        return false;
    }
}
