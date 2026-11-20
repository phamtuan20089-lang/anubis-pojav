package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.impl.misc.NameProtectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Environment(EnvType.CLIENT)
@Mixin(PlayerTabOverlay.class)
public abstract class MixinNameProtectPlayerTabOverlay {
    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
    private Component anubis$nameProtectTabName(Component original) {
        return NameProtectModule.protect(original);
    }

    @ModifyArg(
        method = "render",
        at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"),
        index = 0
    )
    private FormattedText anubis$nameProtectTabHeader(FormattedText header) {
        return NameProtectModule.protectText(header);
    }

    @ModifyArg(
        method = "render",
        at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"),
        index = 0
    )
    private FormattedText anubis$nameProtectTabFooter(FormattedText footer) {
        return NameProtectModule.protectText(footer);
    }

    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerInfo;getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;")
    )
    private PlayerSkin anubis$nameProtectTabHead(PlayerInfo info, Operation<PlayerSkin> original) {
        PlayerSkin real = original.call(info);
        return NameProtectModule.hidesName(info.getProfile().name()) ? NameProtectModule.hiddenHead(info.getProfile().id(), real) : real;
    }
}
