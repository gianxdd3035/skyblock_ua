package ua.kyrylo.skyblockua;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import ua.kyrylo.skyblockua.core.StyleOps;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/*
 * Реалізація StyleOps для справжнього стилю Minecraft.
 *
 * У Minecraft 26.2 з ChatFormatting прибрали isColor(), getChar() і getColor(),
 * тому таблицю "код -> формат" будуємо самі через getByCode(),
 * а колір формату дізнаємося через TextColor.fromLegacyFormat() (для форматів без кольору він повертає null).
 */
public final class McStyleOps implements StyleOps<Style> {

    /* RGB-значення кольору -> символ коду (щоб перетворити стиль назад на §-коди) */
    private static final Map<Integer, Character> COLOR_CODES = new HashMap<>();

    static {
        for (char c : "0123456789abcdefklmnor".toCharArray()) {
            ChatFormatting f = ChatFormatting.getByCode(c);
            if (f == null) continue;
            TextColor color = TextColor.fromLegacyFormat(f);
            if (color != null) COLOR_CODES.putIfAbsent(color.getValue(), c);
        }
    }

    private static boolean isColor(ChatFormatting f) {
        return TextColor.fromLegacyFormat(f) != null;
    }

    @Override
    public Style applyCode(Style base, char code) {
        ChatFormatting f = ChatFormatting.getByCode(code);
        if (f == null) return base;
        /*
         * Колір і §r скидають жирність/курсив (як у Minecraft), але клік і ховер лишаються.
         * Курсив явно вимикаємо, бо інакше рядки опису стали б фіолетовим курсивом.
         */
        if (f == ChatFormatting.RESET) return keepExtras(Style.EMPTY.withItalic(false), base);
        if (isColor(f)) return keepExtras(Style.EMPTY.withColor(f).withItalic(false), base);
        return switch (f) {
            case BOLD -> base.withBold(true);
            case ITALIC -> base.withItalic(true);
            case UNDERLINE -> base.withUnderlined(true);
            case STRIKETHROUGH -> base.withStrikethrough(true);
            case OBFUSCATED -> base.withObfuscated(true);
            default -> base;
        };
    }

    private static Style keepExtras(Style style, Style from) {
        return style.withClickEvent(from.getClickEvent())
                .withHoverEvent(from.getHoverEvent())
                .withInsertion(from.getInsertion());
    }

    @Override
    public String legacy(Style s) {
        StringBuilder sb = new StringBuilder();
        TextColor color = s.getColor();
        if (color != null) {
            Character c = COLOR_CODES.get(color.getValue());
            if (c != null) sb.append('§').append(c);
            else sb.append("§#").append(String.format("%06x", color.getValue()));
        }
        if (s.isObfuscated()) sb.append("§k");
        if (s.isBold()) sb.append("§l");
        if (s.isStrikethrough()) sb.append("§m");
        if (s.isUnderlined()) sb.append("§n");
        if (s.isItalic()) sb.append("§o");
        return sb.toString();
    }

    @Override
    public boolean sameExtras(Style a, Style b) {
        return Objects.equals(a.getClickEvent(), b.getClickEvent())
                && Objects.equals(a.getHoverEvent(), b.getHoverEvent())
                && Objects.equals(a.getInsertion(), b.getInsertion());
    }
}
