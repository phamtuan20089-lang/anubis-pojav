package com.example.client.mixin;

import net.anubis.client.event.impl.ChatSendEvent;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void anubis$onSendChat(String message, CallbackInfo ci) {
        if (ChatSendEvent.dispatch(message)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void anubis$afterEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        PacketAppliedEvent.dispatch(packet);
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void anubis$afterLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        PacketAppliedEvent.dispatch(packet);
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
    private void anubis$afterChunkData(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        PacketAppliedEvent.dispatch(packet);
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void anubis$afterBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        PacketAppliedEvent.dispatch(packet);
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void anubis$afterSectionUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        PacketAppliedEvent.dispatch(packet);
    }

    @Inject(method = "handleLightUpdatePacket", at = @At("TAIL"))
    private void anubis$afterLightUpdate(ClientboundLightUpdatePacket packet, CallbackInfo ci) {
        PacketAppliedEvent.dispatch(packet);
    }

    @Inject(method = "handleSetEntityMotion", at = @At("TAIL"))
    private void anubis$afterEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        PacketAppliedEvent.dispatch(packet);
    }
}
