package net.anubis.client.easyplace.placement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;

@Environment(EnvType.CLIENT)
public final class Clicks {
    private Clicks() {
    }

    public static boolean usesClick(Level level, BlockPos pos, boolean builder) {
        if (level.getBlockEntity(pos) instanceof MenuProvider) {
            return true;
        }

        if (!builder) {
            return false;
        }

        Block block = level.getBlockState(pos).getBlock();
        return level.getBlockEntity(pos) != null
            || block instanceof DoorBlock
            || block instanceof TrapDoorBlock
            || block instanceof FenceGateBlock
            || block instanceof ButtonBlock
            || block instanceof LeverBlock
            || block instanceof DiodeBlock
            || block instanceof NoteBlock
            || block instanceof CraftingTableBlock
            || block instanceof AnvilBlock
            || block instanceof BellBlock
            || block instanceof DaylightDetectorBlock;
    }
}
