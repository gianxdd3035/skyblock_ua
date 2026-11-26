package ua.kyrylo.skyblockua.core;

import java.util.ArrayList;
import java.util.List;

/*
 * Допоміжні операції над рядком, розбитим на стилізовані шматки (List<Seg<S>>).
 */
public final class Lines {

    private Lines() {
    }

    /* Текст рядка без стилів. */
    public static <S> String plain(List<Seg<S>> line) {
        StringBuilder sb = new StringBuilder();
        for (Seg<S> seg : line) sb.append(seg.text());
        return sb.toString();
    }

    /*
     * Додати текст до рядка, розбираючи вбудовані §-коди.
     * Hypixel іноді надсилає текст зі старими §-кодами всередині, а не окремими стилями,
     * тому рушій завжди працює вже з "чистими" шматками без символу §.
     */
    public static <S> void appendLegacy(List<Seg<S>> out, String text, S style, StyleOps<S> ops) {
        appendLegacy(out, text, style, ops, null);
    }

    /*
     * Позначка слоту в тексті перекладу: символ SLOT0 + i замінюється шматками slots[i] без змін
     * (число з оригіналу, ім'я гравця, назва з особливим шрифтом). Так зберігаються стилі,
     * яких не передати §-кодами: RGB-кольори, шрифти інших модів. Це "не-символи" Юнікоду,
     * у справжньому тексті вони не трапляються.
     */
    public static final char SLOT0 = '\uFDD0';

    public static <S> void appendLegacy(List<Seg<S>> out, String text, S style, StyleOps<S> ops, List<List<Seg<S>>> slots) {
        if (text.isEmpty()) return;
        S current = style;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (slots != null && c >= SLOT0 && c < SLOT0 + slots.size()) {
                if (buf.length() > 0) {
                    out.add(new Seg<>(buf.toString(), current));
                    buf.setLength(0);
                }
                out.addAll(slots.get(c - SLOT0));
                continue;
            }
            if (c == '§' && i + 1 < text.length()) {
                if (buf.length() > 0) {
                    out.add(new Seg<>(buf.toString(), current));
                    buf.setLength(0);
                }
                current = ops.applyCode(current, Character.toLowerCase(text.charAt(i + 1)));
                i++;
            } else {
                buf.append(c);
            }
        }
        if (buf.length() > 0) out.add(new Seg<>(buf.toString(), current));
    }

    /* Стиль символу з індексом index (або останнього шматка, якщо індекс за межами). */
    public static <S> S styleAt(List<Seg<S>> line, int index) {
        int pos = 0;
        for (Seg<S> seg : line) {
            int end = pos + seg.text().length();
            if (index < end && !seg.text().isEmpty()) return seg.style();
            pos = end;
        }
        return line.isEmpty() ? null : line.get(line.size() - 1).style();
    }

    /*
     * Замінити символи [start, end) на value.
     * Новий текст отримує стиль символу start; якщо у value є §-коди, вони застосовуються поверх нього.
     * Повертає null, якщо діапазон зачіпає шматки з різними клік/ховер-подіями:
     * такий рядок не можна склеїти без втрати подій, тому краще залишити оригінал.
     */
    public static <S> List<Seg<S>> replaceRange(List<Seg<S>> line, int start, int end, String value, StyleOps<S> ops) {
        return replaceRange(line, start, end, value, ops, styleAt(line, start));
    }

    /*
     * Те саме, але зі стилем, заданим явно (наприклад, стилем основного тексту повідомлення,
     * а не першого символу).
     */
    public static <S> List<Seg<S>> replaceRange(List<Seg<S>> line, int start, int end, String value, StyleOps<S> ops, S base) {
        return replaceRange(line, start, end, value, ops, base, null);
    }

    public static <S> List<Seg<S>> replaceRange(List<Seg<S>> line, int start, int end, String value, StyleOps<S> ops, S base,
                                                List<List<Seg<S>>> slots) {
        List<Seg<S>> out = new ArrayList<>(line.size() + 2);
        int pos = 0;
        boolean inserted = false;
        for (Seg<S> seg : line) {
            String t = seg.text();
            if (t.isEmpty()) continue;
            int segStart = pos;
            int segEnd = pos + t.length();
            pos = segEnd;

            boolean overlaps = segEnd > start && segStart < end;
            if (overlaps && !ops.sameExtras(seg.style(), base)) return null;

            /* частина шматка до діапазону */
            if (segStart < start) {
                int cut = Math.min(segEnd, start);
                out.add(new Seg<>(t.substring(0, cut - segStart), seg.style()));
            }
            /* сам діапазон: вставляємо переклад один раз, щойно дійшли до позиції start */
            if (!inserted && segEnd > start) {
                appendLegacy(out, value, base, ops, slots);
                inserted = true;
            }
            /* частина шматка після діапазону */
            if (segEnd > end) {
                int from = Math.max(segStart, end);
                out.add(new Seg<>(t.substring(from - segStart), seg.style()));
            }
        }
        if (!inserted) appendLegacy(out, value, base, ops, slots);
        return out;
    }

    /*
     * Стиль, яким написано найбільше літер у діапазоні [start, end).
     * У чаті перший шматок часто — кольоровий префікс ("RARE DROP!"), а основний текст іншого кольору.
     */
    public static <S> S dominantStyle(List<Seg<S>> line, int start, int end) {
        S best = styleAt(line, start);
        int bestLetters = -1;
        int pos = 0;
        for (Seg<S> seg : line) {
            String t = seg.text();
            int segStart = pos;
            pos += t.length();
            int from = Math.max(start, segStart);
            int to = Math.min(end, pos);
            int letters = 0;
            for (int i = from; i < to; i++) if (Character.isLetter(t.charAt(i - segStart))) letters++;
            if (letters > bestLetters) {
                bestLetters = letters;
                best = seg.style();
            }
        }
        return best;
    }

    /* Рядок із §-кодами: так його видно у файлі неперекладених рядків. */
    public static <S> String legacy(List<Seg<S>> line, StyleOps<S> ops) {
        StringBuilder sb = new StringBuilder();
        String last = null;
        for (Seg<S> seg : line) {
            if (seg.text().isEmpty()) continue;
            String codes = ops.legacy(seg.style());
            if (!codes.equals(last)) {
                sb.append(codes);
                last = codes;
            }
            sb.append(seg.text());
        }
        return sb.toString();
    }

    /* Шматки рядка в межах [from, to) зі своїми стилями */
    public static <S> List<Seg<S>> slice(List<Seg<S>> line, int from, int to) {
        List<Seg<S>> part = new ArrayList<>();
        int pos = 0;
        for (Seg<S> seg : line) {
            int a = Math.max(from, pos), b = Math.min(to, pos + seg.text().length());
            if (a < b) part.add(new Seg<>(seg.text().substring(a - pos, b - pos), seg.style()));
            pos += seg.text().length();
        }
        return part;
    }

    /* Частина рядка [from, to) у вигляді тексту з §-кодами (кожен шматок зі своїм стилем). */
    public static <S> String legacySlice(List<Seg<S>> line, int from, int to, StyleOps<S> ops) {
        List<Seg<S>> part = new ArrayList<>();
        int pos = 0;
        for (Seg<S> seg : line) {
            int a = Math.max(from, pos), b = Math.min(to, pos + seg.text().length());
            if (a < b) part.add(new Seg<>(seg.text().substring(a - pos, b - pos), seg.style()));
            pos += seg.text().length();
        }
        return legacy(part, ops);
    }

    /* Чи всі шматки рядка мають однакові клік/ховер-події. */
    public static <S> boolean uniformExtras(List<Seg<S>> line, StyleOps<S> ops) {
        S first = null;
        for (Seg<S> seg : line) {
            if (seg.text().isEmpty()) continue;
            if (first == null) first = seg.style();
            else if (!ops.sameExtras(first, seg.style())) return false;
        }
        return true;
    }
}
