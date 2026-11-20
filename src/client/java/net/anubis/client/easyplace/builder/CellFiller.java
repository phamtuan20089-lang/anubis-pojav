package net.anubis.client.easyplace.builder;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

@Environment(EnvType.CLIENT)
final class CellFiller {
    private static final int ANSWER_TICKS = 12;
    private static final int HOLD_TICKS = 20;
    private static final int TURN_TICKS = 30;
    private static final int TRIES = 2;
    private final BlockPos cell;
    private final CellFiller.Kind kind;
    private Aim.Shot shot;
    private int phase;
    private int phaseTicks;
    private int onAim;
    private int tries;

    CellFiller(BlockPos cell, CellFiller.Kind kind) {
        this.cell = cell;
        this.kind = kind;
    }

    BlockPos cell() {
        return this.cell;
    }

    CellFiller.Kind kind() {
        return this.kind;
    }

    static boolean filled(Minecraft mc, BlockPos cell, CellFiller.Kind kind) {
        BlockState state = mc.level.getBlockState(cell);

        return switch (kind) {
            case SUPPORT_LEAF -> Scaffold.block(state);
            case WATER_SOURCE -> state.getFluidState().isSource() && state.getFluidState().is(FluidTags.WATER);
            case WATERLOG -> state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
            case LAVA_SOURCE -> state.getFluidState().isSource() && state.getFluidState().is(FluidTags.LAVA);
            case REFILL -> false;
        };
    }

    static boolean reachable(Minecraft mc, BlockPos cell, CellFiller.Kind kind) {
        return aim(mc, cell, kind) != null;
    }

    CellFiller.Result tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            return CellFiller.Result.FAILED;
        }

        if (filled(mc, this.cell, this.kind)) {
            return CellFiller.Result.DONE;
        }

        this.phaseTicks++;
        switch (this.phase) {
            case 0:
                if (this.kind == CellFiller.Kind.SUPPORT_LEAF ? !Scaffold.hold(mc, player) : !Hands.hold(mc, player, stack -> stack.is(this.kind.item))) {
                    if (this.phaseTicks > 20) {
                        return CellFiller.Result.FAILED;
                    }

                    return CellFiller.Result.RUNNING;
                }

                this.shot = aim(mc, this.cell, this.kind);
                if (this.shot == null) {
                    return CellFiller.Result.FAILED;
                }

                this.next(1);
                break;
            case 1:
                Aim.turn(mc, this.shot.yaw(), this.shot.pitch(), 22.0F);
                if (!Aim.looking(player, this.shot.yaw(), this.shot.pitch())) {
                    this.onAim = 0;
                    if (this.phaseTicks > 30) {
                        return CellFiller.Result.FAILED;
                    }

                    return CellFiller.Result.RUNNING;
                }

                if (++this.onAim < 2) {
                    return CellFiller.Result.RUNNING;
                }

                boolean used = this.kind == CellFiller.Kind.SUPPORT_LEAF
                    ? mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, this.shot.hit()).consumesAction()
                    : mc.gameMode.useItem(player, InteractionHand.MAIN_HAND).consumesAction();
                if (used) {
                    player.swing(InteractionHand.MAIN_HAND);
                }

                this.next(2);
                break;
            case 2:
                if (this.kind == CellFiller.Kind.REFILL && player.getMainHandItem().is(Items.WATER_BUCKET)) {
                    return CellFiller.Result.DONE;
                }

                if (this.phaseTicks > 12) {
                    if (++this.tries >= 2) {
                        return CellFiller.Result.FAILED;
                    }

                    this.next(0);
                }
                break;
            default:
                return CellFiller.Result.FAILED;
        }

        return CellFiller.Result.RUNNING;
    }

    private void next(int value) {
        this.phase = value;
        this.phaseTicks = 0;
        this.onAim = 0;
    }

    private static Aim.Shot aim(Minecraft mc, BlockPos cell, CellFiller.Kind kind) {
        return switch (kind) {
            case SUPPORT_LEAF -> Aim.against(mc, cell, null);
            case WATER_SOURCE -> Aim.against(mc, cell, Fluids.WATER);
            case WATERLOG -> Aim.at(mc, cell);
            case LAVA_SOURCE -> Aim.against(mc, cell, Fluids.LAVA);
            case REFILL -> Aim.water(mc, cell);
        };
    }

    static boolean endless(ClientLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).isSource()) {
            int sources = 0;

            for (Direction direction : Plane.HORIZONTAL) {
                FluidState next = level.getFluidState(pos.relative(direction));
                if (next.isSource() && next.is(FluidTags.WATER)) {
                    sources++;
                }
            }

            if (sources < 2) {
                return false;
            }

            BlockState below = level.getBlockState(pos.below());
            return below.isSolid() || below.getFluidState().isSource() && below.getFluidState().is(FluidTags.WATER);
        } else {
            return false;
        }
    }

    @Environment(EnvType.CLIENT)
    enum Kind {
        SUPPORT_LEAF(Items.OAK_LEAVES),
        WATER_SOURCE(Items.WATER_BUCKET),
        WATERLOG(Items.WATER_BUCKET),
        LAVA_SOURCE(Items.LAVA_BUCKET),
        REFILL(Items.BUCKET);

        final Item item;

        Kind(Item item) {
            this.item = item;
        }
    }

    @Environment(EnvType.CLIENT)
    enum Result {
        RUNNING,
        DONE,
        FAILED;
    }
}
