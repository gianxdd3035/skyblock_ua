package ua.kyrylo.skyblockua;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.kyrylo.skyblockua.core.Translator;
import ua.kyrylo.skyblockua.mixin.ChatComponentAccessor;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/*
 * Точка входу мода.
 */
public final class SkyblockUa implements ClientModInitializer {

    public static final String MOD_ID = "skyblockua";
    public static final Logger LOGGER = LoggerFactory.getLogger("SkyBlock UA");

    private static KeyMapping toggleKey;

    /*
     * Повідомлення чату, які вже перевірені. Порівнюються за посиланням (identity),
     * бо два різні повідомлення можуть мати однаковий текст.
     */
    private static final Set<Component> SEEN_CHAT = Collections.newSetFromMap(new IdentityHashMap<>());

    @Override
    public void onInitializeClient() {
        Config.load();
        reload();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.skyblockua.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_U, category));

        ClientTickEvents.END_CLIENT_TICK.register(SkyblockUa::onTick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> UaCommands.register(dispatcher));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveMissing());
    }

    /* Перечитати словники й застосувати налаштування. */
    public static void reload() {
        Bridge.TRANSLATOR.setDict(Dictionaries.loadAll());
        applyConfig();
    }

    public static void applyConfig() {
        Config c = Config.get();
        Bridge.TRANSLATOR.translateNames = c.translateNames;
        Bridge.TRANSLATOR.collectMissing = c.collectMissing;
        Bridge.clearCache();
    }

    private static void onTick(Minecraft client) {
        while (toggleKey.consumeClick()) {
            Config c = Config.get();
            c.enabled = !c.enabled;
            Config.save();
            Bridge.clearCache();
            client.gui.hud.setOverlayMessage(Component.literal(
                    c.enabled ? "§aSkyBlock UA: переклад увімкнено" : "§cSkyBlock UA: переклад вимкнено"), false);
        }
        if (Config.get().translateChat && Bridge.active()) translateRecentChat(client);
    }

    /*
     * Перекладаємо повідомлення вже після того, як вони потрапили в чат.
     * Так інші моди (SkyHanni, Skyblocker) встигають прочитати оригінальний англійський текст,
     * а змінюється тільки те, що показано на екрані.
     * allMessages упорядкований від найновішого до найстарішого, тому йдемо з початку
     * й зупиняємося на першому вже перевіреному повідомленні.
     */
    private static void translateRecentChat(Minecraft client) {
        ChatComponent chat = client.gui.hud.getChat();
        ChatComponentAccessor accessor = (ChatComponentAccessor) chat;
        List<GuiMessage> all = accessor.skyblockua$allMessages();
        boolean changed = false;
        int limit = Math.min(all.size(), 100);
        for (int i = 0; i < limit; i++) {
            GuiMessage msg = all.get(i);
            if (SEEN_CHAT.contains(msg.content())) break;
            SEEN_CHAT.add(msg.content());
            Component translated = Bridge.translate(msg.content(), Translator.Kind.CHAT);
            if (translated == null) continue;
            all.set(i, new GuiMessage(msg.addedTime(), translated, msg.signature(), msg.source(), msg.tag()));
            SEEN_CHAT.add(translated);
            changed = true;
        }
        if (SEEN_CHAT.size() > 5000) SEEN_CHAT.clear();
        if (changed) accessor.skyblockua$refreshTrimmedMessages();
    }

    public static Path missingFile() {
        return Config.dir().resolve("missing.tsv");
    }

    /* Зберегти неперекладені рядки у файл. */
    public static Path saveMissing() {
        Path f = missingFile();
        try {
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                Bridge.TRANSLATOR.missing().write(w);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not save missing lines", e);
        }
        return f;
    }
}
