package net.anubis.client.security;

import java.lang.StackWalker.StackFrame;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class PayGuard {
    private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
    private static final long ALLOW_MS = 5000L;
    private static final long SCREEN_GAP_MS = 30000L;
    private static final long CHAT_GAP_MS = 3000L;
    private static final int SHOWN_CHARS = 64;
    private static final Component TITLE = Component.literal("You are using a fake Anubis!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
    private static final ConcurrentLinkedQueue<PayGuard.Allowance> allowed = new ConcurrentLinkedQueue<>();
    private static volatile long lastScreenMs;
    private static volatile long lastChatMs;
    private static volatile String lastChatShown = "";

    private PayGuard() {
    }

    public static void allowTyped(String chatInput) {
        if (chatInput != null && chatInput.strip().startsWith("/")) {
            allow(chatInput);
        }
    }

    public static void allow(String command) {
        if (isPay(command)) {
            long now = System.currentTimeMillis();
            allowed.removeIf(entry -> now - entry.atMs() > 5000L);
            allowed.offer(new PayGuard.Allowance(normalize(command), now));
        }
    }

    public static boolean blocks(Packet<?> packet) {
        String command;
        if (packet instanceof ServerboundChatCommandPacket plain) {
            command = plain.command();
        } else {
            if (!(packet instanceof ServerboundChatCommandSignedPacket signed)) {
                return false;
            }

            command = signed.command();
        }

        if (isPay(command) && !consume(command)) {
            report(command);
            return true;
        } else {
            return false;
        }
    }

    public static boolean isPay(String command) {
        String text = normalize(command);
        int space = text.indexOf(32);
        String name = space < 0 ? text : text.substring(0, space);
        name = name.substring(name.indexOf(58) + 1);
        return name.equals("pay") || name.equals("epay");
    }

    private static String normalize(String command) {
        String text = command == null ? "" : command.strip();
        if (text.startsWith("/")) {
            text = text.substring(1).strip();
        }

        return text.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean consume(String command) {
        String wanted = normalize(command);
        long now = System.currentTimeMillis();
        Iterator<PayGuard.Allowance> it = allowed.iterator();

        while (it.hasNext()) {
            PayGuard.Allowance entry = it.next();
            if (now - entry.atMs() > 5000L) {
                it.remove();
            } else if (entry.command().equals(wanted)) {
                it.remove();
                return true;
            }
        }

        return false;
    }

    private static void report(String command) {
        String text = command.strip();
        String shown = "/" + (text.length() > 64 ? text.substring(0, 64) + "..." : text);
        LOGGER.warn("[Anubis] Pay Guard blocked \"{}\" sent by {}", shown, caller());
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> warn(mc, shown));
    }

    private static String caller() {
        return StackWalker.getInstance()
            .walk(
                frames -> frames.map(StackFrame::getClassName)
                    .filter(
                        name -> !name.startsWith("net.anubis.client.security.")
                            && !name.startsWith("net.minecraft.")
                            && !name.startsWith("com.mojang.")
                            && !name.startsWith("io.netty.")
                            && !name.startsWith("java.")
                            && !name.startsWith("jdk.")
                            && !name.startsWith("org.spongepowered.")
                            && !name.startsWith("com.example.client.mixin.")
                    )
                    .findFirst()
                    .orElse("unknown code")
            );
    }

    private static void warn(Minecraft mc, String shown) {
        long now = System.currentTimeMillis();
        if (mc.gui != null && (now - lastChatMs >= 3000L || !shown.equals(lastChatShown))) {
            lastChatMs = now;
            lastChatShown = shown;
            mc.gui
                .getChat()
                .addMessage(
                    Component.empty()
                        .append(Component.literal("[Anubis] ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                        .append(
                            Component.literal(
                                    "Blocked "
                                        + shown
                                        + ". You are using a fake Anubis, this version is not trustworthy. The real one is at discord.gg/anubis. Don't tell whoever gave you this file that it was caught."
                                )
                                .withStyle(ChatFormatting.RED)
                        )
                );
        }

        if (!(mc.screen instanceof PayGuard.WarningScreen) && now - lastScreenMs >= 30000L) {
            lastScreenMs = now;
            mc.setScreen(new PayGuard.WarningScreen(mc.screen, shown));
        }
    }

    @Environment(EnvType.CLIENT)
    private record Allowance(String command, long atMs) {
    }

    @Environment(EnvType.CLIENT)
    private static final class WarningScreen extends ConfirmScreen {
        WarningScreen(Screen previous, String shown) {
            super(
                open -> {
                    if (open) {
                        Util.getPlatform().openUri("https://discord.gg/anubis");
                    }

                    Minecraft.getInstance().setScreen(previous);
                },
                PayGuard.TITLE,
                Component.literal(
                    "This copy of Anubis tried to send "
                        + shown
                        + " by itself. It was blocked.\n\nThis version is not trustworthy. Delete it and get the real Anubis from discord.gg/anubis.\n\nDon't tell the person or place you got this file from that it was caught, so they don't find out. Report it in discord.gg/anubis instead."
                ),
                Component.literal("Open discord.gg/anubis"),
                Component.literal("Close")
            );
        }
    }
}
