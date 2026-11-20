package com.example.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.module.impl.render.NoRenderModule;
import net.anubis.client.util.ShaderOverlay;
import net.anubis.client.util.TracerEngine;
import net.anubis.client.util.WorldQuads;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.fog.FogRenderer.FogMode;
import net.minecraft.gizmos.Gizmos.TemporaryCollection;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @ModifyArg(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/debug/DebugRenderer;emitGizmos(Lnet/minecraft/client/renderer/culling/Frustum;DDDF)V"
        ),
        index = 0
    )
    private Frustum anubis$onRenderLevel(
        Frustum frustum, @Local(argsOnly = true, ordinal = 0) Matrix4f viewRotation, @Local(argsOnly = true, ordinal = 1) Matrix4f projection
    ) {
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        TracerEngine.beginFrame(viewRotation, projection);
        WorldQuads.beginFrame(Minecraft.getInstance().gameRenderer.getMainCamera().position());

        try (TemporaryCollection capture = ShaderOverlay.capture()) {
            Render3DEvent.dispatch(partialTick, frustum);
        }

        return frustum;
    }

    @ModifyArg(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;addLateDebugPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/CameraRenderState;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Matrix4f;)V"
        ),
        index = 2
    )
    private GpuBufferSlice anubis$noFluidFogOnTop(GpuBufferSlice fog, @Local(argsOnly = true) Camera camera) {
        return anubis$onTopFog(fog, camera);
    }

    @Unique
    private static GpuBufferSlice anubis$onTopFog(GpuBufferSlice fog, Camera camera) {
        return camera.getFluidInCamera() == FogType.NONE
            ? fog
            : ((AccessorGameRenderer)Minecraft.getInstance().gameRenderer).anubis$getFogRenderer().getBuffer(FogMode.NONE);
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void anubis$overlayAfterShaderPack(
        CallbackInfo ci,
        @Local(argsOnly = true) GpuBufferSlice fog,
        @Local(argsOnly = true) Camera camera,
        @Local(argsOnly = true, ordinal = 0) Matrix4f viewRotation,
        @Local(argsOnly = true, ordinal = 1) Matrix4f projection
    ) {
        WorldQuads.render(viewRotation, projection);
        if (ShaderOverlay.pending()) {
            ShaderOverlay.render(camera, viewRotation, projection, fog, anubis$onTopFog(fog, camera));
        }
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void anubis$worldFogForHand(CallbackInfo ci, @Local(argsOnly = true) GpuBufferSlice fog, @Local(argsOnly = true) Camera camera) {
        if (camera.getFluidInCamera() != FogType.NONE) {
            RenderSystem.setShaderFog(fog);
        }
    }

    @Inject(method = "doesMobEffectBlockSky", at = @At("HEAD"), cancellable = true)
    private void anubis$keepSkyDuringBlindnessDarkness(Camera camera, CallbackInfoReturnable<Boolean> cir) {
        if (NoRenderModule.hideBlindnessDarkness()) {
            cir.setReturnValue(false);
        }
    }
}
