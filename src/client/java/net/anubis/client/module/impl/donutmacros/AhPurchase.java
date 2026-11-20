package net.anubis.client.module.impl.donutmacros;

import java.util.function.Predicate;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.ListenerHandle;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.module.ClientModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class AhPurchase {
    private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
    private final ClientModule owner;
    private final MacroWorker worker = new MacroWorker("Anubis AH Purchase");
    private final SniperBot bot;
    private final ListenerHandle<PacketReceiveNettyEvent> chat;
    private volatile AhPurchase.Status status = AhPurchase.Status.RUNNING;

    private AhPurchase(ClientModule owner, SnipeTarget target, int amount, long refreshMs, long budget) {
        this.owner = owner;
        this.bot = SniperBot.quota(owner, this::stopped, this.worker, target, amount, refreshMs, budget);
        this.chat = PacketReceiveNettyEvent.TYPE.listen(this, Stage.PRE, EventPriority.NORMAL, event -> {
            if (this.status == AhPurchase.Status.RUNNING) {
                if (event.packet instanceof ClientboundSystemChatPacket packet && packet.content() != null) {
                    if (packet.overlay()) {
                        this.bot.onNote(packet.content().getString());
                    } else {
                        this.bot.onChat(packet.content().getString());
                    }
                } else if (event.packet instanceof ClientboundSetActionBarTextPacket bar && bar.text() != null) {
                    this.bot.onNote(bar.text().getString());
                }
            }
        });
        this.worker.start(this.bot::run, failure -> {
            LOGGER.error("[Anubis] AH purchase stopped", failure);
            ModuleChat.send(owner, ModuleChat.body().danger("AH purchase stopped").text(": " + failure));
            this.stopped(this.bot);
        });
    }

    public static AhPurchase start(ClientModule owner, Item item, int amount, long maxPerListing, long refreshMs, long budget) {
        SnipeTarget target = SnipeTarget.matching(
            item,
            new ItemStack(item).getHoverName().getString(),
            Product.searchText(item),
            stack -> stack.is(item) && !stack.has(DataComponents.BLOCK_STATE) && !stack.has(DataComponents.BLOCK_ENTITY_DATA),
            maxPerListing
        );
        return new AhPurchase(owner, target, amount, refreshMs, budget);
    }

    public static AhPurchase startAny(
        ClientModule owner, Item shown, String name, String search, Predicate<ItemStack> filter, int amount, long maxPerListing, long refreshMs, long budget
    ) {
        return new AhPurchase(owner, SnipeTarget.matching(shown, name, search, filter, maxPerListing), amount, refreshMs, budget);
    }

    public static double unitPrice(ItemStack listing) {
        double total = ServerText.askingPrice(listing);
        return total <= 0.0 ? Double.MAX_VALUE : total / Math.max(1, listing.getCount());
    }

    public static long parseMoney(String raw) {
        return ServerText.parseMoney(raw);
    }

    public static String money(double value) {
        return ServerText.shortMoney(value);
    }

    public AhPurchase.Status status() {
        return this.status;
    }

    public int boughtItems() {
        return this.bot.boughtItems();
    }

    public long spent() {
        return this.bot.spent();
    }

    public void cancel() {
        if (this.status == AhPurchase.Status.RUNNING) {
            this.finish(AhPurchase.Status.FAILED);
        }
    }

    private void stopped(SniperBot from) {
        this.finish(from.complete() ? AhPurchase.Status.DONE : AhPurchase.Status.FAILED);
    }

    private synchronized void finish(AhPurchase.Status result) {
        if (this.status == AhPurchase.Status.RUNNING) {
            this.status = result;
            this.chat.unregister();
            this.worker.stop();
            Minecraft.getInstance().execute(() -> {
                Minecraft mc = Minecraft.getInstance();
                LocalPlayer player = mc.player;
                if (player != null) {
                    if (player.containerMenu != player.inventoryMenu) {
                        player.closeContainer();
                    } else {
                        Dialogs.close(mc);
                    }
                }
            });
            if (result == AhPurchase.Status.FAILED) {
                LOGGER.info("[Anubis] AH purchase for {} ended without finishing", this.owner.name());
            }
        }
    }

    @Environment(EnvType.CLIENT)
    public enum Status {
        RUNNING,
        DONE,
        FAILED;
    }
}
