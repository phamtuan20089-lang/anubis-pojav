package net.anubis.client.easyplace.util;

import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

@Environment(EnvType.CLIENT)
public final class StateRules {
    private static final Set<Property<?>> PLACED = Set.of(
        BlockStateProperties.FACING,
        BlockStateProperties.HORIZONTAL_FACING,
        BlockStateProperties.FACING_HOPPER,
        BlockStateProperties.VERTICAL_DIRECTION,
        BlockStateProperties.AXIS,
        BlockStateProperties.HORIZONTAL_AXIS,
        BlockStateProperties.HALF,
        BlockStateProperties.DOUBLE_BLOCK_HALF,
        BlockStateProperties.SLAB_TYPE,
        BlockStateProperties.ROTATION_16,
        BlockStateProperties.ATTACH_FACE,
        BlockStateProperties.ORIENTATION,
        BlockStateProperties.DOOR_HINGE,
        BlockStateProperties.CHEST_TYPE,
        BlockStateProperties.BED_PART,
        BlockStateProperties.BELL_ATTACHMENT,
        BlockStateProperties.HANGING
    );
    private static final Set<Property<?>> CLICKED = Set.of(
        BlockStateProperties.DELAY, BlockStateProperties.MODE_COMPARATOR, BlockStateProperties.NOTE, BlockStateProperties.OPEN, BlockStateProperties.INVERTED
    );
    private static final Map<Block, Block> TIPS = Map.of(
        Blocks.KELP_PLANT,
        Blocks.KELP,
        Blocks.TWISTING_VINES_PLANT,
        Blocks.TWISTING_VINES,
        Blocks.WEEPING_VINES_PLANT,
        Blocks.WEEPING_VINES,
        Blocks.CAVE_VINES_PLANT,
        Blocks.CAVE_VINES
    );

    private StateRules() {
    }

    public static Block plant(Block block) {
        return TIPS.getOrDefault(block, block);
    }

    public static boolean placed(BlockState actual, BlockState expected) {
        if (plant(actual.getBlock()) != plant(expected.getBlock())) {
            return false;
        }

        for (Property<?> property : expected.getProperties()) {
            if (actual.hasProperty(property)
                && (PLACED.contains(property) || faceProperty(expected, property))
                && !actual.getValue(property).equals(expected.getValue(property))) {
                return false;
            }
        }

        return true;
    }

    public static boolean done(BlockState actual, BlockState expected) {
        if (!placed(actual, expected)) {
            return false;
        }

        for (Property<?> property : expected.getProperties()) {
            if (clickable(expected, property) && actual.hasProperty(property) && !actual.getValue(property).equals(expected.getValue(property))) {
                return false;
            }
        }

        return true;
    }

    public static boolean needsClicks(BlockState actual, BlockState expected) {
        return placed(actual, expected) && !done(actual, expected);
    }

    public static boolean wouldFit(BlockState wouldBe, BlockState desired, boolean skipChestType) {
        if (plant(wouldBe.getBlock()) != plant(desired.getBlock())) {
            return false;
        }

        for (Property<?> property : desired.getProperties()) {
            if (wouldBe.hasProperty(property)
                && (!skipChestType || property != BlockStateProperties.CHEST_TYPE)
                && (property != BlockStateProperties.SLAB_TYPE || desired.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE)) {
                if (faceProperty(desired, property)) {
                    if ((Boolean)wouldBe.getValue(property) && !(Boolean)desired.getValue(property)) {
                        return false;
                    }
                } else if (PLACED.contains(property) && !wouldBe.getValue(property).equals(desired.getValue(property))) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean slabHalf(BlockState actual, BlockState expected) {
        return expected.getBlock() instanceof SlabBlock
            && actual.getBlock() == expected.getBlock()
            && expected.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE
            && actual.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE;
    }

    public static int itemsNeeded(BlockState actual, BlockState expected) {
        if (expected.getBlock() instanceof SlabBlock && expected.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
            return slabHalf(actual, expected) ? 1 : 2;
        } else {
            return 1;
        }
    }

    public static boolean clickable(BlockState expected, Property<?> property) {
        if (!CLICKED.contains(property)) {
            return false;
        }

        if (property != BlockStateProperties.OPEN) {
            return true;
        }

        Block block = expected.getBlock();
        return block != Blocks.IRON_DOOR && block != Blocks.IRON_TRAPDOOR
            ? block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
            : false;
    }

    public static Direction lookFor(BlockState expected) {
        Block block = expected.getBlock();
        if (block instanceof CrafterBlock) {
            return expected.getValue(BlockStateProperties.ORIENTATION).front().getOpposite();
        } else if (block instanceof ObserverBlock) {
            return expected.getValue(BlockStateProperties.FACING);
        } else {
            return !(block instanceof PistonBaseBlock) && !(block instanceof DispenserBlock) && !(block instanceof BarrelBlock)
                ? null
                : expected.getValue(BlockStateProperties.FACING).getOpposite();
        }
    }

    public static Direction flatLookFor(BlockState expected) {
        if (!expected.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return null;
        } else {
            Block block = expected.getBlock();
            Direction facing = expected.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (block instanceof ChestBlock || block instanceof AbstractFurnaceBlock || block instanceof DiodeBlock) {
                return facing.getOpposite();
            } else {
                return !(block instanceof StairBlock) && !(block instanceof DoorBlock) && !(block instanceof FenceGateBlock) ? null : facing;
            }
        }
    }

    private static boolean faceProperty(BlockState state, Property<?> property) {
        Block block = state.getBlock();
        return (block instanceof MultifaceBlock || block instanceof VineBlock)
            && property instanceof BooleanProperty
            && PipeBlock.PROPERTY_BY_DIRECTION.containsValue(property);
    }
}
