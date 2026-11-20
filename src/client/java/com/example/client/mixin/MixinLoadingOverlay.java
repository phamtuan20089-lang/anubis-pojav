package com.example.client.mixin;

import net.anubis.client.gui.LoadingScreens;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Environment(EnvType.CLIENT)
@Mixin(LoadingOverlay.class)
public abstract class MixinLoadingOverlay {
    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"), index = 4)
    private int anubis$backgroundFill(int argb) {
        return LoadingScreens.backgroundColor(argb);
    }

    @ModifyArg(
        method = "render",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearColorTexture(Lcom/mojang/blaze3d/textures/GpuTexture;I)V"),
        index = 1
    )
    private int anubis$backgroundClear(int argb) {
        return LoadingScreens.backgroundColor(argb);
    }
}
