package com.example.client.mixin;

import net.anubis.client.module.impl.render.AtmosphereModule;
import net.anubis.client.module.impl.render.PeepWorld;
import net.anubis.client.module.impl.render.PinkWater;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(FogRenderer.class)
public abstract class MixinAtmosphereFogRenderer {
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void anubis$fogColour(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkness, CallbackInfoReturnable<Vector4f> cir) {
        FogType fluid = camera.getFluidInCamera();
        if (fluid == FogType.WATER) {
            PinkWater.underwater(cir.getReturnValue());
        }

        if (fluid == FogType.NONE && !(darkness > 0.0F)) {
            if (!(camera.entity() instanceof LivingEntity living && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS)))) {
                AtmosphereModule atmosphere = AtmosphereModule.get();
                if (atmosphere != null) {
                    atmosphere.fog(camera, level, cir.getReturnValue());
                }

                PeepWorld.fog(cir.getReturnValue());
            }
        }
    }
}
