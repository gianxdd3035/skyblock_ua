package ua.kyrylo.skyblockua.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Перетворює рядок на "шаблон": кожне число замінюється на {0}, {1}, ...
 * Приклад: "Strength: +50 (+10)" -> ключ "Strength: {0} ({1})", аргументи ["+50", "+10"].
 * Завдяки цьому один запис у словнику покриває всі варіанти з різними числами.
 *
 * Той самий регулярний вираз використовує tools/neu_extract.py — вони мають збігатися.
 */
public final class Template {

    /*
     * Число: необов'язковий знак, цифри (можна з комами як 1,000,000), дробова частина,
     * необов'язковий суфікс k/m/b (лише якщо за ним не йде літера) і необов'язковий %.
     * Число не може починатися посеред слова (перед ним не літера і не цифра).
     */
    public static final Pattern NUMBER = Pattern.compile(
            "(?<![A-Za-z0-9])[+-]?(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?(?:[kKmMbB](?![A-Za-z]))?%?");

    public static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d{1,2})}");

    public final String key;
    public final List<String> args;

    private Template(String key, List<String> args) {
        this.key = key;
        this.args = args;
    }

    /* Зібрати шаблон із рядка. Пробіли спершу стискаються до одного. */
    public static Template of(String text) {
        String normalized = normalizeSpaces(text);
        Matcher m = NUMBER.matcher(normalized);
        List<String> args = new ArrayList<>();
        StringBuilder key = new StringBuilder();
        int last = 0;
        while (m.find()) {
            key.append(normalized, last, m.start());
            key.append('{').append(args.size()).append('}');
            args.add(m.group());
            last = m.end();
        }
        key.append(normalized, last, normalized.length());
        return new Template(key.toString(), args);
    }

    /*
     * Підставити числа в переклад. Якщо переклад посилається на {n},
     * якого немає в оригіналі, повертається null (запис зламаний — його краще не показувати).
     */
    public String fill(String value) {
        if (value.indexOf('{') < 0) return value;
        Matcher m = PLACEHOLDER.matcher(value);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            int index = Integer.parseInt(m.group(1));
            if (index >= args.size()) return null;
            out.append(value, last, m.start()).append(args.get(index));
            last = m.end();
        }
        out.append(value, last, value.length());
        return out.toString();
    }

    /* Стиснути будь-які послідовності пробілів до одного пробілу й обрізати краї. */
    public static String normalizeSpaces(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean space = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == ' ') {
                space = true;
            } else {
                if (space && sb.length() > 0) sb.append(' ');
                space = false;
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
