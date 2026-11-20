package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.impl.render.HandAuraModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(ItemInHandRenderer.class)
public abstract class MixinHandAuraItemInHandRenderer {
    @WrapOperation(
        method = "renderHandsWithItems",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures()V")
    )
    private void anubis$handAura(FeatureRenderDispatcher features, Operation<Void> original) {
        if (HandAuraModule.active != null && HandAuraModule.beginCapture()) {
            boolean drawn = false;

            try {
                original.call(features);
                Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
                drawn = true;
            } finally {
                HandAuraModule.endCapture(drawn);
            }
        } else {
            original.call(features);
        }
    }
}
