package ua.kyrylo.skyblockua.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ua.kyrylo.skyblockua.Bridge;
import ua.kyrylo.skyblockua.core.Translator;

/*
 * Звичайний рядок тултіпа. Підміняємо текст лише там, де рядок малюється й вимірюється
 * (методи самого ClientTextTooltip). Поле text не змінюється, тож Iconographic та інші моди,
 * які читають його напряму, бачать англійський оригінал.
 *
 * Якщо рядок уже перекладено раніше (ItemStackMixin), у ньому кирилиця, і рушій його пропускає.
 */
@Mixin(ClientTextTooltip.class)
public class ClientTextTooltipMixin {

    @ModifyExpressionValue(
            method = {"getWidth", "extractText"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTextTooltip;text:Lnet/minecraft/util/FormattedCharSequence;"),
            require = 0
    )
    private FormattedCharSequence skyblockua$translateText(FormattedCharSequence original) {
        return Bridge.translateSequence(original, Translator.Kind.ANY);
    }
}
