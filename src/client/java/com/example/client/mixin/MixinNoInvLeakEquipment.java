package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.anubis.client.module.impl.misc.NoInvLeakModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class MixinNoInvLeakEquipment {
    @ModifyReturnValue(method = "getItemBySlot", at = @At("RETURN"))
    private ItemStack anubis$noInvLeakBareModel(ItemStack stack) {
        return NoInvLeakModule.modelItem((LivingEntity)(Object)this, stack);
    }
}
