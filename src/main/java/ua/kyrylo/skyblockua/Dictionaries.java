package ua.kyrylo.skyblockua;

import ua.kyrylo.skyblockua.core.Dict;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 * Завантаження словників:
 *  1) вбудовані у .jar (список файлів — assets/skyblockua/dict/index.txt);
 *  2) твої власні з .minecraft/config/skyblockua/dict/ — вони мають пріоритет.
 */
public final class Dictionaries {

    private static final String BUNDLED = "/assets/skyblockua/dict/";

    private Dictionaries() {
    }

    public static Path userDir() {
        return Config.dir().resolve("dict");
    }

    public static Dict loadAll() {
        Dict dict = new Dict();
        for (String name : bundledFiles()) {
            try (InputStream in = Dictionaries.class.getResourceAsStream(BUNDLED + name)) {
                if (in == null) {
                    SkyblockUa.LOGGER.warn("Missing bundled dictionary {}", name);
                    continue;
                }
                dict.load(name, new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                SkyblockUa.LOGGER.warn("Could not read bundled dictionary {}", name, e);
            }
        }

        Path dir = userDir();
        try {
            Files.createDirectories(dir);
            writeReadme(dir);
            List<Path> files = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path p : ds) {
                    String n = p.getFileName().toString();
                    if (n.endsWith(".tsv") || n.endsWith(".txt") && n.startsWith("reforges")) files.add(p);
                }
            }
            Collections.sort(files);
            for (Path p : files) {
                try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    dict.load(p.getFileName().toString(), r);
                }
            }
        } catch (IOException e) {
            SkyblockUa.LOGGER.warn("Could not read user dictionaries", e);
        }
        SkyblockUa.LOGGER.info("SkyBlock UA: loaded {} dictionary entries ({} invalid lines skipped)", dict.size(), dict.invalidLines());
        return dict;
    }

    private static List<String> bundledFiles() {
        List<String> names = new ArrayList<>();
        try (InputStream in = Dictionaries.class.getResourceAsStream(BUNDLED + "index.txt")) {
            if (in == null) return names;
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String l;
            while ((l = br.readLine()) != null) {
                l = l.strip();
                if (!l.isEmpty() && !l.startsWith("#")) names.add(l);
            }
        } catch (IOException e) {
            SkyblockUa.LOGGER.warn("Could not read dictionary index", e);
        }
        return names;
    }

    private static void writeReadme(Path dir) throws IOException {
        Path readme = dir.resolve("README.txt");
        if (Files.exists(readme)) return;
        try (Writer w = Files.newBufferedWriter(readme, StandardCharsets.UTF_8)) {
            w.write("""
                    Тут можна класти власні переклади. Вони перекривають вбудовані.

                    Формат: файл .tsv у UTF-8, кожен рядок "англійською<TAB>українською".
                    Числа в англійській частині пиши як {0}, {1} ... і ті самі {0}, {1} став у переклад.
                    Кольори в перекладі — §-коди (§7 сірий, §a зелений, §c червоний, §l жирний тощо).
                    Рядки, що починаються з #, — коментарі.

                    Назва файлу визначає розділ:
                      names*.tsv   — назви предметів
                      labels*.tsv  — підписи перед двокрапкою (Strength -> Сила)
                      tokens*.tsv  — слова, що замінюються будь-де (RIGHT CLICK -> ПКМ)
                      інші *.tsv   — цілі рядки (описи, меню, чат)

                    Після змін: /ukr reload
                    """);
        }
    }
}
