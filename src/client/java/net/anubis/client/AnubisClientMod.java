package net.anubis.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.anubis.client.addon.AddonManager;
import net.anubis.client.config.HudTheme;
import net.anubis.client.config.ModuleConfigStore;
import net.anubis.client.config.ProfileAutoLoad;
import net.anubis.client.config.ThemeStore;
import net.anubis.client.donutbets.DonutBetsFeed;
import net.anubis.client.esp.ChunkScanner;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.event.impl.Render2DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.gui.BorderlessFullscreen;
import net.anubis.client.gui.HudLayoutStore;
import net.anubis.client.gui.HudRenderer;
import net.anubis.client.gui.GuiLauncher;
import net.anubis.client.gui.ImGuiScreen;
import net.anubis.client.gui.MobileMenuButtons;
import net.anubis.client.gui.LoadingScreens;
import net.anubis.client.gui.StaffGhost;
import net.anubis.client.gui.ToggleToasts;
import net.anubis.client.gui.mainmenu.OfficialBuildBanner;
import net.anubis.client.imgui.ImGuiManager;
import net.anubis.client.input.AnubisKeyPolling;
import net.anubis.client.input.ModuleKeybinds;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.misc.noinvleak.StreamOverlay;
import net.anubis.client.security.OfficialBuild;
import net.anubis.client.util.BlockEntityLeakTracker;
import net.anubis.client.util.CompletionChannel;
import net.anubis.client.util.combat.CombatTarget;
import net.anubis.client.util.netherite.NetheriteChunkTracker;
import net.anubis.client.util.playerbypass.PlayerBypassLightTracker;
import net.anubis.client.util.playerbypass.ProtectedChunkStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class AnubisClientMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
    public static final Identifier SPAWNER_NOTIFY_SOUND_ID = Identifier.fromNamespaceAndPath("anubis", "spawner_notify");
    public static final SoundEvent SPAWNER_NOTIFY_SOUND = SoundEvent.createVariableRangeEvent(SPAWNER_NOTIFY_SOUND_ID);
    public static final Category KEY_CATEGORY = Category.register(Identifier.fromNamespaceAndPath("anubis", "controls"));
    private static KeyMapping openGuiKey;
    private final ModuleKeybinds keybinds = new ModuleKeybinds();
    private static final Object LEAK_TRACKER_OWNER = new Object();
    private static final Object PLAYER_BYPASS_TRACKER_OWNER = new Object();
    private static final Object NETHERITE_TRACKER_OWNER = new Object();

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Anubis] Initialising");
        AddonManager.init();
        ImGuiManager.getInstance().setHudRenderer(HudRenderer.getInstance()::render, HudRenderer.getInstance()::hasWork);
        ImGuiManager.getInstance().setStreamRenderer(StreamOverlay::drawFakes, StreamOverlay::hasFakes);
        ImGuiManager.getInstance().setToastRenderer(ToggleToasts::render, ToggleToasts::hasWork);
        ImGuiManager.getInstance().setOverlayRenderer(LoadingScreens::render, LoadingScreens::hasWork);
        ImGuiManager.getInstance().setAlertRenderer(StaffGhost::render, StaffGhost::hasWork);
        Render2DEvent.TYPE.listen(StaffGhost.class, StaffGhost::renderNote);
        ChunkScanner.init();
        PacketReceiveNettyEvent.TYPE.listen(LEAK_TRACKER_OWNER, event -> BlockEntityLeakTracker.ingest(event.packet));
        CompletionChannel.install();
        DonutBetsFeed.start();
        PacketAppliedEvent.TYPE.listen(PLAYER_BYPASS_TRACKER_OWNER, event -> PlayerBypassLightTracker.onPacketApplied(event.packet));
        TickEvent.TYPE.listen(PLAYER_BYPASS_TRACKER_OWNER, event -> PlayerBypassLightTracker.onTick());
        TickEvent.TYPE.listen(BorderlessFullscreen.class, event -> BorderlessFullscreen.tick());
        CombatTarget.install();
        PacketAppliedEvent.TYPE.listen(NETHERITE_TRACKER_OWNER, event -> NetheriteChunkTracker.onPacketApplied(event.packet));
        ToggleToasts.install();
        ProfileAutoLoad.install();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            HudLayoutStore.getInstance().save();
            ModuleConfigStore.getInstance().saveAndWait();
            ThemeStore.get().save();
            HudTheme.get().save();
            ProtectedChunkStore.get().flushAndWait();
            StreamOverlay.stop();
        });
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.anubis.open_gui", Type.KEYSYM, 344, KEY_CATEGORY));
        ModuleConfigStore.getInstance().load();
        OfficialBuild.enforceIfPending();
        OfficialBuildBanner.installVanillaTitleScreen();
        MobileMenuButtons.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> OfficialBuild.enforceOnFirstTick());
        AddonManager.configLoaded();
        ClientTickEvents.END_CLIENT_TICK
            .register(
                client -> {
                    ModuleConfigStore.getInstance().applyPendingEnabled();

                    while (openGuiKey.consumeClick()) {
                        if (GuiLauncher.isOpen(client.screen)) {
                            if (!GuiLauncher.capturing(client.screen)) {
                                client.setScreen(null);
                            }
                        } else {
                            client.setScreen(GuiLauncher.create());
                        }
                    }

                    this.keybinds
                        .update(
                            ModuleRegistry.all(), AnubisClientMod::isBoundKeyDown, client.screen == null && client.isWindowActive() && client.player != null
                        );
                    ModuleConfigStore.getInstance().tickAutosave();
                }
            );
    }

    public static boolean isGuiKey(long window, KeyEvent event) {
        return openGuiKey != null && window == Minecraft.getInstance().getWindow().handle() && openGuiKey.matches(event);
    }

    public static boolean toggleGuiFromKey() {
        Minecraft client = Minecraft.getInstance();
        if (client.screen == null) {
            client.setScreen(GuiLauncher.create());
            return true;
        }

        if (GuiLauncher.isOpen(client.screen) && !GuiLauncher.capturing(client.screen)) {
            client.setScreen(null);
        }

        return false;
    }

    public static void dropGuiClicks() {
        while (openGuiKey.consumeClick()) {
        }
    }

    private static boolean isBoundKeyDown(int key) {
        Minecraft client = Minecraft.getInstance();
        return key >= 1000 ? GLFW.glfwGetMouseButton(client.getWindow().handle(), key - 1000) == 1 : AnubisKeyPolling.isKeyDown(client.getWindow(), key);
    }
}
