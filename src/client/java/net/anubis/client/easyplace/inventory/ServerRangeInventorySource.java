package net.anubis.client.easyplace.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;

@Environment(EnvType.CLIENT)
public final class ServerRangeInventorySource implements InventoryPickSource {
    private static final int SYNC_TIMEOUT_TICKS = 12;
    private static final int HOTBAR_SIZE = 9;
    private static final int MAIN_END = 35;
    private static final int OFFHAND_SLOT = 40;
    private final Minecraft mc;
    private ServerRangeInventorySource.PendingPick pending;

    public ServerRangeInventorySource(Minecraft mc) {
        this.mc = mc;
    }

    @Override
    public InventoryPickResult prepare(Item item, long tick) {
        return this.prepare(item, tick, false);
    }

    public InventoryPickResult prepare(Item item, long tick, boolean driving) {
        LocalPlayer player = this.mc.player;
        if (player != null && this.mc.gameMode != null) {
            if (this.pending != null) {
                if (this.pending.item == item && tick - this.pending.startedTick <= 12L) {
                    return this.pollPending();
                }

                this.pending = null;
            }

            if (player.getOffhandItem().is(item)) {
                return InventoryPickResult.ready(item, InteractionHand.OFF_HAND, 40);
            }

            Inventory inventory = player.getInventory();
            if (player.getMainHandItem().is(item)) {
                return InventoryPickResult.ready(item, InteractionHand.MAIN_HAND, inventory.getSelectedSlot());
            }

            int found = find(inventory, item);
            if (found < 0) {
                return InventoryPickResult.missing(item);
            }

            if (found < 9) {
                return InventoryPickResult.ready(item, InteractionHand.MAIN_HAND, found);
            }

            if (player.containerMenu != player.inventoryMenu) {
                return InventoryPickResult.missing(item);
            }

            int destination = findHotbarDestination(inventory);
            if (driving ? InventoryCalm.settle(this.mc, player) : InventoryCalm.idle(player)) {
                this.mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, found, destination, ClickType.SWAP, player);
                if (this.mc.screen == null) {
                    player.connection.send(new ServerboundContainerClosePacket(player.inventoryMenu.containerId));
                }

                this.pending = new ServerRangeInventorySource.PendingPick(item, destination, tick);
                return InventoryPickResult.waiting(item, destination);
            } else {
                return InventoryPickResult.waiting(item, destination);
            }
        } else {
            return InventoryPickResult.missing(item);
        }
    }

    private InventoryPickResult pollPending() {
        if (!this.mc.player.getInventory().getItem(this.pending.hotbarSlot).is(this.pending.item)) {
            return InventoryPickResult.waiting(this.pending.item, this.pending.hotbarSlot);
        }

        InventoryPickResult result = InventoryPickResult.ready(this.pending.item, InteractionHand.MAIN_HAND, this.pending.hotbarSlot);
        this.pending = null;
        return result;
    }

    private static int find(Inventory inventory, Item item) {
        for (int slot = 0; slot <= 35; slot++) {
            if (inventory.getItem(slot).is(item)) {
                return slot;
            }
        }

        return -1;
    }

    private static int findHotbarDestination(Inventory inventory) {
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                return slot;
            }
        }

        return inventory.getSelectedSlot();
    }

    @Override
    public void reset() {
        this.pending = null;
    }

    @Environment(EnvType.CLIENT)
    private record PendingPick(Item item, int hotbarSlot, long startedTick) {
    }
}
