package net.anubis.client.module.impl.misc;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.gui.ChallengeToast;
import net.anubis.client.gui.GlintHeadRenderer;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.client.PerformanceModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.Setting;
import net.anubis.client.module.setting.StringSetting;
import net.anubis.client.util.ChunkAreas;
import net.anubis.client.util.FoglessLines;
import net.anubis.client.util.playerbypass.ChunkLightSnapshot;
import net.anubis.client.util.playerbypass.PlayerBypassLightTracker;
import net.anubis.client.util.playerbypass.VisibleChunkCache;
import net.anubis.client.util.playerbypass2.SimulationSignals;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class PlayerBypassModule extends ClientModule {
    private static final int RENDER_DISTANCE_CHUNKS = 16;
    private static final double MARKER_Y = 64.0;
    private static final float LINE_WIDTH = 2.0F;
    private static final int MARK_FILL_ALPHA = 34;
    private static final int BASE_COLOR = -2516480;
    private static final int SUSPECT_COLOR = -5766;
    private static final int ACTIVITY_COLOR = -11205;
    private static final int MEMORY_SECONDS = 45;
    private static final float FALLOFF = 0.3F;
    private static final float MIN_SOURCE = 0.5F;
    private static final int MAX_FOUND = 256;
    private static final int ALERT_SPACING = 4;
    private static final int PIN_SPACING = 1;
    private static final float FROZEN_NEIGHBOUR = 0.5F;
    private static final int REPORT_SOURCES = 4;
    private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
    private final NumberSetting opacity = this.addSetting("opacity", new NumberSetting("Opacity", 140.0F, 10.0F, 255.0F).step(1.0F));
    private final BooleanSetting tracers = this.addSetting("tracers", new BooleanSetting("Tracers", false));
    private final NumberSetting sensitivity = this.addSetting("sens", new NumberSetting("Sensitivity", 5.0F, 1.0F, 10.0F).step(1.0F));
    private final ChunkAreas chunkAreas = new ChunkAreas();
    private final VisibleChunkCache visibleChunks = new VisibleChunkCache(16);
    private final BooleanSetting sound = this.addSetting("sound", new BooleanSetting("Sound", true));
    private final BooleanSetting showCoordinates = this.addSetting("show-coordinates", new BooleanSetting("Show Coordinates", true));
    private final BooleanSetting disconnect = this.addSetting("disconnect-on-spotted", new BooleanSetting("Auto Disconnect", false));
    private final BooleanSetting autoRtp = this.addSetting("auto-rtp", new BooleanSetting("Auto RTP", false));
    private final StringSetting rtpCommand = this.addSetting("rtp-command", new StringSetting("RTP Command", "rtp").hint("Without the slash, e.g. rtp"));
    private final NumberSetting rtpScanTime = this.addSetting("rtp-scan-time", new NumberSetting("Scan Time (s)", 5.0F, 2.0F, 30.0F).step(0.5F));
    private final NumberSetting rtpDelay = this.addSetting("rtp-delay", new NumberSetting("RTP Delay (s)", 6.0F, 3.0F, 120.0F).step(0.5F));
    private final NumberSetting rtpTimeout = this.addSetting("rtp-max-timeout", new NumberSetting("RTP Timeout (s)", 10.0F, 3.0F, 60.0F).step(1.0F));
    private final NumberSetting rtpMax = this.addSetting("rtp-max", new NumberSetting("Max RTPs (0 = unlimited)", 100.0F, 0.0F, 500.0F).step(1.0F));
    private final BooleanSetting rtpStopOnSuspect = this.addSetting("rtp-stop-on-suspect", new BooleanSetting("Stop On Suspect", false));
    private final StringSetting rtpMenuItem = this.addSetting(
        "rtp-menu-item", new StringSetting("Menu Item", "").hint("Clicked if the command opens a menu, e.g. overworld")
    );
    private final PlayerBypassAutoRtp autoRtpRunner = new PlayerBypassAutoRtp(
        this,
        this.autoRtp,
        this.rtpCommand,
        this.rtpScanTime,
        this.rtpDelay,
        this.rtpTimeout,
        this.rtpMax,
        this.rtpStopOnSuspect,
        this.rtpMenuItem,
        this.sound,
        this.showCoordinates
    );
    private final LongOpenHashSet peaks = new LongOpenHashSet();
    private final Map<ResourceKey<Level>, LongLinkedOpenHashSet> foundByDimension = new HashMap<>();
    private LongLinkedOpenHashSet found = new LongLinkedOpenHashSet();
    private int session = -1;
    private final LongOpenHashSet shown = new LongOpenHashSet();
    private final Long2FloatOpenHashMap field = new Long2FloatOpenHashMap();
    private final Long2FloatOpenHashMap raw = new Long2FloatOpenHashMap();
    private int ticks;

    public PlayerBypassModule() {
        super(
            "player-bypass",
            "Player Bypass",
            "Finds hidden players from light data and from chunks that keep ticking outside your simulation distance.",
            ModuleCategory.BASE_RAID
        );
        BooleanSetting notifications = this.usesNotifications();
        this.sound.visibleWhen(() -> notifications.get() || this.autoRtp.get());
        this.showCoordinates.visibleWhen(() -> notifications.get() || this.disconnect.get() || this.autoRtp.get());
        this.autoRtp.sessionOnly();
        Setting[] rtpSettings = new Setting[]{
            this.rtpCommand, this.rtpScanTime, this.rtpDelay, this.rtpTimeout, this.rtpMax, this.rtpStopOnSuspect, this.rtpMenuItem
        };

        for (Setting setting : rtpSettings) {
            setting.visibleWhen(this.autoRtp::get);
        }

        this.autoRtp.onChange(() -> {
            if (this.isEnabled()) {
                if (this.autoRtp.get()) {
                    this.autoRtpRunner.start();
                } else {
                    this.autoRtpRunner.stopByUser();
                }
            }
        });
    }

    @Override
    protected void onEnable() {
        this.visibleChunks.invalidate();
        PlayerBypassLightTracker.setSpottedListener(this::onChunkSpotted);
        if (this.autoRtp.get()) {
            this.autoRtpRunner.start();
        }

        this.foundByDimension.clear();
        this.found = new LongLinkedOpenHashSet();
        this.session = SimulationSignals.session();
        SimulationSignals.clear();
        this.configure();
        this.on(TickEvent.TYPE, Stage.POST, this::onTick);
    }

    @Override
    protected void onDisable() {
        this.visibleChunks.invalidate();
        PlayerBypassLightTracker.setSpottedListener(null);
        this.autoRtpRunner.stopByUser();
        this.autoRtpRunner.forgetCooldown();
        this.autoRtp.set(false);
        SimulationSignals.configure(false, 0, 0, true, 30);
        this.peaks.clear();
        this.foundByDimension.clear();
        this.found = new LongLinkedOpenHashSet();
        this.shown.clear();
        this.field.clear();
        this.raw.clear();
    }

    private void onChunkSpotted(int chunkX, int chunkZ) {
        this.spotted((chunkX << 4) + 8, (chunkZ << 4) + 8, new ItemStack(Items.PLAYER_HEAD));
    }

    private void spotted(int x, int z, ItemStack icon) {
        Minecraft mc = Minecraft.getInstance();
        boolean showPos = this.showCoordinates.get();
        if (!this.autoRtpRunner.baseSpotted(x, z, this.disconnect.get()) && this.notificationsOn()) {
            ChallengeToast.addOrUpdate(icon, "Player Bypass", showPos ? Component.literal("at " + x + ", " + z) : Component.empty(), this.sound.get());
        }

        if (this.disconnect.get()) {
            ClientPacketListener connection = mc.getConnection();
            if (connection != null && connection.getConnection().isConnected()) {
                ModuleChat.send(this, ModuleChat.body().text("Auto Disconnect ").warn("triggered"));
                AutoReconnectModule.holdOff();
                connection.getConnection().disconnect(Component.literal("[Player Bypass] Player detected" + (showPos ? " at " + x + ", " + z : "")));
            }
        }
    }

    private void configure() {
        SimulationSignals.configure(true, 0, 0, true, 45);
    }

    private static boolean netherOrEnd(Level level) {
        ResourceKey<Level> dimension = level.dimension();
        return dimension.equals(Level.NETHER)
            || dimension.equals(Level.END)
            || level.dimensionTypeRegistration().is(BuiltinDimensionTypes.NETHER)
            || level.dimensionTypeRegistration().is(BuiltinDimensionTypes.END)
            || !level.dimensionType().hasSkyLight();
    }

    private float threshold() {
        return 8.0F * (float)Math.pow(2.0, (5.0 - this.sensitivity.get()) / 2.5);
    }

    private void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            if (this.session != SimulationSignals.session()) {
                this.session = SimulationSignals.session();
                this.foundByDimension.clear();
                this.peaks.clear();
                this.shown.clear();
            }

            this.found = this.foundByDimension.computeIfAbsent(mc.level.dimension(), key -> new LongLinkedOpenHashSet());
            this.configure();
            SimulationSignals.tick();
            if (netherOrEnd(mc.level)) {
                SimulationSignals.dropPins();
                this.shown.clear();
            } else {
                this.drainPins();
                if (++this.ticks % 5 == 0) {
                    this.collect(mc.player);
                    this.alert();
                    this.refreshShown(mc.player);
                }
            }
        }
    }

    private void collect(LocalPlayer self) {
        this.peaks.clear();
        this.field.clear();
        this.raw.clear();
        long now = SimulationSignals.now();
        int sim = SimulationSignals.simulation();
        int pcx = self.blockPosition().getX() >> 4;
        int pcz = self.blockPosition().getZ() >> 4;
        int start = SimulationSignals.ringStart();
        int view = SimulationSignals.view();
        int side = 2 * sim + 1;
        float[] weights = new float[side * side];
        float span = sim * 1.4143F + 1.0F;

        for (int dx = -sim; dx <= sim; dx++) {
            for (int dz = -sim; dz <= sim; dz++) {
                weights[(dx + sim) * side + dz + sim] = 1.0F - 0.3F * (float)Math.sqrt(dx * dx + dz * dz) / span;
            }
        }

        for (Entry<SimulationSignals.ChunkSignal> entry : SimulationSignals.chunks().long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            int d = SimulationSignals.distance(cx, cz, pcx, pcz);
            if (d >= start && d <= view) {
                float score = entry.getValue().score(now);
                if (!(score < 0.5F)) {
                    for (int dx = -sim; dx <= sim; dx++) {
                        for (int dz = -sim; dz <= sim; dz++) {
                            long area = ChunkPos.asLong(cx + dx, cz + dz);
                            this.field.addTo(area, score * weights[(dx + sim) * side + dz + sim]);
                            this.raw.addTo(area, score);
                        }
                    }
                }
            }
        }

        for (long frozen : SimulationSignals.frozen()) {
            this.field.remove(frozen);
            this.raw.remove(frozen);
            int fx = ChunkPos.getX(frozen);
            int fz = ChunkPos.getZ(frozen);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long near = ChunkPos.asLong(fx + dx, fz + dz);
                    if (this.field.containsKey(near)) {
                        this.field.put(near, this.field.get(near) * 0.5F);
                        this.raw.put(near, this.raw.get(near) * 0.5F);
                    }
                }
            }
        }

        float threshold = this.threshold();

        for (it.unimi.dsi.fastutil.longs.Long2FloatMap.Entry entry : this.field.long2FloatEntrySet()) {
            long key = entry.getLongKey();
            if (!(this.raw.get(key) < threshold) && this.isPeak(key, entry.getFloatValue(), sim)) {
                this.peaks.add(key);
            }
        }
    }

    private boolean isPeak(long key, float value, int reach) {
        int cx = ChunkPos.getX(key);
        int cz = ChunkPos.getZ(key);

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (dx != 0 || dz != 0) {
                    long other = ChunkPos.asLong(cx + dx, cz + dz);
                    float near = this.field.get(other);
                    if (near > value || near == value && other < key) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private void drainPins() {
        SimulationSignals.Pin pin;
        while ((pin = SimulationSignals.pollPin()) != null) {
            BlockPos pos = pin.pos();
            long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            LOGGER.info("[Player Bypass] {}", pin.detail());
            if (!this.near(key, 1)) {
                this.remember(key);
                ModuleChat.send(this, pin.detail());
                this.spotted(pos.getX(), pos.getZ(), GlintHeadRenderer.stack());
            }
        }
    }

    private void alert() {
        long now = SimulationSignals.now();
        int spacing = Math.max(4, 2 * SimulationSignals.simulation());

        for (long key : this.peaks) {
            if (!this.near(key, spacing)) {
                this.remember(key);
                int x = (ChunkPos.getX(key) << 4) + 8;
                int z = (ChunkPos.getZ(key) << 4) + 8;
                this.report(key, x, z, now);
                this.spotted(x, z, GlintHeadRenderer.stack());
            }
        }
    }

    private boolean near(long key, int spacing) {
        for (long old : this.found) {
            if (SimulationSignals.distance(ChunkPos.getX(old), ChunkPos.getZ(old), ChunkPos.getX(key), ChunkPos.getZ(key)) <= spacing) {
                return true;
            }
        }

        return false;
    }

    private void remember(long key) {
        this.found.add(key);

        while (this.found.size() > 256) {
            this.found.removeFirstLong();
        }
    }

    private void refreshShown(LocalPlayer self) {
        this.shown.clear();
        int pcx = self.blockPosition().getX() >> 4;
        int pcz = self.blockPosition().getZ() >> 4;
        int reach = SimulationSignals.view() + SimulationSignals.simulation() + 2;

        for (long key : this.found) {
            if (SimulationSignals.distance(ChunkPos.getX(key), ChunkPos.getZ(key), pcx, pcz) <= reach) {
                this.shown.add(key);
            }
        }
    }

    private void report(long found, int x, int z, long now) {
        int sim = SimulationSignals.simulation();
        int fx = ChunkPos.getX(found);
        int fz = ChunkPos.getZ(found);
        List<Entry<SimulationSignals.ChunkSignal>> sources = new ArrayList<>();

        for (Entry<SimulationSignals.ChunkSignal> entry : SimulationSignals.chunks().long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            if (SimulationSignals.distance(ChunkPos.getX(key), ChunkPos.getZ(key), fx, fz) <= sim && !(entry.getValue().score(now) < 0.5F)) {
                sources.add(entry);
            }
        }

        sources.sort((a, b) -> Float.compare(b.getValue().score(now), a.getValue().score(now)));
        List<String> lines = new ArrayList<>();
        lines.add(
            "Found "
                + x
                + ", "
                + z
                + " from "
                + sources.size()
                + " chunks (sim "
                + sim
                + ", ring "
                + SimulationSignals.ringStart()
                + "-"
                + SimulationSignals.view()
                + ")"
        );

        for (int i = 0; i < Math.min(4, sources.size()); i++) {
            SimulationSignals.ChunkSignal signal = sources.get(i).getValue();
            long key = sources.get(i).getLongKey();
            StringBuilder kinds = new StringBuilder();

            for (SimulationSignals.Kind kind : SimulationSignals.Kind.values()) {
                if (signal.count(kind) > 0) {
                    kinds.append(kinds.isEmpty() ? "" : ", ").append(kind.title).append(" x").append(signal.count(kind));
                }
            }

            lines.add(
                " chunk "
                    + ((ChunkPos.getX(key) << 4) + 8)
                    + ", "
                    + ((ChunkPos.getZ(key) << 4) + 8)
                    + String.format(Locale.ROOT, " score %.1f: ", signal.score(now))
                    + kinds
            );

            for (String recent : signal.recent()) {
                lines.add("   " + recent);
            }
        }

        for (String line : lines) {
            LOGGER.info("[Player Bypass] {}", line);
            ModuleChat.send(this, line);
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            LongOpenHashSet bases = new LongOpenHashSet();
            LongOpenHashSet suspects = new LongOpenHashSet();
            Long2ObjectMap<ChunkLightSnapshot> snapshots = PlayerBypassLightTracker.snapshots();
            if (!snapshots.isEmpty()) {
                int centerX = mc.player.blockPosition().getX() >> 4;
                int centerZ = mc.player.blockPosition().getZ() >> 4;
                if (PerformanceModule.optimizationsEnabled()) {
                    this.visibleChunks
                        .update(
                            PlayerBypassLightTracker.visibilityRevision(),
                            centerX,
                            centerZ,
                            snapshots,
                            PlayerBypassLightTracker::isFlagged,
                            PlayerBypassLightTracker::isSuspect
                        );
                    bases.addAll(this.visibleChunks.bases());
                    suspects.addAll(this.visibleChunks.suspects());
                } else {
                    this.visibleChunks.invalidate();

                    for (Entry<ChunkLightSnapshot> entry : snapshots.long2ObjectEntrySet()) {
                        long key = entry.getLongKey();
                        if (Math.abs(ChunkPos.getX(key) - centerX) <= 16
                            && Math.abs(ChunkPos.getZ(key) - centerZ) <= 16
                            && PlayerBypassLightTracker.isFlagged(key, entry.getValue())) {
                            (PlayerBypassLightTracker.isSuspect(key) ? suspects : bases).add(key);
                        }
                    }
                }
            }

            LongOpenHashSet activity = new LongOpenHashSet();
            if (!netherOrEnd(mc.level)) {
                for (long key : this.shown) {
                    if (!bases.contains(key) && !suspects.contains(key)) {
                        activity.add(key);
                    }
                }
            }

            if (!bases.isEmpty() || !suspects.isEmpty() || !activity.isEmpty()) {
                FoglessLines lines = FoglessLines.forFrame(event.partialTick);
                double bottom = mc.level.getMinY();
                double top = mc.level.getMaxY() + 1;
                this.mark(lines, suspects, -5766, bottom, top);
                this.mark(lines, activity, -11205, bottom, top);
                this.mark(lines, bases, -2516480, bottom, top);
                if (this.tracers.get()) {
                    this.chunkAreas.drawTracers(bases, 64.0, ARGB.opaque(-2516480));
                    this.chunkAreas.drawTracers(suspects, 64.0, ARGB.opaque(-5766));
                    this.chunkAreas.drawTracers(activity, 64.0, ARGB.opaque(-11205));
                }
            }
        }
    }

    private void mark(FoglessLines lines, LongOpenHashSet chunks, int color, double bottom, double top) {
        if (!chunks.isEmpty()) {
            float strength = Math.max(10, Math.min(255, this.opacity.getInt())) / 255.0F;
            GizmoStyle fill = GizmoStyle.fill(ARGB.color(Math.round(34.0F * strength), color));
            int edge = ARGB.color(Math.round(255.0F * strength), color);

            for (long key : chunks) {
                double x0 = ChunkPos.getX(key) << 4;
                double z0 = ChunkPos.getZ(key) << 4;
                double x1 = x0 + 16.0;
                double z1 = z0 + 16.0;
                Gizmos.cuboid(new AABB(x0, bottom, z0, x1, top, z1), fill).setAlwaysOnTop();
                column(lines, x0, z0, x1, z1, bottom, top, edge);
            }
        }
    }

    private static void column(FoglessLines lines, double x0, double z0, double x1, double z1, double bottom, double top, int color) {
        double[][] corners = new double[][]{{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};

        for (int i = 0; i < corners.length; i++) {
            double[] a = corners[i];
            double[] b = corners[(i + 1) % corners.length];
            lines.line(new Vec3(a[0], bottom, a[1]), new Vec3(a[0], top, a[1]), color, 2.0F);
            lines.line(new Vec3(a[0], bottom, a[1]), new Vec3(b[0], bottom, b[1]), color, 2.0F);
            lines.line(new Vec3(a[0], top, a[1]), new Vec3(b[0], top, b[1]), color, 2.0F);
        }
    }
}
