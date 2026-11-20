package net.anubis.client.module.impl.donutmacros;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.ListenerHandle;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.setting.StringListSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.item.Item;

@Environment(EnvType.CLIENT)
public final class DonutSell {
    private static final Pattern RATE_LIMIT = Pattern.compile("you need to wait another ([\\d.]+) seconds?");
    private static final long GIVE_UP_MS = 45000L;
    private final SellRoutine routine;
    private final ListenerHandle<PacketReceiveNettyEvent> chat;
    private final long started = System.currentTimeMillis();
    private boolean sent;
    private boolean done;
    private int ticks;

    private DonutSell(ClientModule owner, Map<Integer, Item> slots) {
        Map<Integer, Item> wanted = Map.copyOf(slots);
        this.routine = new SellRoutine(
            owner, new StringListSetting("Never Sell"), (index, stack) -> wanted.get(index) == null || !stack.is(wanted.get(index)), () -> this.sent = true
        );
        this.chat = PacketReceiveNettyEvent.TYPE.listen(this, Stage.PRE, EventPriority.NORMAL, event -> {
            if (event.packet instanceof ClientboundSystemChatPacket packet && packet.content() != null) {
                Matcher wait = RATE_LIMIT.matcher(ServerText.normalize(packet.content().getString()));
                if (wait.find()) {
                    try {
                        long retry = System.currentTimeMillis() + (long)Math.ceil(Double.parseDouble(wait.group(1)) * 1000.0) + 100L;
                        Minecraft.getInstance().execute(() -> this.routine.commandRejected(retry));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        });
    }

    public static DonutSell start(ClientModule owner, Map<Integer, Item> slots) {
        return new DonutSell(owner, slots);
    }

    public boolean tick() {
        if (this.done) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && mc.getConnection() != null && mc.gameMode != null) {
            long now = System.currentTimeMillis();
            boolean busy = this.routine.step(mc, player, mc.getConnection(), now, true);
            this.ticks++;
            if (!busy && !this.routine.active()) {
                if (this.sent || this.ticks > 3 || now - this.started > 45000L) {
                    this.finish();
                }

                return !this.done;
            } else {
                if (now - this.started > 45000L) {
                    this.cancel();
                }

                return !this.done;
            }
        } else {
            this.cancel();
            return false;
        }
    }

    public void cancel() {
        if (!this.done) {
            this.routine.abort(Minecraft.getInstance());
            this.finish();
        }
    }

    private void finish() {
        this.done = true;
        this.chat.unregister();
    }
}
