package net.anubis.client.gui.mainmenu;

import imgui.ImDrawList;
import imgui.ImFont;
import java.util.ArrayList;
import java.util.List;
import net.anubis.client.gui.AnubisTitleScreen;
import net.anubis.client.security.OfficialBuild;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

@Environment(EnvType.CLIENT)
public final class OfficialBuildBanner {
    private static final String TITLE = "This Anubis file has been modified";
    private static final String[] LINES = new String[]{
        "It differs from the signed official release, so its code may have been changed.",
        "Get Anubis only from the official Discord.",
        "If you did not change it yourself, delete it and scan your PC."
    };
    private static final int RED = -2537681;
    private static final int WHITE = -921103;
    private static final int PANEL = -16053492;

    private OfficialBuildBanner() {
    }

    public static void draw(MenuFrame frame) {
        if (OfficialBuild.state() == OfficialBuild.State.MODIFIED) {
            ImDrawList dl = frame.fg();
            ImFont titleFont = frame.bodyFont();
            ImFont bodyFont = frame.tagFont();
            float pad = frame.px(14.0F);
            float gap = frame.px(6.0F);
            float titleHeight = frame.textHeight(titleFont);
            float bodyHeight = frame.textHeight(bodyFont);
            float width = frame.textWidth(titleFont, "This Anubis file has been modified");

            for (String line : LINES) {
                width = Math.max(width, frame.textWidth(bodyFont, line));
            }

            width += pad * 2.0F;
            float height = pad * 2.0F + titleHeight + (gap + bodyHeight) * LINES.length;
            float x = frame.px(24.0F);
            float y = frame.height() - height - frame.px(24.0F);
            dl.addRectFilled(x, y, x + width, y + height, MenuFrame.col(-16053492, 0.95F), frame.px(4.0F));
            dl.addRect(x, y, x + width, y + height, MenuFrame.col(-2537681, 0.95F), frame.px(4.0F), 0, Math.max(1.5F, frame.scale() * 2.0F));
            float lineY = y + pad;
            frame.text(dl, titleFont, x + pad, lineY, -2537681, 1.0F, "This Anubis file has been modified");
            lineY += titleHeight;

            for (String line : LINES) {
                lineY += gap;
                frame.text(dl, bodyFont, x + pad, lineY, -921103, 1.0F, line);
                lineY += bodyHeight;
            }
        }
    }

    public static void installVanillaTitleScreen() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof TitleScreen && !(screen instanceof AnubisTitleScreen)) {
                ScreenEvents.afterRender(screen).register((shown, graphics, mouseX, mouseY, delta) -> drawVanilla(graphics));
            }
        });
    }

    private static void drawVanilla(GuiGraphics graphics) {
        if (OfficialBuild.state() == OfficialBuild.State.MODIFIED) {
            Font font = Minecraft.getInstance().font;
            int pad = 6;
            int lineHeight = font.lineHeight + 2;
            int x = 4;
            int y = 4;
            int maxText = Math.max(60, graphics.guiWidth() - x * 2 - pad * 2);
            List<FormattedCharSequence> title = font.split(Component.literal("This Anubis file has been modified"), maxText);
            List<FormattedCharSequence> body = new ArrayList<>();

            for (String line : LINES) {
                body.addAll(font.split(Component.literal(line), maxText));
            }

            int width = 0;

            for (FormattedCharSequence row : title) {
                width = Math.max(width, font.width(row));
            }

            for (FormattedCharSequence row : body) {
                width = Math.max(width, font.width(row));
            }

            int right = x + width + pad * 2;
            int bottom = y + pad * 2 + lineHeight * (title.size() + body.size());
            graphics.fill(x, y, right, bottom, -401929460);
            graphics.fill(x, y, right, y + 1, -2537681);
            graphics.fill(x, bottom - 1, right, bottom, -2537681);
            graphics.fill(x, y, x + 1, bottom, -2537681);
            graphics.fill(right - 1, y, right, bottom, -2537681);
            int rowY = y + pad;

            for (FormattedCharSequence row : title) {
                graphics.drawString(font, row, x + pad, rowY, -2537681, true);
                rowY += lineHeight;
            }

            for (FormattedCharSequence row : body) {
                graphics.drawString(font, row, x + pad, rowY, -921103, true);
                rowY += lineHeight;
            }
        }
    }
}
