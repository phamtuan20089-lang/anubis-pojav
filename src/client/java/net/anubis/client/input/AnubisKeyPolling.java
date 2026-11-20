package net.anubis.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class AnubisKeyPolling {
    private AnubisKeyPolling() {
    }

    public static boolean isKeyDown(Window window, int key) {
        return InputConstants.isKeyDown(window, key);
    }
}
