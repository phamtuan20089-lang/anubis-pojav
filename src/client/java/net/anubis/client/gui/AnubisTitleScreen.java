package net.anubis.client.gui;

import net.anubis.client.config.ThemeStore;
import net.anubis.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

@Environment(EnvType.CLIENT)
public final class AnubisTitleScreen extends TitleScreen {
    private final MainMenuRenderer menu = new MainMenuRenderer(this);

    public static boolean enabled() {
        return ThemeStore.get().customMainMenu.get() && ImGuiManager.getInstance().available();
    }

    @Override
    protected void init() {
        ImGuiManager.getInstance().activate(this.menu::render);
    }

    @Override
    public void tick() {
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return this.menu.keyPressed(event.key()) || super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        ImGuiManager.getInstance().deactivate();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!ImGuiManager.getInstance().available()) {
            net.minecraft.client.Minecraft.getInstance().setScreen(new TitleScreen());
            return;
        }

        ImGuiManager.getInstance().requestCursor(graphics);
        graphics.renderDeferredElements();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
