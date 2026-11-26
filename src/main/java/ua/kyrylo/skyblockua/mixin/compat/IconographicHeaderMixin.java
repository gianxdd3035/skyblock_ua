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
 * Перекладаємо текст, який передається на малювання (ширину шапки Iconographic не рахує за назвою).
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
}
