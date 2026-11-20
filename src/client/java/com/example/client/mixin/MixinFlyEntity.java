package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.anubis.client.module.impl.movement.FlyModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class MixinFlyEntity {
    @ModifyReturnValue(method = "isInWater", at = @At("RETURN"))
    private boolean anubis$flyIsInWater(boolean original) {
        return original && !this.anubis$flyAffects();
    }

    @ModifyReturnValue(method = "isInLava", at = @At("RETURN"))
    private boolean anubis$flyIsInLava(boolean original) {
        return original && !this.anubis$flyAffects();
    }

    @ModifyExpressionValue(method = "updateSwimming", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isUnderWater()Z"))
    private boolean anubis$flyIsUnderWater(boolean original) {
        return original && !this.anubis$flyAffects();
    }

    @Unique
    private boolean anubis$flyAffects() {
        return FlyModule.active != null && (Object)this == Minecraft.getInstance().player;
    }
}
