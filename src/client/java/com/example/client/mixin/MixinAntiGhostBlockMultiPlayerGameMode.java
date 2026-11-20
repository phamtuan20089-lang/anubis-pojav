package com.example.client.mixin;

import net.anubis.client.module.impl.misc.AntiGhostBlockModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinAntiGhostBlockMultiPlayerGameMode {
    @Shadow
    private int destroyDelay;

    @Shadow
    public abstract void stopDestroyBlock();

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void anubis$holdBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (AntiGhostBlockModule.holdBreak(pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void anubis$waitBeforeStart(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (AntiGhostBlockModule.waiting(pos)) {
            this.stopDestroyBlock();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true, order = 1100)
    private void anubis$waitBeforeContinue(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (AntiGhostBlockModule.waiting(pos)) {
            if (this.destroyDelay > 0) {
                this.destroyDelay--;
            }

            this.stopDestroyBlock();
            cir.setReturnValue(false);
        }
    }
}
