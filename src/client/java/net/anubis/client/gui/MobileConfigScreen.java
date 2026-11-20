package net.anubis.client.gui;

import java.util.List;
import net.anubis.client.config.ConfigProfiles;
import net.anubis.client.config.FriendStore;
import net.anubis.client.config.HudTheme;
import net.anubis.client.config.ModuleConfigStore;
import net.anubis.client.config.ProfileException;
import net.anubis.client.config.ThemeStore;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Config / Profile / Theme screen for launchers where ImGui cannot load (PojavLauncher and friends).
 * Built only from vanilla widgets and wired to the same backends as the desktop Config tab
 * ({@link ConfigProfiles}, {@link ModuleConfigStore}, {@link ThemeStore}, {@link HudTheme}).
 *
 * Profile import/export uses the clipboard share code, because native file dialogs do not exist on Android.
 */
@Environment(EnvType.CLIENT)
public final class MobileConfigScreen extends Screen {
    private static final int ROWS_PER_PAGE = 4;
    private static final int COL_TEXT = 0xFFEDE8F4;
    private static final int COL_DIM = 0xFF8E869C;
    private static final int COL_OK = 0xFF8FE3A8;
    private static final int COL_ERR = 0xFFFF7B8A;
    private static final int COL_ACTIVE = 0xFFFF6EB4;
    private static final long MESSAGE_MS = 4000L;
    private static final long ARM_MS = 3000L;

    private static int pageKeep;
    private static String nameKeep = "";

    private EditBox nameBox;
    private int page;
    private String message;
    private boolean messageError;
    private long messageAt;
    private String armed;
    private long armedAt;

    public MobileConfigScreen() {
        super(Component.literal("ANUBIS CONFIG"));
        this.page = pageKeep;
    }

    public boolean capturesKeys() {
        return this.nameBox != null && this.nameBox.isFocused();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        if (this.nameBox != null) {
            nameKeep = this.nameBox.getValue();
        }

        pageKeep = this.page;
        ModuleConfigStore.getInstance().saveIfChanged();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new MobileClickGuiScreen());
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        if (this.nameBox != null) {
            nameKeep = this.nameBox.getValue();
        }

        int w = Math.min(this.width - 12, 300);
        int x = (this.width - w) / 2;
        int y = 22;
        int gap = 4;
        int bh = 18;

        // Row 1: save / reset / back
        int third = (w - gap * 2) / 3;
        this.add(x, y, third, bh, "SAVE ALL", this::saveAll);
        boolean resetArmed = this.isArmed("reset-all");
        this.add(x + third + gap, y, third, bh, resetArmed ? "CONFIRM" : "RESET ALL", () -> {
            if (this.confirm("reset-all")) {
                for (ClientModule module : ModuleRegistry.all()) {
                    module.resetSettings();
                }

                this.say("Every module setting is back to default", false);
            }
        });
        this.add(x + (third + gap) * 2, y, third, bh, "BACK", this::onClose);
        y += bh + gap;

        // Row 2: new profile
        int createW = 56;
        this.nameBox = new EditBox(this.font, x, y, w - createW - gap, bh, Component.literal("Profile name"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setHint(Component.literal("New profile name"));
        this.nameBox.setValue(nameKeep);
        this.addRenderableWidget(this.nameBox);
        this.add(x + w - createW, y, createW, bh, "CREATE", this::createProfile);
        y += bh + gap;

        // Row 3: share code + theme clipboard
        int half = (w - gap) / 2;
        this.add(x, y, half, bh, "PASTE PROFILE CODE", this::pasteProfileCode);
        this.add(x + half + gap, y, half, bh, "RESET THEME", () -> {
            ThemeStore.get().resetToDefaults();
            this.say("Theme reset to the defaults", false);
        });
        y += bh + gap;
        this.add(x, y, half, bh, "COPY THEME", () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(ThemeStore.get().exportString());
            this.say("Theme copied to the clipboard", false);
        });
        this.add(x + half + gap, y, half, bh, "PASTE THEME", () -> {
            boolean ok = ThemeStore.get().importString(Minecraft.getInstance().keyboardHandler.getClipboard());
            this.say(ok ? "Theme applied from the clipboard" : "The clipboard doesn't contain an Anubis theme", !ok);
        });
        y += bh + gap + 12; // room for the message line

        // Profile list
        List<ConfigProfiles.Profile> list = ConfigProfiles.getInstance().list();
        int pages = Math.max(1, (list.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        this.page = Math.max(0, Math.min(this.page, pages - 1));
        int from = this.page * ROWS_PER_PAGE;
        int to = Math.min(list.size(), from + ROWS_PER_PAGE);
        int rowH = 24;
        int btnW = 40;
        for (int i = from; i < to; i++) {
            ConfigProfiles.Profile p = list.get(i);
            int bx = x + w - btnW * 4 - gap * 3;
            int by = y + (i - from) * rowH + 4;
            String name = p.name();
            this.add(bx, by, btnW, bh, "LOAD", () -> this.loadProfile(name));
            this.add(bx + btnW + gap, by, btnW, bh, "SAVE", () -> this.saveProfile(name));
            this.add(bx + (btnW + gap) * 2, by, btnW, bh, "CODE", () -> this.copyCode(name));
            boolean del = this.isArmed("del:" + name);
            this.add(bx + (btnW + gap) * 3, by, btnW, bh, del ? "SURE?" : "DEL", () -> this.deleteProfile(name));
        }

        int navY = this.height - 24;
        this.add(x, navY, 50, bh, "< PREV", () -> {
            this.page--;
            this.rebuildWidgets();
        });
        this.add(x + w - 50, navY, 50, bh, "NEXT >", () -> {
            this.page++;
            this.rebuildWidgets();
        });
    }

    private void add(int x, int y, int w, int h, String label, Runnable action) {
        this.addRenderableWidget(Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, h).build());
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, this.width, this.height, 0x99000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int w = Math.min(this.width - 12, 300);
        int x = (this.width - w) / 2;

        g.drawString(this.font, "ANUBIS CONFIG", x, 8, COL_TEXT);
        ModuleConfigStore store = ModuleConfigStore.getInstance();
        String status = store.isReadOnly() ? "READ-ONLY" : (store.hasUnsavedChanges() ? "SAVING..." : "SAVED");
        g.drawString(this.font, status, x + w - this.font.width(status), 8, store.isReadOnly() ? COL_ERR : COL_DIM);

        int msgY = 22 + (18 + 4) * 4 + 1;
        if (this.message != null && System.currentTimeMillis() - this.messageAt < MESSAGE_MS) {
            g.drawString(this.font, this.fit(this.message, w), x, msgY, this.messageError ? COL_ERR : COL_OK);
        }

        List<ConfigProfiles.Profile> list = ConfigProfiles.getInstance().list();
        int pages = Math.max(1, (list.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        int listTop = msgY + 12;
        if (list.isEmpty()) {
            g.drawString(this.font, "No profiles yet", x, listTop + 8, COL_DIM);
        }

        int from = this.page * ROWS_PER_PAGE;
        int to = Math.min(list.size(), from + ROWS_PER_PAGE);
        String active = store.activeProfile();
        int nameW = w - 40 * 4 - 4 * 3 - 6;
        for (int i = from; i < to; i++) {
            ConfigProfiles.Profile p = list.get(i);
            boolean isActive = p.name().equals(active);
            String label = (isActive ? "* " : "") + p.name() + (p.broken() ? " (broken)" : "");
            g.drawString(this.font, this.fit(label, nameW), x, listTop + (i - from) * 24 + 9, p.broken() ? COL_ERR : (isActive ? COL_ACTIVE : COL_TEXT));
        }

        String pageText = (this.page + 1) + " / " + pages;
        g.drawString(this.font, pageText, (this.width - this.font.width(pageText)) / 2, this.height - 19, COL_DIM);
        if (this.armed != null && System.currentTimeMillis() - this.armedAt > ARM_MS) {
            this.armed = null;
            this.rebuildWidgets();
        }
    }

    private String fit(String s, int maxW) {
        if (this.font.width(s) <= maxW) {
            return s;
        }

        String ellipsis = "...";
        int end = s.length();
        while (end > 1 && this.font.width(s.substring(0, end) + ellipsis) > maxW) {
            end--;
        }

        return s.substring(0, end) + ellipsis;
    }

    // ------------------------------------------------------------------ actions

    private void say(String text, boolean error) {
        this.message = text;
        this.messageError = error;
        this.messageAt = System.currentTimeMillis();
        this.rebuildWidgets();
    }

    private boolean isArmed(String key) {
        return key.equals(this.armed) && System.currentTimeMillis() - this.armedAt <= ARM_MS;
    }

    /** Two-tap confirmation: first tap arms, second tap within 3 seconds confirms. */
    private boolean confirm(String key) {
        if (this.isArmed(key)) {
            this.armed = null;
            return true;
        }

        this.armed = key;
        this.armedAt = System.currentTimeMillis();
        this.rebuildWidgets();
        return false;
    }

    private void saveAll() {
        ModuleConfigStore store = ModuleConfigStore.getInstance();
        store.save();
        ThemeStore.get().save();
        HudTheme.get().save();
        HudLayoutStore.getInstance().save();
        FriendStore.getInstance().save();
        if (store.isReadOnly()) {
            this.say("modules.json is unreadable, so it was left untouched", true);
        } else {
            this.say("Everything saved", false);
        }
    }

    private void createProfile() {
        try {
            String name = ConfigProfiles.getInstance().create(this.nameBox.getValue());
            nameKeep = "";
            this.say("Created " + name + " from the current modules", false);
        } catch (ProfileException e) {
            nameKeep = this.nameBox.getValue();
            this.say(e.getMessage(), true);
        }
    }

    private void loadProfile(String name) {
        try {
            ConfigProfiles profiles = ConfigProfiles.getInstance();
            ConfigProfiles.Profile profile = profiles.find(name);
            profiles.backup(profile == null ? java.util.Set.of(ConfigProfiles.Part.MODULES) : profile.parts());
            profiles.load(name);
            this.say("Loaded " + name + " (previous settings are in Auto backup)", false);
        } catch (ProfileException e) {
            this.say(e.getMessage(), true);
        }
    }

    private void saveProfile(String name) {
        try {
            ConfigProfiles.getInstance().save(name);
            this.say("Saved the current settings into " + name, false);
        } catch (ProfileException e) {
            this.say(e.getMessage(), true);
        }
    }

    private void copyCode(String name) {
        try {
            String code = ConfigProfiles.getInstance().shareCode(name);
            Minecraft.getInstance().keyboardHandler.setClipboard(code);
            this.say("Share code copied (" + code.length() + " characters)", false);
        } catch (ProfileException e) {
            this.say(e.getMessage(), true);
        }
    }

    private void deleteProfile(String name) {
        if (!this.confirm("del:" + name)) {
            return;
        }

        try {
            ConfigProfiles.getInstance().delete(name);
            this.say("Deleted " + name, false);
        } catch (ProfileException e) {
            this.say(e.getMessage(), true);
        }
    }

    private void pasteProfileCode() {
        ConfigProfiles profiles = ConfigProfiles.getInstance();
        try {
            String name = profiles.importCode(Minecraft.getInstance().keyboardHandler.getClipboard());
            ConfigProfiles.Profile imported = profiles.find(name);
            profiles.backup(imported == null ? java.util.Set.of(ConfigProfiles.Part.MODULES) : imported.parts());
            profiles.load(name);
            this.say("Loaded " + name + " (previous settings are in Auto backup)", false);
        } catch (ProfileException e) {
            this.say(e.getMessage(), true);
        }
    }
}
