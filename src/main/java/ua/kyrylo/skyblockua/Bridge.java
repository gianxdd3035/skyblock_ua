package ua.kyrylo.skyblockua;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import ua.kyrylo.skyblockua.core.Lines;
import ua.kyrylo.skyblockua.core.Seg;
import ua.kyrylo.skyblockua.core.Translator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/*
 * Міст між Minecraft і рушієм перекладу:
 * Component -> список шматків -> переклад -> новий Component.
 */
public final class Bridge {

    public static final McStyleOps OPS = new McStyleOps();
    public static final Translator<Style> TRANSLATOR = new Translator<>(OPS);

    /*
     * Тултіп перемальовується щокадру, тому результати кешуються за текстом із кольорами.
     * Для чату кеш не потрібен: кожне повідомлення обробляється один раз.
     */
    private static final int CACHE_SIZE = 8192;
    private static final Map<String, Optional<Component>> CACHE = new LinkedHashMap<>(1024, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Optional<Component>> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private Bridge() {
    }

    /* Кеш для рядків, які малюються як FormattedCharSequence (тултіпи, Iconographic). */
    private static final Map<String, Optional<FormattedCharSequence>> SEQ_CACHE = new LinkedHashMap<>(1024, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Optional<FormattedCharSequence>> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public static void clearCache() {
        CACHE.clear();
        SEQ_CACHE.clear();
    }

    /* Чи треба зараз перекладати взагалі. */
    public static boolean active() {
        Config c = Config.get();
        if (!c.enabled) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !mc.isSameThread()) return false;
        return !c.onlyOnHypixel || onHypixel(mc);
    }

    public static boolean onHypixel(Minecraft mc) {
        ServerData server = mc.getCurrentServer();
        if (server == null || server.ip == null) return false;
        String host = server.ip.trim().toLowerCase(Locale.ROOT).replaceFirst(":\\d+$", "");
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net")
                || host.equals("hypixel.io") || host.endsWith(".hypixel.io");
    }

    /* Розкласти Component на шматки з повністю обчисленим стилем. */
    public static List<Seg<Style>> flatten(Component component) {
        List<Seg<Style>> out = new ArrayList<>();
        component.visit((style, text) -> {
            Lines.appendLegacy(out, text, style, OPS);
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /* Скласти Component назад. */
    public static Component build(List<Seg<Style>> segs) {
        MutableComponent root = Component.empty();
        for (Seg<Style> s : segs) {
            if (!s.text().isEmpty()) root.append(Component.literal(s.text()).setStyle(s.style()));
        }
        return root;
    }

    /* Розкласти FormattedCharSequence (текст, уже готовий до малювання) на шматки. */
    public static List<Seg<Style>> flatten(FormattedCharSequence sequence) {
        List<Seg<Style>> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        Style[] current = {null};
        sequence.accept((index, style, codePoint) -> {
            if (current[0] != null && !current[0].equals(style) && buf.length() > 0) {
                Lines.appendLegacy(out, buf.toString(), current[0], OPS);
                buf.setLength(0);
            }
            current[0] = style;
            buf.appendCodePoint(codePoint);
            return true;
        });
        if (buf.length() > 0) Lines.appendLegacy(out, buf.toString(), current[0], OPS);
        return out;
    }

    /*
     * Перекласти рядок, який малюється як FormattedCharSequence.
     * Повертає перекладений рядок або той самий, якщо перекладати нічого.
     */
    public static FormattedCharSequence translateSequence(FormattedCharSequence sequence, Translator.Kind kind) {
        if (sequence == null || !Config.get().translateLore || !active()) return sequence;
        List<Seg<Style>> segs = flatten(sequence);
        if (segs.isEmpty()) return sequence;
        String key = kind.ordinal() + "|" + Lines.legacy(segs, OPS);
        Optional<FormattedCharSequence> cached = SEQ_CACHE.get(key);
        if (cached == null) {
            List<Seg<Style>> r = TRANSLATOR.translate(segs, kind);
            cached = Optional.ofNullable(r == null ? null : build(r).getVisualOrderText());
            SEQ_CACHE.put(key, cached);
        }
        return cached.orElse(sequence);
    }

    /* Як translate(), але повертає оригінал, якщо перекладу немає або переклад зараз вимкнено. */
    public static Component translateOrSame(Component component, Translator.Kind kind) {
        if (component == null || !Config.get().translateLore || !active()) return component;
        Component t = translate(component, kind);
        return t == null ? component : t;
    }

    /* Перекласти один рядок. Повертає null, якщо перекладу немає. */
    public static Component translate(Component component, Translator.Kind kind) {
        List<Seg<Style>> segs = flatten(component);
        if (segs.isEmpty()) return null;
        if (kind == Translator.Kind.CHAT) {
            List<Seg<Style>> r = TRANSLATOR.translate(segs, kind);
            return r == null ? null : build(r);
        }
        String key = kind.ordinal() + "|" + Lines.legacy(segs, OPS);
        Optional<Component> cached = CACHE.get(key);
        if (cached != null) return cached.orElse(null);
        List<Seg<Style>> r = TRANSLATOR.translate(segs, kind);
        Component result = r == null ? null : build(r);
        CACHE.put(key, Optional.ofNullable(result));
        return result;
    }

    /*
     * Перекласти тултіп предмета: перший рядок — назва, решта — опис.
     * Повертає новий список або той самий, якщо нічого не змінилося.
     */
    public static List<Component> translateTooltip(List<Component> lines) {
        Config c = Config.get();
        if (lines == null || lines.isEmpty() || !active()) return lines;
        /* З Iconographic рядки лишаються англійськими до малювання (див. IconographicCompat) */
        if (IconographicCompat.PRESENT) return lines;
        List<Component> out = null;
        for (int i = 0; i < lines.size(); i++) {
            Translator.Kind kind = i == 0 ? Translator.Kind.NAME : Translator.Kind.LORE;
            if (kind == Translator.Kind.LORE && !c.translateLore) break;
            Component t = translate(lines.get(i), kind);
            if (t == null) continue;
            if (out == null) out = new ArrayList<>(lines);
            out.set(i, t);
        }
        return out == null ? lines : out;
    }
}
