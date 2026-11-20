package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(LevelRenderer.class)
public interface AccessorPinkWaterLevelRenderer {
    @Accessor("viewArea")
    ViewArea anubis$pinkWaterViewArea();
}
