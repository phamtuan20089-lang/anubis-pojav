package com.example.client.mixin;

import net.anubis.client.module.impl.combat.AutoCityModule;
import net.anubis.client.module.impl.combat.bot.CrystalBotModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public abstract class MixinAutoCityMinecraft {
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void anubis$autoCityContinueAttack(boolean down, CallbackInfo ci) {
        if (AutoCityModule.mining() || CrystalBotModule.digging()) {
            ci.cancel();
        }
    }
}
