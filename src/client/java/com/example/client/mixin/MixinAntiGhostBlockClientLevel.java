package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.impl.misc.AntiGhostBlockModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientLevel.class)
public abstract class MixinAntiGhostBlockClientLevel {
    @WrapOperation(
        method = "setServerVerifiedBlockState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/prediction/BlockStatePredictionHandler;updateKnownServerState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"
        )
    )
    private boolean anubis$serverAnswer(BlockStatePredictionHandler handler, BlockPos pos, BlockState state, Operation<Boolean> original) {
        boolean predicted = original.call(handler, pos, state);
        return predicted && !AntiGhostBlockModule.serverAnswered((ClientLevel)(Object)this, pos);
    }

    @Inject(method = "handleBlockChangedAck", at = @At("TAIL"))
    private void anubis$acknowledged(int sequence, CallbackInfo ci) {
        AntiGhostBlockModule.acknowledged((ClientLevel)(Object)this, sequence);
    }
}
