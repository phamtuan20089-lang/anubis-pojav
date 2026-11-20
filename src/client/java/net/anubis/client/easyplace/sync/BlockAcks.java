package net.anubis.client.easyplace.sync;

import net.anubis.client.easyplace.mixin.AccessorClientLevel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

@Environment(EnvType.CLIENT)
public final class BlockAcks {
    private static ClientLevel level;
    private static int lastAcked = -1;

    private BlockAcks() {
    }

    public static void acked(int sequence) {
        ClientLevel now = Minecraft.getInstance().level;
        if (now != level) {
            level = now;
            lastAcked = -1;
        }

        lastAcked = Math.max(lastAcked, sequence);
    }

    public static int lastSent(ClientLevel level) {
        return ((AccessorClientLevel)level).easyplace$predictions().currentSequence();
    }

    public static boolean answered(ClientLevel now, int sequence) {
        return now == level && lastAcked >= sequence;
    }
}
