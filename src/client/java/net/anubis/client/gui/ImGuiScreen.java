package net.anubis.client.gui;

import java.nio.file.Path;
import java.util.List;
import net.anubis.client.config.HudTheme;
import net.anubis.client.config.ModuleConfigStore;
import net.anubis.client.config.ThemeStore;
import net.anubis.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class ImGuiScreen extends Screen {
    private final ClickGuiRenderer clickGui = new ClickGuiRenderer();

    public ImGuiScreen() {
        super(Component.literal("ANUBIS"));
    }

    @Override
    protected void init() {
        super.init();
        ImGuiManager.getInstance().activate(this.clickGui::render);
    }

    @Override
    public void removed() {
        super.removed();
        this.clickGui.handleEscape();
        ImGuiManager.getInstance().deactivate();
        ModuleConfigStore.getInstance().saveIfChanged();
        ThemeStore.get().save();
        HudTheme.get().save();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (ThemeStore.get().menuBlur.get()) {
            super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        this.clickGui.onFilesDropped(paths);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta) {
        if (!ImGuiManager.getInstance().available()) {
            net.minecraft.client.Minecraft.getInstance().setScreen(new MobileClickGuiScreen());
            return;
        }

        ImGuiManager.getInstance().requestCursor(graphics);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return event.key() == 256 && this.clickGui.handleEscape() ? true : super.keyPressed(event);
    }

    public boolean capturesKeys() {
        return this.clickGui.capturesKeys();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
