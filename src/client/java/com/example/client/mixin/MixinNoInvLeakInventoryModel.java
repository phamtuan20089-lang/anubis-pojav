package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.impl.misc.NoInvLeakModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(InventoryScreen.class)
public abstract class MixinNoInvLeakInventoryModel {
    @WrapOperation(
        method = "renderEntityInInventoryFollowsMouse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
        )
    )
    private static EntityRenderState anubis$noInvLeakModel(LivingEntity entity, Operation<EntityRenderState> original) {
        if (!NoInvLeakModule.hidesModelOf(entity)) {
            return original.call(entity);
        }

        NoInvLeakModule.bareModel(true);

        try {
            return original.call(entity);
        } finally {
            NoInvLeakModule.bareModel(false);
        }
    }
}
