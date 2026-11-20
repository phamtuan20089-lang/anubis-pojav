package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.anubis.client.module.impl.movement.BoatNoclipModule;
import net.anubis.client.module.impl.render.CustomFovModule;
import net.anubis.client.module.impl.render.NoRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ScreenEffectRenderer.class)
public abstract class MixinScreenEffectRenderer {
    @ModifyExpressionValue(method = "renderScreenEffect", at = @At(value = "NEW", target = "()Lcom/mojang/blaze3d/vertex/PoseStack;"))
    private PoseStack anubis$undoCustomFov(PoseStack poseStack) {
        float scale = CustomFovModule.screenEffectScale();
        if (scale != 1.0F) {
            poseStack.last().pose().scale(scale, scale, 1.0F);
        }

        return poseStack;
    }

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void anubis$cancelFireOverlay(PoseStack poseStack, MultiBufferSource bufferSource, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (NoRenderModule.hideFireOverlay()) {
            ci.cancel();
        }
    }

    @Inject(method = "getViewBlockingState", at = @At("HEAD"), cancellable = true)
    private static void anubis$noWallOverlayInNoclipBoat(Player player, CallbackInfoReturnable<BlockState> cir) {
        if (BoatNoclipModule.hidesWallOverlay(player)) {
            cir.setReturnValue(null);
        }
    }
}
