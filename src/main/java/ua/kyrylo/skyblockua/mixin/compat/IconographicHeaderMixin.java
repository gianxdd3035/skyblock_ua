package ua.kyrylo.skyblockua.mixin.compat;

import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ua.kyrylo.skyblockua.Bridge;
import ua.kyrylo.skyblockua.core.Translator;

/*
 * Назва предмета в шапці тултіпа Iconographic.
 * Перекладаємо назву, яка передається на малювання, і ту саму назву, коли шапка рахує свою ширину
 * (через TextProperties.getWidth зі skyblock-api) — інакше довга українська назва вилазить за рамку.
 * Розбір англійського тексту самим Iconographic уже відбувся раніше, тож йому це не заважає.
 */
@Pseudo
@Mixin(targets = "me.owdding.iconographic.render.TooltipHeader")
public class IconographicHeaderMixin {

    @ModifyArg(
            method = {"extract"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"),
            index = 1,
            require = 0
    )
    private Component skyblockua$translateDrawn(Component original) {
        return Bridge.translateOrSame(original, Translator.Kind.NAME);
    }

    @ModifyArg(
            method = {"getWidth"},
            at = @At(value = "INVOKE", target = "Ltech/thatgravyboat/skyblockapi/utils/text/TextProperties;getWidth(Lnet/minecraft/network/chat/Component;)I"),
            index = 0,
            require = 0
    )
    private Component skyblockua$translateMeasured(Component original) {
        return Bridge.translateOrSame(original, Translator.Kind.NAME);
    }
}
