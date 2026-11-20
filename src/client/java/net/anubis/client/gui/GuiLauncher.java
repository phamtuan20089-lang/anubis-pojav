package net.anubis.client.gui;

import net.anubis.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;

/** Picks the ImGui ClickGUI when it works, otherwise the vanilla-widget mobile GUI. */
@Environment(EnvType.CLIENT)
public final class GuiLauncher {
    private GuiLauncher() {
    }

    public static Screen create() {
        return ImGuiManager.getInstance().available() ? new ImGuiScreen() : new MobileClickGuiScreen();
    }

    public static boolean isOpen(Screen screen) {
        return screen instanceof ImGuiScreen || screen instanceof MobileClickGuiScreen || screen instanceof MobileConfigScreen;
    }

    /** True when the open Anubis GUI is busy with text/key input and must not be closed by the GUI key. */
    public static boolean capturing(Screen screen) {
        if (screen instanceof ImGuiScreen gui) {
            return gui.capturesKeys();
        }

        if (screen instanceof MobileConfigScreen config) {
            return config.capturesKeys();
        }

        return screen instanceof MobileClickGuiScreen mobile && mobile.capturesKeys();
    }
}
