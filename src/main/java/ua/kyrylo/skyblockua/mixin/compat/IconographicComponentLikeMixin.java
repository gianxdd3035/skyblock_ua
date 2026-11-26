package ua.kyrylo.skyblockua.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import ua.kyrylo.skyblockua.Bridge;
import ua.kyrylo.skyblockua.core.Translator;

/*
 * Звичайний текстовий рядок у тултіпі Iconographic (клас ComponentLike).
 * Iconographic шукає в ньому англійський текст через getStripped()/getComponent() —
 * їх не чіпаємо. Перекладаємо лише текст, який малюється (extract) і за яким рахується ширина
 * (лямбда width_delegate), щоб рамка тултіпа підлаштувалася під довший український текст.
 *
 * @Pseudo: якщо Iconographic не встановлено, цей mixin просто пропускається.
 */
@Pseudo
@Mixin(targets = "me.owdding.iconographic.ComponentLike")
public class IconographicComponentLikeMixin {

    @ModifyExpressionValue(
            method = "extract",
            at = @At(value = "FIELD", target = "Lme/owdding/iconographic/ComponentLike;charSequence:Lnet/minecraft/util/FormattedCharSequence;"),
            require = 0
    )
    private FormattedCharSequence skyblockua$translateDrawn(FormattedCharSequence original) {
        return Bridge.translateSequence(original, Translator.Kind.ANY);
    }

    @ModifyExpressionValue(
            method = "width_delegate$lambda$0",
            at = @At(value = "FIELD", target = "Lme/owdding/iconographic/ComponentLike;charSequence:Lnet/minecraft/util/FormattedCharSequence;"),
            require = 0
    )
    private static FormattedCharSequence skyblockua$translateMeasured(FormattedCharSequence original) {
        return Bridge.translateSequence(original, Translator.Kind.ANY);
    }
}
