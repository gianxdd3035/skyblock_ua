package ua.kyrylo.skyblockua.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.kyrylo.skyblockua.Bridge;

import java.util.List;

/*
 * Підміняємо готовий список рядків тултіпа (назва + опис) перекладеним.
 * Сам предмет (ItemStack) не змінюється, тому інші моди читають оригінальні дані.
 */
@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
    private void skyblockua$translateTooltip(Item.TooltipContext context, Player player, TooltipFlag flag,
                                             CallbackInfoReturnable<List<Component>> cir) {
        List<Component> original = cir.getReturnValue();
        List<Component> translated = Bridge.translateTooltip(original);
        if (translated != original) cir.setReturnValue(translated);
    }
}
