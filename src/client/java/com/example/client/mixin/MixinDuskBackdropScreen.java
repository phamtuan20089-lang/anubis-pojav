package com.example.client.mixin;

import net.anubis.client.gui.AnubisTitleScreen;
import net.anubis.client.gui.mainmenu.DuskBackdrop;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Screen.class)
public abstract class MixinDuskBackdropScreen {
    @Shadow
    public int width;
    @Shadow
    public int height;

    @Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
    private void anubis$duskBackdrop(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        if (AnubisTitleScreen.enabled()) {
            ci.cancel();
            DuskBackdrop.draw(graphics, this.width, this.height);
            graphics.nextStratum();
        }
    }
}
