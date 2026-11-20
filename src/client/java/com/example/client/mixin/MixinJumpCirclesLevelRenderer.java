package com.example.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.anubis.client.module.impl.render.JumpCirclesModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LevelRenderer.class)
public abstract class MixinJumpCirclesLevelRenderer {
    @Inject(method = "renderBlockDestroyAnimation", at = @At("HEAD"))
    private void anubis$jumpCircles(PoseStack poseStack, BufferSource crumblingBuffers, LevelRenderState levelRenderState, CallbackInfo ci) {
        if (JumpCirclesModule.active != null) {
            JumpCirclesModule.renderLevel(levelRenderState.cameraRenderState);
        }
    }
}
