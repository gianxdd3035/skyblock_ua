package ua.kyrylo.skyblockua.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ua.kyrylo.skyblockua.Bridge;
import ua.kyrylo.skyblockua.Config;
import ua.kyrylo.skyblockua.core.Translator;

/*
 * Заголовок меню перекладається лише в момент малювання.
 * Поле title не чіпаємо: SkyHanni, Skyblocker та інші моди впізнають меню саме за англійською назвою.
 * extractLabels малює і назву меню, і підпис "Inventory" — обидва проходять через цей метод.
 */
@Mixin(AbstractContainerScreen.class)
public class ContainerScreenMixin {

    @ModifyArg(
            method = "extractLabels",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"
            ),
            index = 1
    )
    private Component skyblockua$translateTitle(Component original) {
        if (!Config.get().translateTitles || !Bridge.active()) return original;
        Component t = Bridge.translate(original, Translator.Kind.TITLE);
        return t == null ? original : t;
    }
}
