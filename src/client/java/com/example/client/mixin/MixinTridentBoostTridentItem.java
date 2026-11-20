package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.impl.movement.TridentBoostModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(TridentItem.class)
public abstract class MixinTridentBoostTridentItem {
    @WrapOperation(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;push(DDD)V"), require = 0)
    private void anubis$tridentBoostPush(Player player, double x, double y, double z, Operation<Void> original) {
        double multiplier = TridentBoostModule.riptideMultiplier();
        original.call(player, x * multiplier, y * multiplier, z * multiplier);
    }

    @ModifyExpressionValue(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"), require = 0)
    private boolean anubis$tridentBoostUseOutOfWater(boolean original) {
        return original || TridentBoostModule.allowOutOfWater();
    }

    @ModifyExpressionValue(
        method = "releaseUsing",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"),
        require = 0
    )
    private boolean anubis$tridentBoostReleaseOutOfWater(boolean original) {
        return original || TridentBoostModule.allowOutOfWater();
    }
}
