package net.anubis.client.easyplace.hopper;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

@Environment(EnvType.CLIENT)
public final class HopperPlacementHandler {
    public boolean isHopper(BlockState state) {
        return state.getBlock() instanceof HopperBlock;
    }

    public boolean faceCanProduceExpectedFacing(BlockState expected, Direction clickedFace) {
        if (this.isHopper(expected) && expected.hasProperty(BlockStateProperties.FACING_HOPPER)) {
            Direction wanted = expected.getValue(BlockStateProperties.FACING_HOPPER);
            Direction produced = clickedFace.getOpposite();
            if (produced.getAxis() == Axis.Y) {
                produced = Direction.DOWN;
            }

            return produced == wanted;
        } else {
            return true;
        }
    }

    public int facingScore(BlockState expected, Direction clickedFace) {
        return this.faceCanProduceExpectedFacing(expected, clickedFace) ? 1000 : -1000;
    }
}
