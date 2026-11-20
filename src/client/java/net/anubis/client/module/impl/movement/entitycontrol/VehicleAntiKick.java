package net.anubis.client.module.impl.movement.entitycontrol;

import com.example.client.mixin.AccessorBoatFlyServerboundMoveVehiclePacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class VehicleAntiKick {
    private static final double Y_OFFSET = 0.0313;
    private static final long HOLD_NANOS = 50000000L;
    private int delayLeft;
    private double lastPacketY = Double.MAX_VALUE;
    private boolean sentPacket;
    private long dipNanos;
    private Entity trackedVehicle;

    public void start(int delay) {
        this.delayLeft = delay;
        this.forget();
        LocalPlayer player = Minecraft.getInstance().player;
        this.trackedVehicle = player != null ? player.getVehicle() : null;
    }

    public void stop() {
        this.trackedVehicle = null;
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Entity vehicle = player != null ? player.getVehicle() : null;
        this.trackVehicle(vehicle);
        if (this.sentPacket && System.nanoTime() - this.dipNanos >= 50000000L) {
            ClientPacketListener connection = mc.getConnection();
            if (vehicle != null && connection != null && this.lastPacketY != Double.MAX_VALUE) {
                ServerboundMoveVehiclePacket current = ServerboundMoveVehiclePacket.fromEntity(vehicle);
                Vec3 pos = current.position();
                connection.send(new ServerboundMoveVehiclePacket(new Vec3(pos.x, this.lastPacketY, pos.z), current.yRot(), current.xRot(), current.onGround()));
            }

            this.sentPacket = false;
        }

        if (this.delayLeft > 0) {
            this.delayLeft--;
        }
    }

    public boolean onPacketSend(ServerboundMoveVehiclePacket packet, int delay) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            return false;
        }

        LocalPlayer player = mc.player;
        Entity vehicle = player != null ? player.getVehicle() : null;
        if (vehicle == null) {
            return false;
        }

        this.trackVehicle(vehicle);
        Vec3 pos = packet.position();
        double currentY = pos.y;
        if (this.sentPacket && System.nanoTime() - this.dipNanos < 50000000L) {
            this.lastPacketY = currentY;
            return true;
        }

        if (this.delayLeft <= 0 && !this.sentPacket && this.shouldFlyDown(currentY) && EntityControlVehicleHook.isOnAir(vehicle) && !vehicle.isFlyingVehicle()) {
            ((AccessorBoatFlyServerboundMoveVehiclePacket)(Object)packet).anubis$setPosition(new Vec3(pos.x, this.lastPacketY - 0.0313, pos.z));
            this.sentPacket = true;
            this.dipNanos = System.nanoTime();
            this.delayLeft = delay;
        }

        this.lastPacketY = currentY;
        return false;
    }

    public void observe(ServerboundMoveVehiclePacket packet) {
        this.lastPacketY = packet.position().y;
    }

    public boolean isHolding() {
        return this.sentPacket && System.nanoTime() - this.dipNanos < 50000000L;
    }

    public void forget() {
        this.lastPacketY = Double.MAX_VALUE;
        this.sentPacket = false;
    }

    private boolean shouldFlyDown(double currentY) {
        return currentY >= this.lastPacketY ? true : this.lastPacketY - currentY < 0.0313;
    }

    private void trackVehicle(Entity vehicle) {
        if (vehicle != this.trackedVehicle) {
            this.trackedVehicle = vehicle;
            this.forget();
        }
    }
}
