package ua.kyrylo.skyblockua.mixin.compat;

import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.kyrylo.skyblockua.Bridge;

/*
 * Теги зачарувань Iconographic ("Sharpness" на кольоровому фоні з рівнем VII).
 * Iconographic сам будує тег: міряє ширину назви й підкладає під неї фон потрібної довжини.
 * Тому назву підміняємо на вході fromLevelAndName, до побудови тегу, і фон одразу рахується
 * під українську назву.
 */
@Pseudo
@Mixin(targets = "me.owdding.iconographic.features.ItemEnchants")
public class IconographicEnchantNameMixin {

    @ModifyVariable(method = "fromLevelAndName", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private String skyblockua$translateEnchantName(String name) {
        return Bridge.translateEnchantName(name);
    }

    /*
     * Опція Iconographic "дрібний текст зачарувань" використовує піксельний шрифт лише з латиницею,
     * і кирилиця там не малюється. Поки переклад увімкнений, віддаємо звичайний шрифт тегів
     * (він має запасний шрифт гри з кирилицею).
     */
    @Inject(method = "font", at = @At("HEAD"), cancellable = true, require = 0)
    private void skyblockua$regularFont(CallbackInfoReturnable<FontDescription.Resource> cir) {
        if (Bridge.translatesEnchantNames()) {
            cir.setReturnValue(new FontDescription.Resource(Identifier.fromNamespaceAndPath("iconographic", "enchantment_tags")));
        }
    }
}
