package ua.kyrylo.skyblockua;

import net.fabricmc.loader.api.FabricLoader;

/*
 * Сумісність із модом Iconographic.
 *
 * Iconographic будує власний тултіп з іконками, розбираючи АНГЛІЙСЬКИЙ текст рядків
 * ("Strength: +50", "Ability: ... RIGHT CLICK", зачарування тощо) регулярними виразами.
 * Якщо ми перекладемо рядки раніше, він їх не впізнає і показує звичайним текстом без іконок.
 *
 * Тому, коли Iconographic встановлено:
 *  - рядки тултіпа лишаються англійськими (ItemStackMixin нічого не робить);
 *  - переклад підставляється лише в момент малювання: у рядках Iconographic
 *    (mixin/compat/*) і в звичайних рядках тултіпа (ClientTextTooltipMixin), якщо тултіп малює гра.
 */
public final class IconographicCompat {

    public static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("iconographic");

    private IconographicCompat() {
    }
}
