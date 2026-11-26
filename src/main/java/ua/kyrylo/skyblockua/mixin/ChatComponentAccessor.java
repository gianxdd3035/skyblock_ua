package ua.kyrylo.skyblockua.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/*
 * Доступ до приватних частин чату: списку всіх повідомлень
 * і методу, що перебудовує видимі (перенесені по ширині) рядки.
 */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {

    @Accessor("allMessages")
    List<GuiMessage> skyblockua$allMessages();

    @Invoker("refreshTrimmedMessages")
    void skyblockua$refreshTrimmedMessages();
}
