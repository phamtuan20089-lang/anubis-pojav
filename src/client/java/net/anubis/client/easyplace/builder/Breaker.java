package net.anubis.client.easyplace.builder;

import java.util.Random;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;

@Environment(EnvType.CLIENT)
final class Breaker {
    private static final int TIME_LIMIT = 100;
    private static final int LEAN_TICKS = 40;
    private static final float TURN = 22.0F;
    private static final Random HAND = new Random();
    private static final float WOBBLE_CHANCE = 0.3F;
    private static final double WOBBLE_DEGREES = 0.35;
    private final BlockPos pos;
    private final boolean mayLean;
    private final Direction side;
    private static final float VARIED_CHANCE = 0.2F;
    private final boolean varied = HAND.nextFloat() < 0.2F;
    private final double u = this.varied ? Mth.clamp(0.5 + HAND.nextGaussian() * 0.12, 0.22, 0.78) : 0.5;
    private final double v = this.varied ? Mth.clamp(0.5 + HAND.nextGaussian() * 0.12, 0.22, 0.78) : 0.5;
    private Direction face;
    private Direction leanWay;
    private boolean leaning;
    private float yaw;
    private float pitch;
    private int phase;
    private int ticks;
    private int react = -1;

    Breaker(BlockPos pos, boolean mayLean) {
        this(pos, mayLean, null);
    }

    Breaker(BlockPos pos, boolean mayLean, Direction side) {
        this.pos = pos;
        this.mayLean = mayLean;
        this.side = side;
    }

    private Aim.Shot shot(Minecraft mc) {
        return this.side == null ? Aim.at(mc, this.pos) : Aim.mine(mc, this.pos, this.side, this.u, this.v);
    }

    BlockPos pos() {
        return this.pos;
    }

    Breaker.Result tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            return this.finish(mc, Breaker.Result.FAILED);
        }

        if (mc.level.getBlockState(this.pos).isAir()) {
            if (this.phase >= 2) {
                mc.gameMode.stopDestroyBlock();
            }

            return this.finish(mc, Breaker.Result.DONE);
        } else {
            if (++this.ticks > 100) {
                mc.gameMode.stopDestroyBlock();
                return this.finish(mc, Breaker.Result.FAILED);
            }

            switch (this.phase) {
                case 0: {
                    Aim.Shot shot = this.shot(mc);
                    if (shot == null) {
                        if (this.mayLean && this.ticks <= 40) {
                            if (this.leanWay == null) {
                                this.leanWay = this.leanWay(mc, player);
                            }

                            if (this.leanWay == null) {
                                return this.finish(mc, Breaker.Result.FAILED);
                            }

                            this.leaning = true;
                            mc.options.keyShift.setDown(true);
                            float off = Aim.turn(mc, this.leanWay.toYRot(), 70.0F, 22.0F);
                            mc.options.keyUp.setDown(off < 20.0F);
                            return Breaker.Result.RUNNING;
                        }

                        return this.finish(mc, Breaker.Result.FAILED);
                    }

                    if (this.leaning) {
                        mc.options.keyUp.setDown(false);
                    }

                    this.face = shot.hit().getDirection();
                    this.yaw = shot.yaw();
                    this.pitch = shot.pitch();
                    this.phase = 1;
                    break;
                }
                case 1: {
                    if (this.react < 0) {
                        if (!(Aim.turn(mc, this.yaw, this.pitch, 22.0F) > 1.0F) && !(Math.abs(player.getXRot() - this.pitch) > 1.0F)) {
                            Aim.Shot shot = this.shot(mc);
                            if (shot == null) {
                                this.phase = 0;
                                return Breaker.Result.RUNNING;
                            }

                            this.face = shot.hit().getDirection();
                            Aim.look(mc, shot.yaw(), shot.pitch());
                            this.yaw = player.getYRot();
                            this.pitch = player.getXRot();
                            this.react = this.varied ? (int)Mth.clamp(Math.round(Math.exp(Math.log(2.6) + HAND.nextGaussian() * 0.35)), 2L, 6L) : 2;
                            return Breaker.Result.RUNNING;
                        }

                        return Breaker.Result.RUNNING;
                    }

                    if (--this.react > 0) {
                        return Breaker.Result.RUNNING;
                    }

                    BlockHitResult hit = Aim.trace(mc, player, player.getYRot(), player.getXRot(), player.blockInteractionRange());
                    if (hit == null || hit.getType() != Type.BLOCK || !hit.getBlockPos().equals(this.pos) || hit.getDirection() != this.face) {
                        this.react = -1;
                        return Breaker.Result.RUNNING;
                    }

                    mc.gameMode.startDestroyBlock(this.pos, this.face);
                    player.swing(InteractionHand.MAIN_HAND);
                    this.phase = 2;
                    break;
                }
                default:
                    if (this.side != null && this.varied && HAND.nextFloat() < 0.3F) {
                        this.wobble(mc, player);
                    }

                    Aim.look(mc, this.yaw, this.pitch);
                    mc.gameMode.continueDestroyBlock(this.pos, this.face);
                    player.swing(InteractionHand.MAIN_HAND);
            }

            return Breaker.Result.RUNNING;
        }
    }

    private void wobble(Minecraft mc, LocalPlayer player) {
        float nextYaw = this.yaw + (float)(HAND.nextGaussian() * 0.35);
        float nextPitch = this.pitch + (float)(HAND.nextGaussian() * 0.35 * 0.7);
        BlockHitResult hit = Aim.trace(mc, player, nextYaw, nextPitch, player.blockInteractionRange());
        if (hit != null && hit.getType() == Type.BLOCK && hit.getBlockPos().equals(this.pos) && hit.getDirection() == this.face) {
            Aim.look(mc, nextYaw, nextPitch);
            this.yaw = player.getYRot();
            this.pitch = player.getXRot();
        }
    }

    private Direction leanWay(Minecraft mc, LocalPlayer player) {
        BlockPos feet = player.blockPosition();
        Direction best = null;

        for (Direction way : Plane.HORIZONTAL) {
            if (mc.level.getBlockState(this.pos.relative(way)).canBeReplaced()) {
                BlockPos beside = feet.relative(way);
                if (mc.level.getBlockState(beside).getCollisionShape(mc.level, beside).isEmpty()
                    && mc.level.getBlockState(beside.above()).getCollisionShape(mc.level, beside.above()).isEmpty()
                    && (
                        best == null
                            || Math.abs(Mth.wrapDegrees(way.toYRot() - player.getYRot())) < Math.abs(Mth.wrapDegrees(best.toYRot() - player.getYRot()))
                    )) {
                    best = way;
                }
            }
        }

        return best;
    }

    void release(Minecraft mc) {
        this.finish(mc, Breaker.Result.FAILED);
    }

    private Breaker.Result finish(Minecraft mc, Breaker.Result result) {
        if (this.leaning) {
            mc.options.keyUp.setDown(false);
            mc.options.keyShift.setDown(false);
            this.leaning = false;
        }

        return result;
    }

    @Environment(EnvType.CLIENT)
    enum Result {
        RUNNING,
        DONE,
        FAILED;
    }
}
