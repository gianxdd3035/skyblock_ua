package ua.kyrylo.skyblockua.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.kyrylo.skyblockua.Bridge;

/*
 * Підписи над головами. На Hypixel ім'я NPC показує невидима стійка для броні над ним
 * ("Jacob", нижче "CLICK"). Справжніх гравців не чіпаємо взагалі.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true, require = 0)
    private void skyblockua$translateNameTag(Entity entity, CallbackInfoReturnable<Component> cir) {
        if (entity instanceof Player) return;
        Component original = cir.getReturnValue();
        Component translated = Bridge.translateNameTag(original);
        if (translated != original) cir.setReturnValue(translated);
    }
}
