package net.anubis.client.easyplace.util;

import net.anubis.client.easyplace.schematic.TargetStatus;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

@Environment(EnvType.CLIENT)
public final class BlockStateUtil {
    private BlockStateUtil() {
    }

    public static boolean statesMatch(BlockState expected, BlockState actual) {
        return StateRules.placed(actual, expected) || StateRules.slabHalf(actual, expected);
    }

    public static TargetStatus classify(BlockState expected, BlockState actual) {
        if (StateRules.done(actual, expected)) {
            return TargetStatus.CORRECT;
        } else if (StateRules.placed(actual, expected)) {
            return TargetStatus.ADJUST;
        } else {
            return !actual.isAir() && !actual.canBeReplaced() && !StateRules.slabHalf(actual, expected) ? TargetStatus.WRONG : TargetStatus.MISSING;
        }
    }

    public static Item requiredItem(BlockState expected) {
        Item item = StateRules.plant(expected.getBlock()).asItem();
        return item == Items.AIR ? null : item;
    }

    public static boolean isPrimaryPlacementState(BlockState state) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
            ? false
            : !state.hasProperty(BlockStateProperties.BED_PART) || state.getValue(BlockStateProperties.BED_PART) != BedPart.HEAD;
    }

    public static boolean isSimpleFullBlock(BlockState state, Level level, BlockPos pos) {
        return state.isCollisionShapeFullBlock(level, pos) && state.getBlock().asItem() != Items.AIR;
    }
}
