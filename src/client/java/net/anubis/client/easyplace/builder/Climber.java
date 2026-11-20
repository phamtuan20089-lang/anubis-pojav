package net.anubis.client.easyplace.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

@Environment(EnvType.CLIENT)
final class Climber {
    static final int HEIGHT = 2;
    private static final int STEP_LIMIT = 40;
    private static final int TRIES = 3;
    private static final int DIG_DEPTH = 2;
    private static final double CENTERED = 0.12;
    private final Set<Long> scaffold;
    private final int x;
    private final int z;
    private final int baseY;
    private final int height;
    private int placed;
    private int phase;
    private int phaseTicks;
    private int tries;
    private final List<BlockPos> toBreak = new ArrayList<>();
    private Breaker breaker;

    Climber(BlockPos column, Set<Long> scaffold) {
        this(column, scaffold, 2);
    }

    Climber(BlockPos column, Set<Long> scaffold, int height) {
        this.x = column.getX();
        this.z = column.getZ();
        this.baseY = column.getY();
        this.scaffold = scaffold;
        this.height = height;
    }

    static BlockPos column(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != null && mc.level != null && player.onGround()) {
            int feetY = (int)Math.floor(player.getY() + 0.01);
            if (Math.abs(player.getY() - feetY) > 0.01) {
                return null;
            }

            BlockPos center = BlockPos.containing(player.getX(), feetY, player.getZ());
            if (groundUnder(mc, center)) {
                return center;
            }

            double r = player.getBbWidth() / 2.0 - 0.01;

            for (double[] corner : new double[][]{{-r, -r}, {r, -r}, {-r, r}, {r, r}}) {
                BlockPos column = BlockPos.containing(player.getX() + corner[0], feetY, player.getZ() + corner[1]);
                if (groundUnder(mc, column)) {
                    return column;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    static boolean hasRoom(Minecraft mc, BlockPos column) {
        for (int dy = 0; dy <= 4; dy++) {
            BlockPos cell = column.above(dy);
            if (!mc.level.getBlockState(cell).getCollisionShape(mc.level, cell).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private static boolean groundUnder(Minecraft mc, BlockPos column) {
        BlockPos below = column.below();
        VoxelShape shape = mc.level.getBlockState(below).getCollisionShape(mc.level, below);
        return !shape.isEmpty() && shape.max(Axis.Y) >= 0.99;
    }

    BlockPos column() {
        return new BlockPos(this.x, this.baseY, this.z);
    }

    static int leavesCount(LocalPlayer player) {
        return Scaffold.count(player);
    }

    Climber.Result tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            return Climber.Result.FAILED;
        }

        if (this.breaker != null || this.placed >= this.height) {
            return this.breakBelow(mc);
        }

        if (!Scaffold.has(player)) {
            return Climber.Result.FAILED;
        }

        if (!Scaffold.hold(mc, player)) {
            return Climber.Result.RUNNING;
        }

        this.phaseTicks++;
        BlockPos leaf = new BlockPos(this.x, this.baseY + this.placed, this.z);
        switch (this.phase) {
            case 0:
                double dx = this.x + 0.5 - player.getX();
                double dz = this.z + 0.5 - player.getZ();
                boolean centered = Math.abs(dx) < 0.12 && Math.abs(dz) < 0.12;
                if (centered) {
                    mc.options.keyUp.setDown(false);
                    Aim.turn(mc, player.getYRot(), 90.0F, 22.0F);
                    Vec3 motion = player.getDeltaMovement();
                    if (this.phaseTicks >= 2 && player.getXRot() > 88.0F && motion.x * motion.x + motion.z * motion.z < 4.0E-4) {
                        this.next(1);
                    }
                } else {
                    float off = Aim.turn(mc, (float)Math.toDegrees(Math.atan2(-dx, dz)), player.getXRot(), 22.0F);
                    mc.options.keyUp.setDown(off < 30.0F);
                    if (this.phaseTicks > 40) {
                        mc.options.keyUp.setDown(false);
                        return Climber.Result.FAILED;
                    }
                }
                break;
            case 1:
                Aim.look(mc, player.getYRot(), 90.0F);
                if (player.onGround()) {
                    mc.options.keyShift.setDown(Aim.clickable(mc, leaf.below()));
                    mc.options.keyJump.setDown(true);
                    this.next(2);
                } else if (this.phaseTicks > 40) {
                    return Climber.Result.FAILED;
                }
                break;
            case 2:
                Aim.look(mc, player.getYRot(), 90.0F);
                if (!player.onGround()) {
                    mc.options.keyJump.setDown(false);
                }

                if (player.getY() >= leaf.getY() + 1.02) {
                    mc.options.keyJump.setDown(false);
                    BlockHitResult hit = new BlockHitResult(new Vec3(this.x + 0.5, leaf.getY(), this.z + 0.5), Direction.UP, leaf.below(), false);
                    if (mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction()) {
                        player.swing(InteractionHand.MAIN_HAND);
                        this.scaffold.add(leaf.asLong());
                    }

                    mc.options.keyShift.setDown(false);
                    this.next(3);
                } else if (player.onGround() && this.phaseTicks > 4 || this.phaseTicks > 40) {
                    mc.options.keyJump.setDown(false);
                    if (++this.tries > 3) {
                        return Climber.Result.FAILED;
                    }

                    this.next(0);
                }
                break;
            case 3:
                Aim.look(mc, player.getYRot(), 90.0F);
                boolean there = Scaffold.block(mc.level.getBlockState(leaf));
                boolean over = Math.abs(this.x + 0.5 - player.getX()) < 0.5 && Math.abs(this.z + 0.5 - player.getZ()) < 0.5;
                if (there && over && player.onGround() && player.getY() >= leaf.getY() + 0.99) {
                    this.placed++;
                    SchematicBuilderModule.trace("climber base " + this.baseY + " placed " + this.placed + " at y " + player.getY());
                    this.tries = 0;
                    if (this.placed >= this.height) {
                        for (int y = this.baseY + this.height - 2; y >= this.baseY - 2; y--) {
                            BlockPos below = new BlockPos(this.x, y, this.z);
                            if (this.scaffold.contains(below.asLong()) && Scaffold.block(mc.level.getBlockState(below))) {
                                this.toBreak.add(below);
                            } else if (!mc.level.getBlockState(below).isAir()) {
                                break;
                            }
                        }

                        return this.breakBelow(mc);
                    }

                    this.next(0);
                } else if (this.phaseTicks > 40 || player.onGround() && !over && this.phaseTicks > 4) {
                    SchematicBuilderModule.trace(
                        "climber retry base " + this.baseY + " placed " + this.placed + " there " + there + " over " + over + " y " + player.getY()
                    );
                    if (!there) {
                        this.scaffold.remove(leaf.asLong());
                    }

                    if (!over) {
                        return Climber.Result.FAILED;
                    }

                    if (++this.tries > 3) {
                        return Climber.Result.FAILED;
                    }

                    this.next(1);
                }
                break;
            default:
                return Climber.Result.FAILED;
        }

        return Climber.Result.RUNNING;
    }

    void release(Minecraft mc) {
        mc.options.keyShift.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyUp.setDown(false);
    }

    private Climber.Result breakBelow(Minecraft mc) {
        if (this.breaker == null) {
            if (this.toBreak.isEmpty()) {
                return Climber.Result.DONE;
            }

            this.breaker = new Breaker(this.toBreak.removeFirst(), true);
        }

        Breaker.Result result = this.breaker.tick(mc);
        if (result == Breaker.Result.RUNNING) {
            return Climber.Result.RUNNING;
        }

        if (result == Breaker.Result.DONE) {
            this.scaffold.remove(this.breaker.pos().asLong());
        }

        this.breaker = null;
        return this.toBreak.isEmpty() ? Climber.Result.DONE : Climber.Result.RUNNING;
    }

    private void next(int value) {
        this.phase = value;
        this.phaseTicks = 0;
    }

    @Environment(EnvType.CLIENT)
    enum Result {
        RUNNING,
        DONE,
        FAILED;
    }
}
