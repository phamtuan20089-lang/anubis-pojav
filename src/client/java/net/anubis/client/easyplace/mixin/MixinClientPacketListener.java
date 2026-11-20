package net.anubis.client.easyplace.mixin;

import net.anubis.client.easyplace.sync.BlockAcks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {
    @Inject(method = "handleBlockChangedAck", at = @At("TAIL"))
    private void easyplace$acked(ClientboundBlockChangedAckPacket packet, CallbackInfo ci) {
        BlockAcks.acked(packet.sequence());
    }
}
