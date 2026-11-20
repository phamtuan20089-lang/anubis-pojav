package net.anubis.client.easyplace.sync;

import net.anubis.client.easyplace.schematic.SchematicBlockState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public record PlacementConfirmation(BlockPos pos, SchematicBlockState expected, long sentTick, long deadlineTick, int retry, int sequence, BlockState predicted) {
}
