package com.example.client.mixin;

import net.anubis.client.module.impl.misc.NameProtectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Environment(EnvType.CLIENT)
@Mixin(GuiMessage.class)
public abstract class MixinNameProtectGuiMessage {
    @ModifyArg(
        method = "splitLines",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"
        ),
        index = 0
    )
    private FormattedText anubis$nameProtectChatLine(FormattedText text) {
        return NameProtectModule.protectChatLine(text);
    }
}
