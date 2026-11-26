package ua.kyrylo.skyblockua.mixin.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ua.kyrylo.skyblockua.Bridge;
import ua.kyrylo.skyblockua.core.Translator;

/*
 * Заголовок здібності: "Ability: ..." і клавіша ("RIGHT CLICK" -> "ПКМ").
 * Перекладаємо текст, який передається на малювання, і той самий текст під час вимірювання ширини.
 * Розбір англійського тексту самим Iconographic уже відбувся раніше, тож йому це не заважає.
 */
@Pseudo
@Mixin(targets = "me.owdding.iconographic.features.ItemAbility$AbilityHeaderLine")
public class IconographicAbilityMixin {

    @ModifyArg(
            method = {"extract"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"),
            index = 1,
            require = 0
    )
    private Component skyblockua$translateDrawn(Component original) {
        return Bridge.translateOrSame(original, Translator.Kind.ANY);
    }

    @ModifyArg(
            method = {"getWidth", "extract"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"),
            index = 0,
            require = 0
    )
    private FormattedText skyblockua$translateMeasured(FormattedText original) {
        return original instanceof Component c ? Bridge.translateOrSame(c, Translator.Kind.ANY) : original;
    }
}
