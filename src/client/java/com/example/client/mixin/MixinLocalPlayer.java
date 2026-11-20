package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.anubis.client.event.impl.PlayerMoveEvent;
import net.anubis.client.module.impl.misc.FreecamModule;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {
    @WrapMethod(method = "move")
    private void anubis$onMove(MoverType type, Vec3 movement, Operation<Void> original) {
        if (!PlayerMoveEvent.TYPE.hasListeners()) {
            original.call(type, movement);
        } else {
            PlayerMoveEvent.move(movement, changed -> original.call(type, changed));
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void anubis$walkServerRotation(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer)(Object)this;
        if (Minecraft.getInstance().player == self) {
            ServerRotation.applyForMovement(self);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void anubis$finishServerRotation(CallbackInfo ci) {
        ServerRotation.finishTick((LocalPlayer)(Object)this);
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = Shift.AFTER))
    private void anubis$freecamAfterInput(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer)(Object)this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == self) {
            FreecamModule.drive(mc);
        }
    }

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void anubis$freecamBeforeSend(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer)(Object)this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == self) {
            FreecamModule.drive(mc);
        }
    }

    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void anubis$swapOutServerRotation(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer)(Object)this;
        if (Minecraft.getInstance().player == self) {
            ServerRotation.afterSend(self);
        }
    }
}
