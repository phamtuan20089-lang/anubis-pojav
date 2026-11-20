package net.anubis.client.easyplace.builder;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

@Environment(EnvType.CLIENT)
final class Relogger {
    private static final int WAIT_TICKS = 60;
    private static final int TRIES = 5;
    private ServerData server;
    private boolean out;
    private int wait;
    private int tries;

    boolean active() {
        return this.server != null;
    }

    boolean start(Minecraft mc) {
        ServerData data = mc.isLocalServer() ? null : mc.getCurrentServer();
        if (data != null && !data.isRealm() && mc.level != null) {
            this.server = data;
            this.out = false;
            this.wait = 60;
            this.tries = 0;
            mc.schedule(() -> {
                if (this.server == data && mc.level != null) {
                    mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
                    this.out = true;
                }
            });
            return true;
        } else {
            return false;
        }
    }

    void tick(Minecraft mc) {
        if (this.server != null && this.out) {
            if (mc.level != null) {
                this.server = null;
            } else if (!(mc.screen instanceof ConnectScreen) && --this.wait <= 0) {
                if (++this.tries > 5) {
                    this.server = null;
                } else {
                    ServerData data = this.server;
                    Screen parent = mc.screen instanceof JoinMultiplayerScreen ? mc.screen : new JoinMultiplayerScreen(new TitleScreen());
                    this.wait = 180;
                    mc.schedule(() -> {
                        if (mc.level == null) {
                            ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(data.ip), data, false, null);
                        }
                    });
                }
            }
        }
    }
}
