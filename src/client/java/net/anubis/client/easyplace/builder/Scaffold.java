package net.anubis.client.easyplace.builder;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
final class Scaffold {
    private Scaffold() {
    }

    static boolean item(ItemStack stack) {
        return glass(stack) || stack.is(ItemTags.LEAVES);
    }

    static boolean glass(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof TransparentBlock;
    }

    static boolean block(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.getBlock() instanceof TransparentBlock;
    }

    static int count(LocalPlayer player) {
        int count = 0;
        Inventory inventory = player.getInventory();

        for (int i = 0; i < 36; i++) {
            if (item(inventory.getItem(i))) {
                count += inventory.getItem(i).getCount();
            }
        }

        if (item(player.getOffhandItem())) {
            count += player.getOffhandItem().getCount();
        }

        return count;
    }

    static boolean has(LocalPlayer player) {
        return Hands.has(player, Scaffold::item);
    }

    static boolean hold(Minecraft mc, LocalPlayer player) {
        if (Hands.has(player, stack -> stack.is(Items.GLASS))) {
            return Hands.hold(mc, player, stack -> stack.is(Items.GLASS));
        } else {
            return Hands.has(player, stack -> stack.is(ItemTags.LEAVES))
                ? Hands.hold(mc, player, stack -> stack.is(ItemTags.LEAVES))
                : Hands.hold(mc, player, Scaffold::glass);
        }
    }
}
