package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(LoadingOverlay.class)
public interface AccessorLoadingOverlay {
    @Accessor("currentProgress")
    float anubis$getProgress();

    @Accessor("fadeOutStart")
    long anubis$getFadeOutStart();

    @Accessor("fadeInStart")
    long anubis$getFadeInStart();

    @Accessor("fadeIn")
    boolean anubis$isFadeIn();
}
