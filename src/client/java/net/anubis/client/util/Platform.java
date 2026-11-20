package net.anubis.client.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime platform detection so Anubis can degrade gracefully inside
 * PojavLauncher-family launchers (Pojav, Amethyst, Zalith, FCL, ...) on Android.
 */
@Environment(EnvType.CLIENT)
public final class Platform {
    private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
    private static final boolean ANDROID = detectAndroid();
    private static volatile Boolean awtOk;

    private Platform() {
    }

    private static boolean detectAndroid() {
        if (Boolean.getBoolean("anubis.forceAndroid")) {
            return true;
        }
        if (Boolean.getBoolean("anubis.forceDesktop")) {
            return false;
        }
        try {
            for (String key : System.getenv().keySet()) {
                if (key.startsWith("POJAV") || key.equals("ANDROID_ROOT") || key.equals("ANDROID_DATA")) {
                    return true;
                }
            }
            for (String key : System.getProperties().stringPropertyNames()) {
                if (key.startsWith("pojav") || key.startsWith("glfwstub")) {
                    return true;
                }
            }
            String vendor = System.getProperty("java.vendor", "").toLowerCase(Locale.ROOT);
            String vm = System.getProperty("java.vm.vendor", "").toLowerCase(Locale.ROOT);
            return vendor.contains("android") || vm.contains("android");
        } catch (SecurityException e) {
            return false;
        }
    }

    /** True when running under PojavLauncher or another Android launcher. */
    public static boolean isAndroid() {
        return ANDROID;
    }

    /** True when the GL renderer looks like a mobile / translated implementation. */
    public static boolean isMobileRenderer(String glRenderer) {
        if (ANDROID) {
            return true;
        }
        if (glRenderer == null) {
            return false;
        }
        String r = glRenderer.toLowerCase(Locale.ROOT);
        return r.contains("gl4es") || r.contains("mali") || r.contains("adreno") || r.contains("angle") || r.contains("powervr");
    }

    /** One-time probe: can we create a BufferedImage + Graphics2D + Font here? */
    public static boolean awtUsable() {
        Boolean cached = awtOk;
        if (cached != null) {
            return cached;
        }
        boolean ok;
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12));
            g.getFontMetrics().stringWidth("A");
            g.dispose();
            ok = true;
        } catch (Throwable t) {
            LOGGER.warn("[Anubis] java.awt is not usable here ({}); AWT-drawn sky textures are disabled", t.toString());
            ok = false;
        }
        awtOk = ok;
        return ok;
    }

    /**
     * Must run before any imgui-java class initialises. Lets an Android build of the
     * imgui-java JNI library be dropped into {@code <config>/anubis/natives/} (or pointed at
     * with -Dimgui.library.path) without touching the bundled desktop natives.
     */
    public static void prepareImGuiNatives() {
        if (!ANDROID || System.getProperty("imgui.library.path") != null) {
            return;
        }
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("anubis").resolve("natives");
            if (Files.isDirectory(dir)) {
                System.setProperty("imgui.library.path", dir.toAbsolutePath().toString());
                LOGGER.info("[Anubis] Using ImGui natives from {}", dir);
            } else {
                LOGGER.warn("[Anubis] Android detected but {} is missing; ImGui UI will be unavailable (see docs/ANDROID.md)", dir);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("[Anubis] Could not prepare ImGui natives: {}", e.toString());
        }
    }
}
