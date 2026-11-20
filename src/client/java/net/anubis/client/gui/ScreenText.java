package net.anubis.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

@Environment(EnvType.CLIENT)
public final class ScreenText {
    private static final float IN = 0.4F;
    private static final float OUT = 0.8F;
    private final int rgb;
    private final float stay;
    private Component title;
    private Component subtitle;
    private long start;

    public ScreenText(int rgb, float staySeconds) {
        this.rgb = rgb & 16777215;
        this.stay = staySeconds;
    }

    public void show(String line, String below) {
        this.title = Component.literal(line);
        this.subtitle = Component.literal(below);
        this.start = 0L;
    }

    public void render(GuiGraphics g) {
        if (this.title != null) {
            Minecraft mc = Minecraft.getInstance();
            long now = System.nanoTime();
            if (this.start == 0L) {
                if (mc.screen != null || mc.level == null) {
                    return;
                }

                this.start = now;
            }

            float t = (float)(now - this.start) / 1.0E9F;
            if (t >= 0.4F + this.stay + 0.8F) {
                this.title = null;
            } else {
                float fade = t < 0.4F ? t / 0.4F : Math.min(1.0F, (0.4F + this.stay + 0.8F - t) / 0.8F);
                int alpha = Math.round(fade * 255.0F);
                if (alpha > 8) {
                    Font font = mc.font;
                    int color = ARGB.color(alpha, this.rgb);
                    g.pose().pushMatrix();
                    g.pose().translate(g.guiWidth() / 2.0F, g.guiHeight() / 2.0F - 30.0F);
                    g.pose().scale(3.0F, 3.0F);
                    g.drawString(font, this.title, -font.width(this.title) / 2, -font.lineHeight / 2, color, true);
                    g.pose().popMatrix();
                    g.pose().pushMatrix();
                    g.pose().translate(g.guiWidth() / 2.0F, g.guiHeight() / 2.0F);
                    g.pose().scale(1.5F, 1.5F);
                    g.drawString(font, this.subtitle, -font.width(this.subtitle) / 2, -font.lineHeight / 2, color, true);
                    g.pose().popMatrix();
                }
            }
        }
    }
}
