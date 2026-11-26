package ua.kyrylo.skyblockua;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

/*
 * Клієнтські команди /ukr (на сервер не надсилаються).
 *   /ukr            — стан мода
 *   /ukr toggle     — увімкнути/вимкнути переклад
 *   /ukr names      — перекладати назви предметів чи ні
 *   /ukr lore | chat | titles | hypixel — інші перемикачі
 *   /ukr reload     — перечитати словники
 *   /ukr dump       — зберегти неперекладені рядки у файл
 *   /ukr clear      — очистити список неперекладених
 */
public final class UaCommands {

    private UaCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("ukr")
                .executes(UaCommands::status)
                .then(ClientCommands.literal("toggle").executes(ctx -> flip(ctx, "Переклад", c -> c.enabled, (c, v) -> c.enabled = v)))
                .then(ClientCommands.literal("names").executes(ctx -> flip(ctx, "Назви предметів", c -> c.translateNames, (c, v) -> c.translateNames = v)))
                .then(ClientCommands.literal("lore").executes(ctx -> flip(ctx, "Описи предметів", c -> c.translateLore, (c, v) -> c.translateLore = v)))
                .then(ClientCommands.literal("chat").executes(ctx -> flip(ctx, "Чат", c -> c.translateChat, (c, v) -> c.translateChat = v)))
                .then(ClientCommands.literal("titles").executes(ctx -> flip(ctx, "Заголовки меню", c -> c.translateTitles, (c, v) -> c.translateTitles = v)))
                .then(ClientCommands.literal("hypixel").executes(ctx -> flip(ctx, "Лише на Hypixel", c -> c.onlyOnHypixel, (c, v) -> c.onlyOnHypixel = v)))
                .then(ClientCommands.literal("collect").executes(ctx -> flip(ctx, "Збір неперекладеного", c -> c.collectMissing, (c, v) -> c.collectMissing = v)))
                .then(ClientCommands.literal("reload").executes(ctx -> {
                    SkyblockUa.reload();
                    say(ctx, "§aСловники перечитано: " + Bridge.TRANSLATOR.dict().size() + " записів.");
                    return 1;
                }))
                .then(ClientCommands.literal("dump").executes(ctx -> {
                    Path f = SkyblockUa.saveMissing();
                    say(ctx, "§aЗбережено " + Bridge.TRANSLATOR.missing().size() + " неперекладених рядків у:");
                    say(ctx, "§7" + f.toAbsolutePath());
                    return 1;
                }))
                .then(ClientCommands.literal("clear").executes(ctx -> {
                    Bridge.TRANSLATOR.missing().clear();
                    say(ctx, "§aСписок неперекладених очищено.");
                    return 1;
                })));
    }

    private interface Setter {
        void set(Config c, boolean value);
    }

    private static int flip(CommandContext<FabricClientCommandSource> ctx, String what,
                            Function<Config, Boolean> getter, Setter setter) {
        Config c = Config.get();
        boolean value = !getter.apply(c);
        setter.set(c, value);
        Config.save();
        SkyblockUa.applyConfig();
        say(ctx, "§e" + what + ": " + (value ? "§aувімкнено" : "§cвимкнено"));
        return 1;
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        Config c = Config.get();
        Consumer<String> line = s -> say(ctx, s);
        line.accept("§6SkyBlock UA");
        line.accept("§7Переклад: " + onOff(c.enabled) + "§7, на Hypixel зараз: " + onOff(Bridge.onHypixel(Minecraft.getInstance())));
        line.accept("§7Назви: " + onOff(c.translateNames) + "§7, описи: " + onOff(c.translateLore)
                + "§7, чат: " + onOff(c.translateChat) + "§7, меню: " + onOff(c.translateTitles));
        line.accept("§7Записів у словнику: §f" + Bridge.TRANSLATOR.dict().size()
                + "§7, неперекладених зібрано: §f" + Bridge.TRANSLATOR.missing().size());
        line.accept("§8/ukr toggle | names | lore | chat | titles | hypixel | collect | reload | dump | clear");
        return 1;
    }

    private static String onOff(boolean v) {
        return v ? "§aтак" : "§cні";
    }

    private static void say(CommandContext<FabricClientCommandSource> ctx, String text) {
        ctx.getSource().sendFeedback(Component.literal(text));
    }
}
