package net.anubis.client.easyplace.builder;

import java.util.function.Predicate;
import net.anubis.client.easyplace.inventory.InventoryCalm;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
final class Hands {
    private static final long SWAP_WAIT_NANOS = 1000000000L;
    private static long swapSentAt = System.nanoTime() - 1000000000L;

    private Hands() {
    }

    static boolean hold(Minecraft mc, LocalPlayer player, Predicate<ItemStack> want) {
        Inventory inventory = player.getInventory();
        if (want.test(player.getMainHandItem())) {
            return true;
        }

        for (int i = 0; i < 9; i++) {
            if (want.test(inventory.getItem(i))) {
                inventory.setSelectedSlot(i);
                return true;
            }
        }

        if (player.containerMenu == player.inventoryMenu && mc.gameMode != null) {
            for (int i = 9; i < 36; i++) {
                if (want.test(inventory.getItem(i))) {
                    if (!InventoryCalm.settle(mc, player)) {
                        return false;
                    }

                    mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, i, inventory.getSelectedSlot(), ClickType.SWAP, player);
                    close(mc, player);
                    return false;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    static boolean has(LocalPlayer player, Predicate<ItemStack> want) {
        Inventory inventory = player.getInventory();

        for (int i = 0; i < 36; i++) {
            if (want.test(inventory.getItem(i))) {
                return true;
            }
        }

        return want.test(player.getOffhandItem());
    }

    static boolean toOffhand(Minecraft mc, LocalPlayer player, Predicate<ItemStack> want) {
        if (player.containerMenu == player.inventoryMenu && mc.gameMode != null && mc.getConnection() != null && player.inventoryMenu.getCarried().isEmpty()) {
            Inventory inventory = player.getInventory();
            if (want.test(player.getMainHandItem())) {
                long now = System.nanoTime();
                if (now - swapSentAt < 1000000000L) {
                    return true;
                }

                swapSentAt = now;
                mc.getConnection().send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                return true;
            } else {
                for (int i = 0; i < 9; i++) {
                    if (want.test(inventory.getItem(i))) {
                        inventory.setSelectedSlot(i);
                        return true;
                    }
                }

                for (int i = 9; i < 36; i++) {
                    if (want.test(inventory.getItem(i))) {
                        if (!InventoryCalm.settle(mc, player)) {
                            return true;
                        }

                        mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, i, 40, ClickType.SWAP, player);
                        close(mc, player);
                        return true;
                    }
                }

                return false;
            }
        } else {
            return false;
        }
    }

    private static void close(Minecraft mc, LocalPlayer player) {
        if (mc.screen == null) {
            player.connection.send(new ServerboundContainerClosePacket(player.inventoryMenu.containerId));
        }
    }
}
