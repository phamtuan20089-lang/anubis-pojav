package com.example.client.mixin;

import net.anubis.client.module.impl.movement.JesusModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class MixinJesusEntity {
    @Inject(method = "onAboveBubbleColumn", at = @At("HEAD"))
    private void anubis$jesusAboveBubbleColumn(CallbackInfo ci) {
        this.anubis$jesusMarkBubbleColumn();
    }

    @Inject(method = "onInsideBubbleColumn", at = @At("HEAD"))
    private void anubis$jesusInsideBubbleColumn(CallbackInfo ci) {
        this.anubis$jesusMarkBubbleColumn();
    }

    @Unique
    private void anubis$jesusMarkBubbleColumn() {
        JesusModule jesus = JesusModule.active;
        if (jesus != null && (Object)this == Minecraft.getInstance().player) {
            jesus.markInBubbleColumn();
        }
    }
}
