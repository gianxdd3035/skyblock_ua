package ua.kyrylo.skyblockua.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Шаблон із підстановками, наприклад:
 *   "You dropped {0}x {item}!"  ->  "Тобі випало {0}x {item}!"
 * Ключ перетворюється на регулярний вираз: літери й символи — як є, пробіли — "один або більше пробілів",
 * {0}...{9} — число, {item}/{item2} і {any}/{any2} — будь-який текст.
 */
public final class PatternEntry {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d|item2?|any2?)}");
    private static final String NUMBER = "([+-]?(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?(?:[kKmMbB])?%?)";

    public final Pattern regex;
    public final String value;
    /* назва кожної групи по порядку: "0", "item", "any" ... */
    public final List<String> groups;

    private PatternEntry(Pattern regex, String value, List<String> groups) {
        this.regex = regex;
        this.value = value;
        this.groups = groups;
    }

    public static PatternEntry compile(String key, String value) {
        Matcher m = PLACEHOLDER.matcher(key);
        StringBuilder re = new StringBuilder("^");
        List<String> groups = new ArrayList<>();
        int last = 0;
        while (m.find()) {
            re.append(literal(key.substring(last, m.start())));
            String name = m.group(1);
            re.append(Character.isDigit(name.charAt(0)) ? NUMBER : "(.+?)");
            groups.add(name);
            last = m.end();
        }
        re.append(literal(key.substring(last))).append('$');
        if (groups.isEmpty()) return null;
        try {
            return new PatternEntry(Pattern.compile(re.toString()), value, groups);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /* Літеральна частина ключа: усе екрановано, пробіли — "\s+". */
    private static String literal(String s) {
        StringBuilder sb = new StringBuilder();
        String[] parts = s.split(" ", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append("\\s+");
            if (!parts[i].isEmpty()) sb.append(Pattern.quote(parts[i]));
        }
        return sb.toString();
    }
}
