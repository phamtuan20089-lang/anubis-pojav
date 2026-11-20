package net.anubis.client.easyplace.builder;

import net.anubis.client.easyplace.util.PlacementUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
final class Shuffle {
    private static final double CLOSE = 0.3;
    private static final double SLOW = 0.0025;
    private static final double KEY = 0.12;
    private static final double SLIDE = 1.2;
    private static final int TIME_LIMIT = 60;
    private static final double QUICK_SLOW = 0.0144;
    private final Vec3 spot;
    private final Vec3 look;
    private final double slow;
    private int ticks;

    Shuffle(Vec3 spot, Vec3 look, boolean quick) {
        this.spot = spot;
        this.look = look;
        this.slow = quick ? 0.0144 : 0.0025;
    }

    boolean quick() {
        return this.slow == 0.0144;
    }

    Vec3 spot() {
        return this.spot;
    }

    Shuffle.Result tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return Shuffle.Result.FAILED;
        } else {
            float[] rot = PlacementUtil.calcRotation(player.getEyePosition(), this.look);
            Aim.turn(mc, rot[0], rot[1], 22.0F);
            double dx = this.spot.x - player.getX();
            double dz = this.spot.z - player.getZ();
            Vec3 motion = player.getDeltaMovement();
            double left = Math.hypot(dx, dz);
            if (left < 0.3 && motion.horizontalDistanceSqr() < this.slow) {
                this.release(mc);
                return Shuffle.Result.DONE;
            } else if (++this.ticks > 60) {
                this.release(mc);
                return left < 0.45 ? Shuffle.Result.DONE : Shuffle.Result.FAILED;
            } else {
                double yaw = Math.toRadians(player.getYRot());
                double sin = Math.sin(yaw);
                double cos = Math.cos(yaw);
                double ahead = -sin * dx + cos * dz - 1.2 * (-sin * motion.x + cos * motion.z);
                double toLeft = cos * dx + sin * dz - 1.2 * (cos * motion.x + sin * motion.z);
                mc.options.keyUp.setDown(ahead > 0.12);
                mc.options.keyDown.setDown(ahead < -0.12);
                mc.options.keyLeft.setDown(toLeft > 0.12);
                mc.options.keyRight.setDown(toLeft < -0.12);
                return Shuffle.Result.RUNNING;
            }
        }
    }

    void release(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
    }

    @Environment(EnvType.CLIENT)
    enum Result {
        RUNNING,
        DONE,
        FAILED;
    }
}
