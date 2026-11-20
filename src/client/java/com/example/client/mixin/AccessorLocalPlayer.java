package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(LocalPlayer.class)
public interface AccessorLocalPlayer {
    @Accessor("positionReminder")
    void anubis$setPositionReminder(int var1);

    @Accessor("wasSprinting")
    boolean anubis$wasSprinting();

    @Accessor("lastSentInput")
    Input anubis$lastSentInput();
}
