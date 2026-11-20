package net.anubis.client.easyplace.placement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public record PlacementTarget(
    BlockPos targetPos,
    BlockPos neighborPos,
    Direction placeSide,
    Direction clickedFace,
    int hotbarSlot,
    float yaw,
    float pitch,
    int score,
    Vec3 hitCursor,
    boolean sneak
) {
}
