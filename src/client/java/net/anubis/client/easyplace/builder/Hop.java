package net.anubis.client.easyplace.builder;

import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
final class Hop {
    private static final int TIME_LIMIT = 60;
    private static final double CENTERED = 0.15;
    private static final double STEP = 0.55;
    private static final double LINED_UP = 0.25;
    private static final double LEAP_AT = 0.3;
    private static final double LANDED = 0.55;
    private static final int SAFE_DROP = 3;
    private static final float TURN = 22.0F;
    private static final float WALK_OFF = 30.0F;
    private static final double JUMP_AT = 0.15;
    private final BlockPos spot;
    private final boolean through;
    private final boolean leap;
    private boolean sprint;
    private boolean careful;
    private boolean slow;
    private BlockPos from;
    private double floor = Double.NaN;
    private boolean centered;
    private int ticks;

    Hop(BlockPos spot) {
        this(spot, false, false);
    }

    Hop(BlockPos spot, boolean through) {
        this(spot, through, false);
    }

    Hop(BlockPos spot, boolean through, boolean leap) {
        this.spot = spot;
        this.through = through;
        this.leap = leap;
    }

    BlockPos spot() {
        return this.spot;
    }

    Hop sprinting() {
        this.sprint = true;
        return this;
    }

    Hop careful() {
        this.careful = true;
        this.sprint = false;
        return this;
    }

    Hop slow() {
        this.slow = true;
        this.sprint = false;
        return this;
    }

    static BlockPos spot(ClientLevel level, LocalPlayer player, Predicate<BlockPos> avoid) {
        if (!player.onGround()) {
            return null;
        }

        BlockPos feet = player.blockPosition();
        double from = Walker.floorOr(level, feet);
        BlockPos best = null;
        double bestRise = 0.45;

        for (BlockPos up : Walker.steps(level, feet)) {
            double rise = Walker.floor(level, up) - from;
            if (rise > bestRise && !avoid.test(up)) {
                bestRise = rise;
                best = up;
            }
        }

        return best;
    }

    Hop.Result tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return Hop.Result.FAILED;
        }

        if (this.from == null) {
            this.from = player.blockPosition();
        }

        if (Double.isNaN(this.floor) && mc.level != null) {
            this.floor = Walker.floorOr(mc.level, this.spot);
        }

        Vec3 pos = player.position();
        mc.options.keyShift.setDown(this.careful);
        double dx = this.spot.getX() + 0.5 - pos.x;
        double dz = this.spot.getZ() + 0.5 - pos.z;
        double near = this.through ? 0.35 : 0.12;
        boolean over = Math.abs(dx) < near && Math.abs(dz) < near;
        boolean settled = this.through || player.getDeltaMovement().horizontalDistanceSqr() < 0.0025;
        boolean landed = pos.y < this.floor + 0.55;
        if (over && settled && landed && player.onGround() && pos.y >= this.floor - 0.05) {
            if (!this.through) {
                this.release(mc);
            }

            return Hop.Result.DONE;
        } else {
            if (++this.ticks > 60) {
                this.release(mc);
                return Hop.Result.FAILED;
            }

            if (mc.level != null && player.onGround() && this.intoHole(mc.level, player, pos)) {
                mc.options.keyUp.setDown(false);
                mc.options.keySprint.setDown(false);
                mc.options.keyJump.setDown(false);
                mc.options.keyShift.setDown(true);
                return Hop.Result.RUNNING;
            }

            if (!this.centered) {
                double cx = this.from.getX() + 0.5 - pos.x;
                double cz = this.from.getZ() + 0.5 - pos.z;
                double aside = this.spot.getX() != this.from.getX() ? Math.abs(cz) : Math.abs(cx);
                boolean level = this.floor - pos.y <= 0.55 && aside < 0.25;
                if (!level && (!(Math.abs(cx) < 0.15) || !(Math.abs(cz) < 0.15)) && (player.onGround() || this.ticks <= 10)) {
                    float off = Aim.turn(mc, (float)Math.toDegrees(Math.atan2(-cx, cz)), player.getXRot(), 22.0F);
                    mc.options.keyUp.setDown(off < 30.0F);
                    mc.options.keyJump.setDown(false);
                    return Hop.Result.RUNNING;
                }

                this.centered = true;
            }

            float off = Aim.turn(mc, (float)Math.toDegrees(Math.atan2(-dx, dz)), player.getXRot(), 22.0F);
            boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 4.0E-4;
            mc.options.keySprint.setDown(this.sprint && !over && off < 10.0F);
            boolean push = (!over || !landed) && (this.through || Math.hypot(dx, dz) > 0.3 || !moving || !landed);
            if (this.slow) {
                boolean down = player.onGround() && pos.y < this.floor + 0.05 && Math.hypot(dx, dz) < 0.45;
                push = push && player.getDeltaMovement().horizontalDistance() < (down ? 0.03 : 0.08);
            }

            mc.options.keyUp.setDown(push && off < 30.0F);
            double ahead = (pos.x - (this.from.getX() + 0.5)) * Integer.signum(this.spot.getX() - this.from.getX())
                + (pos.z - (this.from.getZ() + 0.5)) * Integer.signum(this.spot.getZ() - this.from.getZ());
            boolean atStep = player.horizontalCollision || ahead > 0.15 || this.spot.getX() == this.from.getX() && this.spot.getZ() == this.from.getZ();
            mc.options
                .keyJump
                .setDown(player.onGround() && this.floor - pos.y > 0.55 && atStep || this.leap && player.onGround() && ahead > 0.3 && ahead < 1.0);
            return Hop.Result.RUNNING;
        }
    }

    private boolean intoHole(ClientLevel level, LocalPlayer player, Vec3 pos) {
        Vec3 motion = player.getDeltaMovement();
        double speed = motion.horizontalDistance();
        if (speed < 0.02) {
            return false;
        }

        double ahead = speed * 1.3 + 0.35;
        BlockPos cell = BlockPos.containing(pos.x + motion.x / speed * ahead, pos.y + 0.01, pos.z + motion.z / speed * ahead);
        BlockPos feet = player.blockPosition();
        return (cell.getX() != this.spot.getX() || cell.getZ() != this.spot.getZ()) && (cell.getX() != feet.getX() || cell.getZ() != feet.getZ())
            ? Walker.dropAt(level, cell) > 3
            : false;
    }

    void release(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keySprint.setDown(false);
        if (this.careful) {
            mc.options.keyShift.setDown(false);
        }
    }

    @Environment(EnvType.CLIENT)
    enum Result {
        RUNNING,
        DONE,
        FAILED;
    }
}
