import ua.kyrylo.skyblockua.core.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/*
 * Перевірка рушія без Minecraft.
 * Запуск (з кореня проєкту):
 *   javac -d build/enginetest src/main/java/ua/kyrylo/skyblockua/core/*.java tools/EngineTest.java
 *   java -cp build/enginetest EngineTest src/main/resources/assets/skyblockua/dict [шлях до NEU items]
 *
 * 1) Кілька ручних перевірок механіки (числа, кольори, рефорж, зірки, підписи, NPC).
 * 2) Якщо вказано NEU — проганяє всі назви й рядки описів і рахує покриття.
 */
public class EngineTest {

    /* Простий стиль для тестів: колір, формати, "подія" (замість кліку/ховера). */
    record TStyle(char color, String fmt, String extra) {
    }

    static final StyleOps<TStyle> OPS = new StyleOps<>() {
        public TStyle applyCode(TStyle base, char code) {
            if ("0123456789abcdef".indexOf(code) >= 0) return new TStyle(code, "", base.extra());
            if (code == 'r') return new TStyle('f', "", base.extra());
            if ("klmno".indexOf(code) >= 0) return new TStyle(base.color(), base.fmt().indexOf(code) >= 0 ? base.fmt() : base.fmt() + code, base.extra());
            return base;
        }

        public String legacy(TStyle s) {
            StringBuilder sb = new StringBuilder("§").append(s.color());
            for (char c : s.fmt().toCharArray()) sb.append('§').append(c);
            return sb.toString();
        }

        public boolean sameExtras(TStyle a, TStyle b) {
            return a.extra().equals(b.extra());
        }
    };

    static final TStyle BASE = new TStyle('f', "", "");
    static int failures = 0;

    static List<Seg<TStyle>> parse(String legacy) {
        List<Seg<TStyle>> out = new ArrayList<>();
        Lines.appendLegacy(out, legacy, BASE, OPS);
        return out;
    }

    static String run(Translator<TStyle> t, String legacy, Translator.Kind kind) {
        List<Seg<TStyle>> r = t.translate(parse(legacy), kind);
        return r == null ? null : Lines.legacy(r, OPS);
    }

    static void check(String what, String got, String expected) {
        if (!Objects.equals(got, expected)) {
            failures++;
            System.out.println("FAIL: " + what + "\n   got:      " + got + "\n   expected: " + expected);
        }
    }

    static void expect(Translator<TStyle> t, String legacy, Translator.Kind kind, String expected) {
        String got = run(t, legacy, kind);
        if (!Objects.equals(got, expected)) {
            failures++;
            System.out.println("FAIL: " + legacy + "\n   got:      " + got + "\n   expected: " + expected);
        }
    }

    public static void main(String[] args) throws Exception {
        mechanics();
        if (args.length >= 1) {
            Dict dict = loadDir(Paths.get(args[0]));
            System.out.println("dictionary entries: " + dict.size() + " (lines " + dict.size(Dict.Section.LINES)
                    + ", names " + dict.size(Dict.Section.NAMES) + ", labels " + dict.size(Dict.Section.LABELS)
                    + ", tokens " + dict.size(Dict.Section.TOKENS) + "), invalid lines: " + dict.invalidLines());
            if (args.length >= 2 && args[1].endsWith(".tsv")) played(dict, Paths.get(args[1]));
            else if (args.length >= 2) corpus(dict, Paths.get(args[1]));
        }
        System.out.println(failures == 0 ? "ALL MECHANICS TESTS PASSED" : failures + " FAILURES");
        if (failures > 0) System.exit(1);
    }

    static void mechanics() throws IOException {
        Dict d = new Dict();
        d.load("lines.tsv", new StringReader(String.join("\n",
                "# comment",
                "This item can be reforged!\t§8Цей предмет можна перекувати!",
                "Max Storage: {0}\t§7Макс. сховище: §b{0}",
                "Grants {0} Strength.\t§7Дає §c{0} Сили§7.",
                "LEGENDARY SWORD\t§6§lЛЕГЕНДАРНИЙ МЕЧ",
                "Close\tЗакрити",
                "Hello there!\tПривіт!",
                "Yes\tТак",
                "")));
        d.load("names_items.tsv", new StringReader("Superior Dragon Helmet\tШолом Вищого Дракона\nHyperion\tГіперіон\n"));
        d.load("labels.tsv", new StringReader("Strength\tСила\nAbility\tЗдібність\n"));
        d.load("tokens.tsv", new StringReader("RIGHT CLICK\tПКМ\nLvl\tРів.\n"));
        d.load("reforges.tsv", new StringReader("Fierce\tЛютий\tЛюта\tЛюте\tЛюті\nWise\tМудрий\tМудра\tМудре\tМудрі\nAncient\tСтародавній\tСтародавня\tСтародавнє\tСтародавні\nGreen Thumb\tСадівничий\tСадівнича\tСадівниче\tСадівничі\n"));
        d.load("armor_pieces.tsv", new StringReader("Helmet\tшолом\tm\nBoots\tчоботи\tp\nNecklace\tнамисто\tn\nGauntlet\tлатна рукавиця\tf\n"));
        d.load("armor_sets.tsv", new StringReader("Necron's\tНекрона\nDiamond\tadj:Алмазний\nHardened Diamond\tadj:Загартований алмазний\nMolten\tadj:Розплавлений\n"));
        d.load("names_test.tsv", new StringReader("Golden Axe\tЗолота сокира\nAspect of the End\tАспект Енду\nGiant's Sword\tМеч Велетня\tm\nRancher's Boots\tЧоботи скотаря\n"));
        Translator<TStyle> t = new Translator<>(OPS);
        t.setDict(d);
        t.collectMissing = true;

        /* цілий рядок, кольори з перекладу */
        expect(t, "§8This item can be reforged!", Translator.Kind.LORE, "§8Цей предмет можна перекувати!");
        /* числа */
        expect(t, "§7Max Storage: §b1,024", Translator.Kind.LORE, "§7Макс. сховище: §b1,024");
        expect(t, "§7Grants §c+15 §cStrength§7.", Translator.Kind.LORE, "§7Дає §c+15 Сили§7.");
        /* пробіли на початку зберігаються */
        expect(t, "§7  Max Storage: §b64", Translator.Kind.LORE, "§7  Макс. сховище: §b64");
        /* рекомбобулятор: "a" по краях лишаються з власним стилем */
        expect(t, "§6§l§ka§r §6§lLEGENDARY SWORD §6§l§ka", Translator.Kind.LORE,
                "§6§l§ka§f §6§lЛЕГЕНДАРНИЙ МЕЧ §6§l§ka");
        /* підпис + решта без змін */
        expect(t, "§7Strength: §c+50 §9(+10)", Translator.Kind.LORE, "§7Сила: §c+50 §9(+10)");
        /* підпис + токен */
        expect(t, "§6Ability: Instant Transmission §e§lRIGHT CLICK", Translator.Kind.LORE,
                "§6Здібність: Instant Transmission §e§lПКМ");
        /* назва з рефоржем (узгодженим за родом) і зірками */
        expect(t, "§6Fierce Superior Dragon Helmet §6✪✪✪", Translator.Kind.NAME,
                "§6Лютий Шолом Вищого Дракона ✪✪✪");
        /* броня: частина + сет, рефорж у роді частини */
        expect(t, "§6Ancient Necron's Boots", Translator.Kind.NAME, "§6Стародавні чоботи Некрона");
        expect(t, "§5Necron's Helmet", Translator.Kind.NAME, "§5Шолом Некрона");
        expect(t, "§9Hardened Diamond Boots", Translator.Kind.NAME, "§9Загартовані алмазні чоботи");
        expect(t, "§9Wise Diamond Helmet", Translator.Kind.NAME, "§9Мудрий алмазний шолом");
        expect(t, "§5Fierce Molten Necklace", Translator.Kind.NAME, "§5Люте розплавлене намисто");
        expect(t, "§5Molten Gauntlet", Translator.Kind.NAME, "§5Розплавлена латна рукавиця");
        /* рід із перекладу назви (вгадується) або з третього стовпця */
        expect(t, "§9Green Thumb Golden Axe", Translator.Kind.NAME, "§9Садівнича Золота сокира");
        expect(t, "§6Fierce Giant's Sword", Translator.Kind.NAME, "§6Лютий Меч Велетня");
        expect(t, "§6Wise Rancher's Boots", Translator.Kind.NAME, "§6Мудрі Чоботи скотаря");
        /* невідома база: рефорж лишається англійською разом із назвою */
        expect(t, "§6Fierce Unknownium", Translator.Kind.NAME, null);
        /* ANY: тултіп, який малює інший мод — рядок опису і назва */
        expect(t, "§8This item can be reforged!", Translator.Kind.ANY, "§8Цей предмет можна перекувати!");
        expect(t, "§6Wise Necron's Helmet", Translator.Kind.ANY, "§6Мудрий шолом Некрона");
        /* окремий фрагмент-підпис (так стати малює Iconographic) */
        expect(t, "§cStrength", Translator.Kind.ANY, "§cСила");
        /* подвійний префікс, рамка тегу, список зачарувань, Skyblocker */
        Dict ed = new Dict();
        ed.load("reforges.tsv", new StringReader("Shiny\tСяючий\tСяюча\tСяюче\tСяючі\nHeroic\tГероїчний\tГероїчна\tГероїчне\tГероїчні\n"));
        ed.load("names_t.tsv", new StringReader("Hyperion\tГіперіон\n"));
        ed.load("lines_t.tsv", new StringReader("MYTHIC\tМІФІЧНИЙ\nSharpness\tГострота\nCritical\tКритичний удар\nBane of Arthropods\tЗагибель членистоногих\nDUNGEON SWORD\tМеч підземель\nLegion\tЛегіон\n"));
        ed.load("patterns_t.tsv", new StringReader("{item} {0} | {1}\t{item} {0} | {1}\n"));
        ed.load("names_m.tsv", new StringReader("Enchanted Sponge\tЗачарована губка\n"));
        Translator<TStyle> et = new Translator<>(OPS);
        et.setDict(ed);
        expect(et, "§dShiny Heroic Hyperion", Translator.Kind.NAME, "§dСяючий героїчний Гіперіон");
        expect(et, "§d> Mythic <", Translator.Kind.ANY, "§d> МІФІЧНИЙ <");
        expect(et, "§7dungeon sword", Translator.Kind.ANY, "§7Меч підземель");
        expect(et, "§7DUNGEON SWORD", Translator.Kind.ANY, "§7МЕЧ ПІДЗЕМЕЛЬ");
        expect(et, "§dLEGION", Translator.Kind.ANY, "§dЛЕГІОН");
        expect(et, "§9Sharpness VI, §9Critical VI", Translator.Kind.LORE, "§9Гострота VI, Критичний удар VI");
        expect(et, "§e› Bane Of Arthropods VI→VII, Sharpness VI→VII", Translator.Kind.ANY, "§e› Загибель членистоногих VI→VII, Гострота VI→VII");
        expect(et, "§9▶ §7Enchanted Sponge §c7.2k§8 | §a10.3k", Translator.Kind.ANY, "§9▶ §7Зачарована губка§8 §c7.2k§8 | §a10.3k");
        /* кольори в перекладі без §-кодів, теги Iconographic, здібності, книги */
        Dict cd2 = new Dict();
        cd2.load("lines_c.tsv", new StringReader(
                "Grants {0}\u279C{1} \u2763 Charm Chance.\tДає {0}\u279C{1} \u2763 шансу чарів.\n"
                + "{0} SkyBlock XP\t{0} досвіду SkyBlock\n"
                + "{0} Farming XP\t{0} досвіду фермерства\n"
                + "Grants {0}\u2764 Health and {1}\u2748 Defense.\tДає {0}\u2764 здоров'я і {1}\u2748 захисту.\n"
                + "Sharpness\tГострота\nSmite\tКара\nWither Impact\tУдар візера\n"));
        cd2.load("labels_c.tsv", new StringReader("Ability\tЗдібність\nFull Set Bonus\tБонус повного комплекту\n"));
        cd2.load("tokens_c.tsv", new StringReader("RIGHT CLICK\tПКМ\n"));
        cd2.load("patterns_c.tsv", new StringReader("Enchanted Book ({item})\tЗачарована книга ({item})\n"));
        Translator<TStyle> ct2 = new Translator<>(OPS);
        ct2.setDict(cd2);
        expect(ct2, "§fGrants §a+§81.64\u279C§a1.68§f §b\u2763 Charm Chance§f.", Translator.Kind.LORE,
                "§fДає §a+§81.64\u279C§a1.68§f §b\u2763 шансу чарів§f.");
        expect(ct2, "§8+§b20 SkyBlock XP", Translator.Kind.LORE, "§8+§b20 досвіду SkyBlock");
        expect(ct2, "§8+§3912 §7Farming XP", Translator.Kind.LORE, "§8+§3912§7 досвіду фермерства");
        expect(ct2, "§7Grants §a+5§c\u2764 Health §7and §a+3§a\u2748 Defense§7.", Translator.Kind.LORE,
                "§7Дає §a+5§c\u2764 здоров'я§7 і §a+3\u2748 захисту§7.");
        expect(ct2, "§9Sharpness\uE917\uE900\uE910VII\uE905\uE910", Translator.Kind.ANY, "§9Гострота\uE917\uE900\uE910VII\uE905\uE910");
        expect(ct2, "§6Ability: Wither Impact  §e§lRIGHT CLICK", Translator.Kind.LORE, "§6Здібність: Удар візера  §e§lПКМ");
        expect(ct2, "§6Ability: Wither Impact", Translator.Kind.ANY, "§6Здібність: Удар візера");
        expect(ct2, "§fEnchanted Book (Smite VI)", Translator.Kind.NAME, "§fЗачарована книга (Кара VI)");
        cd2.load("lines_m.tsv", new StringReader("Bestiary\tБестіарій\nSpider's Den\tПавуче лігво\nPets\tУлюбленці\n"));
        expect(ct2, "§8(2/3) Bestiary \u279C Spider's Den", Translator.Kind.TITLE, "§8(2/3) Бестіарій \u279C Павуче лігво");
        expect(ct2, "§8Spider's Den \u279C Arachne", Translator.Kind.TITLE, "§8Павуче лігво \u279C Arachne");
        expect(ct2, "§8(1/3) Pets", Translator.Kind.TITLE, "§8(1/3) Улюбленці");
        cd2.load("npcs_t.tsv", new StringReader("Jacob\tДжейкоб\nSafari Receptionist\tАдміністраторка сафарі\n"));
        cd2.load("chat_t.tsv", new StringReader("Welcome to the Critter Safari!\tЛаскаво просимо на сафарі звірят!\nCLICK\tНАТИСНИ\n"));
        expect(ct2, "§e[NPC] §bSafari Receptionist§f: Welcome to the Critter Safari!", Translator.Kind.CHAT,
                "§e[NPC] §bАдміністраторка сафарі§f: Ласкаво просимо на сафарі звірят!");
        expect(ct2, "§e[NPC] Jacob§f: Something new", Translator.Kind.CHAT, "§e[NPC] Джейкоб§f: Something new");
        expect(ct2, "§9Jacob", Translator.Kind.NAME, "§9Джейкоб");
        /* стиль, якого не передати §-кодами (шрифт іншого мода, як у Feesh), має лишитися на захопленому імені */
        cd2.load("patterns_f.tsv", new StringReader("A {any} has spawned!\tЗ'явилася істота: {any}!\n"));
        List<Seg<TStyle>> feesh = new ArrayList<>();
        feesh.add(new Seg<>("A ", new TStyle('b', "", "")));
        feesh.add(new Seg<>("Hfffce4agg", new TStyle('z', "", "")));
        feesh.add(new Seg<>(" has spawned!", new TStyle('b', "", "")));
        List<Seg<TStyle>> feeshOut = ct2.translate(feesh, Translator.Kind.CHAT);
        boolean kept = feeshOut != null && feeshOut.stream().anyMatch(sg -> sg.text().equals("Hfffce4agg") && sg.style().color() == 'z');
        check("foreign style kept in {any}", kept ? "kept" : String.valueOf(feeshOut), "kept");
        cd2.load("stats_t.tsv", new StringReader("Strength\tсили\nCombat Wisdom\tбойової мудрості\n"));
        cd2.load("patterns_s.tsv", new StringReader("Grants {0}{stat}.\tДає {0}{stat}.\nIncreases {stat} by {0}.\tДодає {0} до {stat}.\n"));
        expect(ct2, "§7Grants §c+50 \uE00D Strength§7.", Translator.Kind.LORE, "§7Дає §c+50 \uE00D сили§7.");
        expect(ct2, "§7Grants §3+20\u262F Combat Wisdom§7.", Translator.Kind.LORE, "§7Дає §3+20\u262F бойової мудрості§7.");
        expect(ct2, "§7Increases §c\u2741 Strength §7by §a30§7.", Translator.Kind.LORE, "§7Додає §a30§7 до §c\u2741 сили§7.");
        check("unknown stat -> no pattern", run(ct2, "§7Grants §a+5 §fWeird Stat§7.", Translator.Kind.LORE), null);
        List<Seg<TStyle>> tag = ct2.translateTag(parse("§6Jacob"));
        check("nametag", tag == null ? null : Lines.legacy(tag, OPS), "§6Джейкоб");
        List<Seg<TStyle>> tag2 = ct2.translateTag(parse("§e§lCLICK"));
        check("nametag click", tag2 == null ? null : Lines.legacy(tag2, OPS), "§e§lНАТИСНИ");
        check("mob tag untouched", ct2.translateTag(parse("§c[Lv10] Zombie §a100§f/§a100§c❤")) == null ? "null" : "changed", "null");
        /* рід за першим словом */
        String[][] genders = {{"Зачарована книга", "f"}, {"Вугілля", "n"}, {"Насіння", "n"}, {"Кристали призмарину", "p"},
                {"Шолом Вищого Дракона", "m"}, {"Риба-фугу", "f"}, {"Сіль", "f"}, {"Око Енду", "n"}, {"Стародавній меч", "m"}};
        for (String[] g : genders) {
            char got = UaGender.guess(g[0]);
            if (got != g[1].charAt(0)) {
                failures++;
                System.out.println("FAIL: gender of " + g[0] + " = " + got);
            }
        }
        expect(t, "§dHyperion", Translator.Kind.NAME, "§dГіперіон");
        /* вимкнені назви: предмет не перекладається, а кнопка меню — так */
        t.translateNames = false;
        expect(t, "§dHyperion", Translator.Kind.NAME, null);
        expect(t, "§cClose", Translator.Kind.NAME, "§cЗакрити");
        t.translateNames = true;
        /* пет: токен Lvl */
        expect(t, "§7[Lvl 100] §6Golden Dragon", Translator.Kind.NAME, "§7[Рів. 100] §6Golden Dragon");
        /* NPC */
        expect(t, "§e[NPC] Jerry§f: Hello there!", Translator.Kind.CHAT, "§e[NPC] Jerry§f: Привіт!");
        /* чат: лічильник повторів від модів і капсовий префікс у власному кольорі */
        Dict cd = new Dict();
        cd.load("chat.tsv", new StringReader("RARE DROP!\tРІДКІСНИЙ ДРОП!\nYour inventory is full!\tТвій інвентар заповнено!\nYou dropped {0}x Bone!\tТобі випало {0}x кістки!\n"));
        cd.load("patterns.tsv", new StringReader("You dropped {0}x {item}!\tТобі випало {0}x {item}!\nYou caught a {any}!\tТи зловив: {any}!\n"));
        cd.load("names_x.tsv", new StringReader("Enchanted Bone\tЗачарована кістка\n"));
        Translator<TStyle> ct = new Translator<>(OPS);
        ct.setDict(cd);
        expect(ct, "§cYour inventory is full! §7(×3)", Translator.Kind.CHAT, "§cТвій інвентар заповнено! §7(×3)");
        expect(ct, "§6§lRARE DROP! §fYou dropped §a2x Bone§f!", Translator.Kind.CHAT, "§6§lРІДКІСНИЙ ДРОП! §fТобі випало §a2x§f кістки!");
        expect(ct, "§6§lRARE DROP! §fYou dropped §9Something§f!", Translator.Kind.CHAT, "§6§lРІДКІСНИЙ ДРОП! §fYou dropped §9Something§f!");
        /* шаблон: назва предмета перекладається й зберігає свій колір */
        expect(ct, "§6§lRARE DROP! §fYou dropped §a3x §9Enchanted Bone§f!", Translator.Kind.CHAT, "§6§lРІДКІСНИЙ ДРОП! §fТобі випало §a3§fx §9Зачарована кістка§f!");
        expect(ct, "§7You caught a §cFrog Man§7!", Translator.Kind.CHAT, "§7Ти зловив: §cFrog Man§7!");
        /* кнопки з різними подіями: перекладаються по шматках */
        List<Seg<TStyle>> buttons = List.of(
                new Seg<>("[", new TStyle('8', "", "")),
                new Seg<>("Yes", new TStyle('a', "", "click:yes")),
                new Seg<>("] ", new TStyle('8', "", "")));
        List<Seg<TStyle>> res = t.translate(buttons, Translator.Kind.CHAT);
        if (res == null || !Lines.plain(res).equals("[Так] ") || !res.get(1).style().extra().equals("click:yes")) {
            failures++;
            System.out.println("FAIL: segment-wise buttons -> " + (res == null ? null : Lines.plain(res)));
        }
        /* кирилиця не перекладається повторно, невідоме йде в missing */
        expect(t, "§7Сила: +5", Translator.Kind.LORE, null);
        expect(t, "§7Something unknown 42", Translator.Kind.LORE, null);
        StringWriter sw = new StringWriter();
        t.missing().write(sw);
        if (!sw.toString().contains("Something unknown {0}")) {
            failures++;
            System.out.println("FAIL: missing not recorded:\n" + sw);
        }
        /* чат гравців у missing не потрапляє */
        t.missing().clear();
        run(t, "§b[MVP§c+§b] Steve§f: hi guys", Translator.Kind.CHAT);
        run(t, "§2Guild > §bAlex§f: gm", Translator.Kind.CHAT);
        if (t.missing().size() != 0) {
            failures++;
            System.out.println("FAIL: player chat recorded");
        }
        /* шаблон чисел */
        Template tp = Template.of("Cost: 1,250,000 Coins, 5.5k, -3%, 1-5, x64, 10s, T5");
        if (!tp.key.equals("Cost: {0} Coins, {1}, {2}, {3}-{4}, x64, {5}s, T5")) {
            failures++;
            System.out.println("FAIL: template " + tp.key + " " + tp.args);
        }
    }

    static Dict loadDir(Path dir) throws IOException {
        Dict d = new Dict();
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) if (p.toString().endsWith(".tsv") || p.getFileName().toString().startsWith("reforges")) files.add(p);
        }
        Collections.sort(files);
        for (Path p : files) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                d.load(p.getFileName().toString(), r);
            }
        }
        return d;
    }

    /* Перевірити файл missing.tsv з гри: скільки рядків тепер перекладається; решта -> build/played_left.tsv */
    static void played(Dict dict, Path file) throws Exception {
        Translator<TStyle> t = new Translator<>(OPS);
        t.setDict(dict);
        int total = 0, done = 0;
        List<String> left = new ArrayList<>();
        for (String row : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String[] c = row.split("\t", -1);
            if (c.length < 4 || c[0].startsWith("#") || c[0].equals("kind")) continue;
            Translator.Kind kind = Translator.Kind.valueOf(c[0]);
            String example = c[3].isEmpty() ? c[2] : c[3];
            total++;
            String r = run(t, example, kind);
            if (r != null) done++;
            else left.add(row);
        }
        System.out.printf("played lines: %d/%d translated%n", done, total);
        Files.write(Paths.get("build/played_left.tsv"), left, StandardCharsets.UTF_8);
    }

    /* Прогнати весь корпус NEU і порахувати, яка частка рядків перекладається. */
    static void corpus(Dict dict, Path itemsDir) throws Exception {
        Translator<TStyle> t = new Translator<>(OPS);
        t.setDict(dict);
        int loreTotal = 0, loreDone = 0, nameTotal = 0, nameDone = 0, broken = 0;
        List<String> samples = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(itemsDir, "*.json")) {
            for (Path p : ds) {
                String json = Files.readString(p);
                String name = jsonString(json, "displayname");
                if (name != null) {
                    nameTotal++;
                    String r = run(t, name, Translator.Kind.NAME);
                    if (r != null) nameDone++;
                }
                for (String l : jsonArray(json, "lore")) {
                    if (l.replaceAll("§.", "").isBlank()) continue;
                    loreTotal++;
                    String r = run(t, l, Translator.Kind.LORE);
                    if (r != null) {
                        loreDone++;
                        if (r.matches(".*\\{\\d}.*")) {
                            broken++;
                            System.out.println("  broken: " + l + " -> " + r);
                        }
                        if (samples.size() < 25 && Math.random() < 0.002) samples.add(l + "\n      -> " + r);
                    }
                }
            }
        }
        System.out.printf("names: %d/%d (%.1f%%)%n", nameDone, nameTotal, 100.0 * nameDone / nameTotal);
        System.out.printf("lore lines: %d/%d (%.1f%%), broken placeholders: %d%n", loreDone, loreTotal, 100.0 * loreDone / loreTotal, broken);
        for (String s : samples) System.out.println("  " + s);
        if (broken > 0) failures++;
        /* неперекладене з NEU — робочий список для перекладу */
        Path out = Paths.get("build/neu_missing.tsv");
        Files.createDirectories(out.getParent());
        try (Writer w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            t.missing().write(w);
        }
        System.out.println("untranslated NEU lines -> " + out);
    }

    /* Мінімальний розбір JSON NEU (без бібліотек): лише потрібні поля-рядки. */
    static String jsonString(String json, String field) {
        int i = json.indexOf("\"" + field + "\"");
        if (i < 0) return null;
        i = json.indexOf('"', json.indexOf(':', i) + 1);
        return readString(json, i, new int[1]);
    }

    static List<String> jsonArray(String json, String field) {
        List<String> out = new ArrayList<>();
        int i = json.indexOf("\"" + field + "\"");
        if (i < 0) return out;
        i = json.indexOf('[', i);
        int[] end = new int[1];
        while (true) {
            int q = json.indexOf('"', i);
            int close = json.indexOf(']', i);
            if (q < 0 || (close >= 0 && close < q)) break;
            out.add(readString(json, q, end));
            i = end[0];
        }
        return out;
    }

    static String readString(String json, int quote, int[] endOut) {
        StringBuilder sb = new StringBuilder();
        int i = quote + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\') {
                char n = json.charAt(i + 1);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(json.substring(i + 2, i + 6), 16));
                        i += 4;
                    }
                    default -> sb.append(n);
                }
                i += 2;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
                i++;
            }
        }
        endOut[0] = i + 1;
        return sb.toString();
    }
}
