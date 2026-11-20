package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.anubis.client.event.EventBus;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.gui.AnubisTitleScreen;
import net.anubis.client.module.impl.misc.AutoEatModule;
import net.anubis.client.util.PerformanceBudget;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.HitResult.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Shadow
    public ClientLevel level;

    @Shadow
    public abstract void setScreen(Screen var1);

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void anubis$replaceTitleScreen(Screen screen, CallbackInfo ci) {
        if (!(screen instanceof AnubisTitleScreen)) {
            boolean title = screen instanceof TitleScreen || screen == null && this.level == null;
            if (title && AnubisTitleScreen.enabled()) {
                ci.cancel();
                this.setScreen(new AnubisTitleScreen());
            }
        }
    }

    @Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
    private void anubis$windowTitle(CallbackInfoReturnable<String> cir) {
        if (AnubisTitleScreen.enabled()) {
            cir.setReturnValue("Minecraft " + SharedConstants.getCurrentVersion().name() + " - Anubis Client");
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void anubis$onTickStart(CallbackInfo ci) {
        PerformanceBudget.beginTick();
        EventBus.INSTANCE.drainAsync();
        ServerRotation.startTick();
        TickEvent.dispatch();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void anubis$onTickEnd(CallbackInfo ci) {
        TickEvent.dispatchPost();
    }

    @ModifyExpressionValue(
        method = "startUseItem",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/HitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;")
    )
    private Type anubis$autoEatIgnoreCrosshair(Type type) {
        return AutoEatModule.isEating() ? Type.MISS : type;
    }
}
