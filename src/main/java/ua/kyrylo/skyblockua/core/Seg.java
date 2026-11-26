package ua.kyrylo.skyblockua.core;

/*
 * Один шматок рядка з однаковим стилем (колір, жирність, клік/ховер).
 * S — тип стилю: у грі це net.minecraft.network.chat.Style, у тестах — простий рядок.
 */
public record Seg<S>(String text, S style) {
}
