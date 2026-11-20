package net.anubis.client.easyplace.module;

import java.util.HashMap;
import java.util.Map;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.easyplace.builder.SchematicBuilderModule;
import net.anubis.client.easyplace.compat.BaritoneCompat;
import net.anubis.client.easyplace.inventory.InventoryPickSourceMode;
import net.anubis.client.easyplace.placement.PlacementConfig;
import net.anubis.client.easyplace.placement.PlacementManager;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.FoglessLines;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class EasyPlaceModule extends ClientModule {
    private static final int FOV_COLOR = -1275016961;
    private static final double FOV_LENGTH = 6.0;
    private final BooleanSetting allBlocks = this.addSetting("all-blocks", new BooleanSetting("All Blocks", true));
    private final BooleanSetting rotationSpoof = this.addSetting("rotation-spoof", new BooleanSetting("Rotation Spoof", true));
    private final NumberSetting maxYawChange = this.addSetting("max-yaw-change", new NumberSetting("Max Yaw Change", 120.0F, 30.0F, 180.0F).step(1.0F));
    private final ModeSetting inventorySource = this.addSetting(
        "inventory-source", new ModeSetting("Inventory Source", InventoryPickSourceMode.HOTBAR_ONLY.title(), InventoryPickSourceMode.titles())
    );
    private final BooleanSetting schematicOnly = this.addSetting("schematic-only", new BooleanSetting("Schematic Only", true));
    private final NumberSetting tickDelay = this.addSetting("tick-delay", new NumberSetting("Tick Delay", 0.0F, 0.0F, 10.0F).step(1.0F));
    private final BooleanSetting fovFilter = this.addSetting("fov-filter", new BooleanSetting("FOV Filter", false));
    private final BooleanSetting drawFov = this.addSetting("draw-fov", new BooleanSetting("Draw FOV", false));
    private final NumberSetting fovDegrees = this.addSetting("fov-degrees", new NumberSetting("FOV Degrees", 60.0F, 10.0F, 180.0F).step(1.0F));
    private final BooleanSetting schematicAura = this.addSetting("schematic-aura", new BooleanSetting("Schematic Aura", true));
    private final NumberSetting auraRange = this.addSetting("aura-range", new NumberSetting("Aura Range", 4.0F, 1.0F, 8.0F).step(0.1F));
    private final BooleanSetting hopperForceFacing = this.addSetting("hopper-force-facing", new BooleanSetting("Hopper Force Facing", true));
    private final BooleanSetting baritoneWalk = this.addSetting("baritone-walk", new BooleanSetting("Baritone Walk", false));
    private final BooleanSetting chatFeedback = this.addSetting("chat-feedback", new BooleanSetting("Chat Feedback", true));
    private final BooleanSetting debug = this.addSetting("debug", new BooleanSetting("Debug Log", false));
    private final PlacementManager placementManager = new PlacementManager(Minecraft.getInstance(), this::feedback);
    private final Map<String, Long> lastFeedbackTick = new HashMap<>();
    private long moduleTick;

    public EasyPlaceModule() {
        super("easy-place", "Easy Place", "Places missing blocks from active Litematica placements and waits for server confirmation.", ModuleCategory.DONUT);
        this.drawFov.visibleWhen(this.fovFilter::get);
        this.fovDegrees.visibleWhen(this.fovFilter::get);
    }

    @Override
    protected void onEnable() {
        SchematicBuilderModule builder = ModuleRegistry.get(SchematicBuilderModule.class);
        if (builder != null && builder.isEnabled()) {
            builder.setEnabled(false);
        }

        this.placementManager.start();
        if (this.baritoneWalk.get()) {
            ModuleChat.send(this, BaritoneCompat.isAvailable() ? "Baritone detected, walking is on" : "Baritone not found, walking is off");
        }

        this.on(TickEvent.TYPE, Stage.PRE, event -> this.placementManager.tickPre(this.config()));
        this.on(TickEvent.TYPE, Stage.POST, event -> {
            this.moduleTick++;
            this.placementManager.tick(this.config());
        });
        this.on(Render3DEvent.TYPE, Stage.POST, this::renderFov);
    }

    @Override
    protected void onDisable() {
        this.placementManager.reset();
        this.lastFeedbackTick.clear();
        BaritoneCompat.cancel();
    }

    private void renderFov(Render3DEvent event) {
        if (this.fovFilter.get() && this.drawFov.get()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                Vec3 eye = player.getEyePosition(event.partialTick);
                double yaw = Math.toRadians(player.getViewYRot(event.partialTick));
                double pitch = Math.toRadians(player.getViewXRot(event.partialTick));
                double tan = Math.tan(Math.toRadians(this.fovDegrees.get() * 0.5));
                Vec3 look = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
                Vec3 right = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw)).scale(tan);
                Vec3 up = new Vec3(-Math.sin(yaw) * Math.sin(pitch), Math.cos(pitch), Math.cos(yaw) * Math.sin(pitch)).scale(tan);
                Vec3[] corners = new Vec3[]{
                    look.add(right).add(up), look.subtract(right).add(up), look.subtract(right).subtract(up), look.add(right).subtract(up)
                };
                FoglessLines lines = FoglessLines.forFrame(event.partialTick);

                for (int i = 0; i < corners.length; i++) {
                    Vec3 edge = eye.add(corners[i].scale(6.0));
                    Vec3 next = eye.add(corners[(i + 1) % corners.length].scale(6.0));
                    lines.line(eye, edge, -1275016961, 1.5F);
                    lines.line(edge, next, -1275016961, 1.5F);
                }
            }
        }
    }

    private PlacementConfig config() {
        return new PlacementConfig(
            this.allBlocks.get(),
            this.rotationSpoof.get(),
            this.maxYawChange.get(),
            InventoryPickSourceMode.of(this.inventorySource.get()),
            this.schematicOnly.get(),
            this.tickDelay.getInt(),
            this.schematicAura.get(),
            this.auraRange.get(),
            this.hopperForceFacing.get(),
            this.baritoneWalk.get(),
            this.debug.get(),
            this.fovFilter.get(),
            this.fovDegrees.get()
        );
    }

    private void feedback(String key, String message, int minimumIntervalTicks) {
        if (this.chatFeedback.get()) {
            long last = this.lastFeedbackTick.getOrDefault(key, -4611686018427387904L);
            if (this.moduleTick - last >= minimumIntervalTicks) {
                this.lastFeedbackTick.put(key, this.moduleTick);
                ModuleChat.send(this, message);
            }
        }
    }
}
