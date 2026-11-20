package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.anubis.client.module.impl.misc.NameProtectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(Gui.class)
public abstract class MixinNameProtectGui {
    @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true)
    private Component anubis$nameProtectTitle(Component title) {
        return NameProtectModule.protect(title);
    }

    @ModifyVariable(method = "setSubtitle", at = @At("HEAD"), argsOnly = true)
    private Component anubis$nameProtectSubtitle(Component subtitle) {
        return NameProtectModule.protect(subtitle);
    }

    @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true)
    private Component anubis$nameProtectActionBar(Component message) {
        return NameProtectModule.protect(message);
    }

    @ModifyExpressionValue(
        method = "displayScoreboardSidebar",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/Objective;getDisplayName()Lnet/minecraft/network/chat/Component;")
    )
    private Component anubis$nameProtectSidebarTitle(Component original) {
        return NameProtectModule.protect(original);
    }

    @ModifyExpressionValue(
        method = "displayScoreboardSidebar",
        at = @At(
            value = "FIELD",
            opcode = 180,
            ordinal = 0,
            target = "Lnet/minecraft/client/gui/Gui$1DisplayEntry;name:Lnet/minecraft/network/chat/Component;"
        )
    )
    private Component anubis$nameProtectSidebarLineWidth(Component original) {
        return NameProtectModule.protect(original);
    }

    @ModifyExpressionValue(
        method = "displayScoreboardSidebar",
        at = @At(
            value = "FIELD",
            opcode = 180,
            ordinal = 1,
            target = "Lnet/minecraft/client/gui/Gui$1DisplayEntry;name:Lnet/minecraft/network/chat/Component;"
        )
    )
    private Component anubis$nameProtectSidebarLineDraw(Component original) {
        return NameProtectModule.protect(original);
    }
}
