package net.anubis.client.easyplace.builder;

import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
final class Bridger {
    private static final int EDGE_TICKS = 40;
    private static final int HOLD_TICKS = 20;
    private static final int ANSWER_TICKS = 12;
    private static final int TURN_TICKS = 30;
    private static final int STEP_TICKS = 40;
    private static final int TRIES = 2;
    private static final double LEANING = 0.18;
    private final BlockPos from;
    private final BlockPos spot;
    private final BlockPos leaf;
    private final Direction way;
    private final Set<Long> scaffold;
    private final Item material;
    private boolean chained;
    private int phase;
    private int phaseTicks;
    private int stillTicks;
    private int onAim;
    private int clickedAt = -1;
    private int tries;
    private Vec3 lastPos;

    Bridger(BlockPos from, BlockPos spot, Set<Long> scaffold, Item material) {
        this.from = from;
        this.spot = spot;
        this.leaf = spot.below();
        this.way = spot.getX() > from.getX()
            ? Direction.EAST
            : (spot.getX() < from.getX() ? Direction.WEST : (spot.getZ() > from.getZ() ? Direction.SOUTH : Direction.NORTH));
        this.scaffold = scaffold;
        this.material = material;
    }

    BlockPos spot() {
        return this.spot;
    }

    Bridger chained() {
        this.chained = true;
        return this;
    }

    Bridger.Result tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != null && mc.level != null && mc.gameMode != null) {
            mc.options.keyShift.setDown(true);
            this.phaseTicks++;
            boolean leafThere = this.material == null ? Scaffold.block(mc.level.getBlockState(this.leaf)) : !mc.level.getBlockState(this.leaf).canBeReplaced();
            switch (this.phase) {
                case 0:
                    if (leafThere) {
                        this.next(3);
                        return Bridger.Result.RUNNING;
                    }

                    if (this.material == null ? !Scaffold.has(player) : !Hands.has(player, stack -> stack.is(this.material))) {
                        return Bridger.Result.FAILED;
                    }

                    boolean held = this.material == null ? Scaffold.hold(mc, player) : Hands.hold(mc, player, stack -> stack.is(this.material));
                    if (!held) {
                        return this.phaseTicks > 20 ? Bridger.Result.FAILED : Bridger.Result.RUNNING;
                    }

                    this.lastPos = player.position();
                    this.next(1);
                    break;
                case 1: {
                    double out = this.past(player.position());
                    Vec3 pos = player.position();
                    this.stillTicks = pos.distanceToSqr(this.lastPos) < 1.0E-4 ? this.stillTicks + 1 : 0;
                    this.lastPos = pos;
                    if (out > 0.18 || out > 0.03 && this.stillTicks >= 3) {
                        mc.options.keyUp.setDown(false);
                        this.next(2);
                        return Bridger.Result.RUNNING;
                    }

                    if (this.phaseTicks > 40) {
                        return Bridger.Result.FAILED;
                    }

                    float off = Aim.turn(mc, this.way.toYRot(), 30.0F, 22.0F);
                    mc.options.keyUp.setDown(off < 20.0F);
                    break;
                }
                case 2:
                    if (leafThere) {
                        if (this.material == null) {
                            this.scaffold.add(this.leaf.asLong());
                        }

                        this.next(3);
                        return Bridger.Result.RUNNING;
                    }

                    if (!player.isShiftKeyDown()) {
                        if (this.phaseTicks > 10) {
                            return this.retry(mc);
                        }

                        return Bridger.Result.RUNNING;
                    }

                    if (this.clickedAt < 0) {
                        Aim.Shot shot = Aim.side(mc, this.from.below(), this.way);
                        if (shot == null) {
                            return this.retry(mc);
                        }

                        Aim.turn(mc, shot.yaw(), shot.pitch(), 22.0F);
                        if (!Aim.looking(player, shot.yaw(), shot.pitch())) {
                            this.onAim = 0;
                            if (this.phaseTicks > 30) {
                                return this.retry(mc);
                            }

                            return Bridger.Result.RUNNING;
                        }

                        if (++this.onAim < 2) {
                            return Bridger.Result.RUNNING;
                        }

                        ItemStack hand = player.getMainHandItem();
                        if (this.material == null ? !Scaffold.item(hand) : !hand.is(this.material)) {
                            if (++this.tries >= 2) {
                                return Bridger.Result.FAILED;
                            }

                            this.next(0);
                            return Bridger.Result.RUNNING;
                        }

                        if (mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, shot.hit()).consumesAction()) {
                            player.swing(InteractionHand.MAIN_HAND);
                        }

                        this.clickedAt = this.phaseTicks;
                    } else if (this.phaseTicks - this.clickedAt > 12) {
                        return this.retry(mc);
                    }
                    break;
                case 3: {
                    Vec3 pos = player.position();
                    double dx = this.spot.getX() + 0.5 - pos.x;
                    double dz = this.spot.getZ() + 0.5 - pos.z;
                    boolean there = player.onGround() && player.blockPosition().equals(this.spot);
                    if (there && (this.chained || Math.abs(dx) < 0.25 && Math.abs(dz) < 0.25)) {
                        this.release(mc);
                        return Bridger.Result.DONE;
                    }

                    if (this.phaseTicks > 40) {
                        return Bridger.Result.FAILED;
                    }

                    float off = Aim.turn(mc, (float)Math.toDegrees(Math.atan2(-dx, dz)), 30.0F, 22.0F);
                    mc.options.keyUp.setDown(off < 30.0F);
                    break;
                }
                default:
                    return Bridger.Result.FAILED;
            }

            return Bridger.Result.RUNNING;
        } else {
            return Bridger.Result.FAILED;
        }
    }

    private double past(Vec3 pos) {
        double edge = this.way.getAxisDirection() == AxisDirection.POSITIVE ? 1.0 : 0.0;

        return switch (this.way.getAxis()) {
            case X -> (pos.x - (this.from.getX() + edge)) * this.way.getStepX();
            case Z -> (pos.z - (this.from.getZ() + edge)) * this.way.getStepZ();
            default -> 0.0;
        };
    }

    private Bridger.Result retry(Minecraft mc) {
        if (++this.tries >= 2) {
            return Bridger.Result.FAILED;
        }

        this.lastPos = mc.player == null ? this.lastPos : mc.player.position();
        this.next(1);
        return Bridger.Result.RUNNING;
    }

    private void next(int value) {
        this.phase = value;
        this.phaseTicks = 0;
        this.stillTicks = 0;
        this.onAim = 0;
        this.clickedAt = -1;
    }

    void release(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keyShift.setDown(false);
    }

    @Environment(EnvType.CLIENT)
    enum Result {
        RUNNING,
        DONE,
        FAILED;
    }
}
