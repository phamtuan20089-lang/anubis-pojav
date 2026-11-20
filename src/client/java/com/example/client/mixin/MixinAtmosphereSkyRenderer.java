package com.example.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.anubis.client.module.impl.render.AtmosphereModule;
import net.anubis.client.module.impl.render.SkyLyrics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(SkyRenderer.class)
public abstract class MixinAtmosphereSkyRenderer {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void anubis$readSky(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
        AtmosphereModule atmosphere = AtmosphereModule.get();
        if (atmosphere != null && state.skybox == Skybox.OVERWORLD) {
            atmosphere.extract(level, partialTick, state);
        }
    }

    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void anubis$dome(int color, CallbackInfo ci) {
        AtmosphereModule atmosphere = AtmosphereModule.get();
        if (atmosphere != null && atmosphere.skyDisc()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSkyDisc", at = @At("TAIL"))
    private void anubis$afterDome(int color, CallbackInfo ci) {
        AtmosphereModule atmosphere = AtmosphereModule.get();
        if (atmosphere != null) {
            atmosphere.afterSkyDisc();
        }
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void anubis$noSunriseFan(PoseStack pose, float sunAngle, int color, CallbackInfo ci) {
        AtmosphereModule atmosphere = AtmosphereModule.get();
        if (atmosphere != null && atmosphere.replacesSunrise()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void anubis$stars(float starBrightness, PoseStack pose, CallbackInfo ci) {
        AtmosphereModule atmosphere = AtmosphereModule.get();
        if (atmosphere != null && atmosphere.stars(pose)) {
            ci.cancel();
        }
    }

    @Inject(
        method = "renderSunMoonAndStars",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SkyRenderer;renderMoon(Lnet/minecraft/world/level/MoonPhase;FLcom/mojang/blaze3d/vertex/PoseStack;)V"
        )
    )
    private void anubis$beforeMoon(
        PoseStack pose, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci
    ) {
        AtmosphereModule atmosphere = AtmosphereModule.get();
        if (atmosphere != null) {
            atmosphere.beforeMoon(pose);
        }
    }

    @Inject(
        method = "renderSunMoonAndStars",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SkyRenderer;renderMoon(Lnet/minecraft/world/level/MoonPhase;FLcom/mojang/blaze3d/vertex/PoseStack;)V",
            shift = Shift.AFTER
        )
    )
    private void anubis$afterMoon(
        PoseStack pose, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci
    ) {
        AtmosphereModule atmosphere = AtmosphereModule.get();
        if (atmosphere != null) {
            atmosphere.afterMoon(pose);
        }
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
    private void anubis$afterCelestials(
        PoseStack pose, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci
    ) {
        AtmosphereModule atmosphere = AtmosphereModule.get();
        if (atmosphere != null) {
            atmosphere.afterCelestials();
        }

        SkyLyrics.render();
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void anubis$endSky(CallbackInfo ci) {
        AtmosphereModule atmosphere = AtmosphereModule.get();
        if (atmosphere != null && atmosphere.endSky()) {
            ci.cancel();
        }
    }
}
