package net.anubis.client.easyplace.util;

import java.util.Locale;
import net.anubis.client.easyplace.EasyPlace;
import net.anubis.client.easyplace.placement.PlacementState;
import net.anubis.client.easyplace.placement.PlacementTarget;
import net.anubis.client.easyplace.sync.ServerAckManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

@Environment(EnvType.CLIENT)
public final class EasyPlaceDebug {
    private EasyPlaceDebug() {
    }

    public static void log(String event, PlacementState state, PlacementTarget placement, float[] rotation, ServerAckManager.Result ack, int retry) {
        LocalPlayer player = Minecraft.getInstance().player;
        float playerYaw = player != null ? player.getYRot() : Float.NaN;
        float playerPitch = player != null ? player.getXRot() : Float.NaN;
        EasyPlace.LOG
            .info(
                "EasyPlace Debug | event={} | Face={} | PlaceSide={} | Yaw={} | Pitch={} | PlayerYaw={} | PlayerPitch={} | State={} | ACK={} | Retry={}",
                event,
                placement == null ? "-" : placement.clickedFace(),
                placement == null ? "-" : placement.placeSide(),
                rotation == null ? "-" : String.format(Locale.ROOT, "%.2f", rotation[0]),
                rotation == null ? "-" : String.format(Locale.ROOT, "%.2f", rotation[1]),
                String.format(Locale.ROOT, "%.2f", playerYaw),
                String.format(Locale.ROOT, "%.2f", playerPitch),
                state,
                ack,
                retry
            );
    }
}
