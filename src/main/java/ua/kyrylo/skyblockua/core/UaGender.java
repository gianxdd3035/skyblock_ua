package ua.kyrylo.skyblockua.core;

import java.util.Locale;
import java.util.Set;

/*
 * Граматичний рід в українській: m — чоловічий, f — жіночий, n — середній, p — множина.
 * Потрібен, щоб узгодити прикметник (рефорж, назву сету) з іменником:
 * "Лютий меч", "Люта сокира", "Люте намисто", "Люті чоботи".
 */
public final class UaGender {

    /* Іменники жіночого роду на -ь (і подібні), які правило "закінчення -> рід" вгадало б неправильно. */
    private static final Set<String> FEMININE = Set.of(
            "сіль", "мідь", "сталь", "тінь", "ртуть", "піч", "ніч", "річ", "кров", "любов",
            "лють", "осінь", "розкіш", "мить", "суть", "подорож", "мазь", "путь", "далечінь", "глибінь",
            "височінь", "зелень", "блакить", "жовч", "міць", "паморозь", "пам'ять", "смерть");

    private UaGender() {
    }

    /* Розібрати позначку роду з файлу словника ("m", "f", "n", "p" або українські "ч", "ж", "с", "мн"). */
    public static char parse(String s) {
        String t = s.strip().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "m", "ч" -> 'm';
            case "f", "ж" -> 'f';
            case "n", "с" -> 'n';
            case "p", "pl", "мн" -> 'p';
            default -> 0;
        };
    }

    public static int index(char g) {
        return switch (g) {
            case 'f' -> 1;
            case 'n' -> 2;
            case 'p' -> 3;
            default -> 0;
        };
    }

    /*
     * Вгадати рід за першим словом українського перекладу.
     * Перше слово в назві — або прикметник, узгоджений з головним іменником ("Зачарована книга"),
     * або сам головний іменник ("Шолом Вищого Дракона"). В обох випадках закінчення підказує рід.
     */
    public static char guess(String ukrainian) {
        String w = firstWord(ukrainian);
        if (w.isEmpty()) return 'm';
        if (FEMININE.contains(w) || w.endsWith("ість")) return 'f';
        /* прикметники */
        if (w.endsWith("ий") || w.endsWith("ій") || w.endsWith("їй")) return 'm';
        if (w.endsWith("а") || w.endsWith("я")) {
            /* вугілля, насіння, латаття, пір'я — середній рід */
            int n = w.length();
            if (w.endsWith("'я") || (w.endsWith("я") && n >= 3 && w.charAt(n - 2) == w.charAt(n - 3) && isConsonant(w.charAt(n - 2)))) return 'n';
            return 'f';
        }
        if (w.endsWith("о") || w.endsWith("е") || w.endsWith("є")) return 'n';
        if (w.endsWith("і") || w.endsWith("и") || w.endsWith("ї")) return 'p';
        return 'm';
    }

    /*
     * Поставити прикметник (або кілька прикметників через пробіл) у потрібний рід.
     * Задається форма чоловічого роду: "Алмазний" -> "Алмазна", "Стародавній" -> "Стародавня",
     * "Загартований алмазний" -> "Загартовані алмазні".
     */
    public static String adjective(String masculine, char g) {
        if (g == 'm') return masculine;
        String[] words = masculine.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(inflect(words[i], g));
        }
        return sb.toString();
    }

    private static String inflect(String w, char g) {
        String lw = w.toLowerCase(Locale.ROOT);
        if (lw.endsWith("ий")) {
            String stem = w.substring(0, w.length() - 2);
            return stem + switch (g) {
                case 'f' -> "а";
                case 'n' -> "е";
                default -> "і";
            };
        }
        if (lw.endsWith("ій") || lw.endsWith("їй")) {
            String stem = w.substring(0, w.length() - 2);
            return stem + switch (g) {
                case 'f' -> "я";
                case 'n' -> "є";
                default -> "і";
            };
        }
        /* присвійні на -ін/-їн: Джеррін -> Джерріна */
        if (lw.endsWith("ін") || lw.endsWith("їн")) {
            return w + switch (g) {
                case 'f' -> "а";
                case 'n' -> "е";
                default -> "і";
            };
        }
        return w;
    }

    private static String firstWord(String s) {
        String t = s.strip().toLowerCase(Locale.ROOT);
        int end = 0;
        while (end < t.length()) {
            char c = t.charAt(end);
            if (Character.isLetter(c) || c == '\'' || c == '’') end++;
            else break;
        }
        return t.substring(0, end).replace('’', '\'');
    }

    private static boolean isConsonant(char c) {
        return "бвгґджзйклмнпрстфхцчшщ".indexOf(c) >= 0;
    }
}
