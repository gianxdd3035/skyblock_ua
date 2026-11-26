package ua.kyrylo.skyblockua;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/*
 * Налаштування мода. Зберігаються у .minecraft/config/skyblockua/config.json.
 */
public final class Config {

    /* Увесь мод увімкнено (перемикається клавішею U або /ukr toggle). */
    public boolean enabled = true;
    /* Перекладати тільки на Hypixel. */
    public boolean onlyOnHypixel = true;
    /* Назви предметів (вимкни, щоб шукати в Аукціоні/Базарі за англійськими назвами). */
    public boolean translateNames = true;
    /* Описи предметів. */
    public boolean translateLore = true;
    /* Повідомлення сервера й діалоги NPC у чаті. */
    public boolean translateChat = true;
    /* Заголовки меню (інвентарів). */
    public boolean translateTitles = true;
    /* Збирати неперекладені рядки для /ukr dump. */
    public boolean collectMissing = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance = new Config();

    public static Config get() {
        return instance;
    }

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("skyblockua");
    }

    private static Path file() {
        return dir().resolve("config.json");
    }

    public static void load() {
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                Config c = GSON.fromJson(r, Config.class);
                if (c != null) instance = c;
            } catch (IOException | RuntimeException e) {
                SkyblockUa.LOGGER.warn("Could not read config, using defaults", e);
            }
        }
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(dir());
            try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                GSON.toJson(instance, w);
            }
        } catch (IOException e) {
            SkyblockUa.LOGGER.warn("Could not save config", e);
        }
    }
}
