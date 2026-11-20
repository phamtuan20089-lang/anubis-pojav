package net.anubis.client.easyplace.builder;

import net.anubis.client.easyplace.util.StateRules;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

@Environment(EnvType.CLIENT)
final class BlockUser {
    private static final int CLICK_LIMIT = 30;
    private static final int ANSWER_TICKS = 12;
    private static final int OPEN_TICKS = 40;
    private static final int SYNC_TICKS = 4;
    private final BlockPos pos;
    private final BlockState expected;
    private final boolean[] disabledSlots;
    private Aim.Shot shot;
    private BlockState before;
    private int phase;
    private int phaseTicks;
    private int clicks;
    private int rounds;
    private boolean lever;

    private BlockUser(BlockPos pos, BlockState expected, boolean[] disabledSlots) {
        this.pos = pos;
        this.expected = expected;
        this.disabledSlots = disabledSlots;
    }

    static BlockUser adjust(BlockPos pos, BlockState expected) {
        return new BlockUser(pos, expected, null);
    }

    static BlockUser crafter(BlockPos pos, boolean[] disabledSlots) {
        return new BlockUser(pos, null, disabledSlots);
    }

    static BlockUser lever(BlockPos pos, BlockState expected) {
        BlockUser user = new BlockUser(pos, expected, null);
        user.lever = true;
        return user;
    }

    BlockPos pos() {
        return this.pos;
    }

    boolean crafter() {
        return this.disabledSlots != null;
    }

    BlockUser.Result tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != null && mc.level != null && mc.gameMode != null) {
            this.phaseTicks++;
            BlockState now = mc.level.getBlockState(this.pos);
            if (this.lever) {
                if (!now.hasProperty(BlockStateProperties.POWERED)) {
                    return BlockUser.Result.FAILED;
                }

                if (now.getValue(BlockStateProperties.POWERED) == this.expected.getValue(BlockStateProperties.POWERED)) {
                    return BlockUser.Result.DONE;
                }
            } else if (this.disabledSlots == null && StateRules.done(now, this.expected)) {
                return BlockUser.Result.DONE;
            }

            switch (this.phase) {
                case 0:
                    if (player.isShiftKeyDown() || player.containerMenu != player.inventoryMenu) {
                        return BlockUser.Result.RUNNING;
                    }

                    this.shot = Aim.at(mc, this.pos);
                    if (this.shot == null) {
                        return BlockUser.Result.FAILED;
                    }

                    emptyHand(player);
                    this.next(1);
                    break;
                case 1:
                    Aim.turn(mc, this.shot.yaw(), this.shot.pitch(), 22.0F);
                    if (Aim.looking(player, this.shot.yaw(), this.shot.pitch()) && this.phaseTicks >= 2) {
                        this.before = mc.level.getBlockState(this.pos);
                        if (!mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, this.shot.hit()).consumesAction()) {
                            return BlockUser.Result.FAILED;
                        }

                        player.swing(InteractionHand.MAIN_HAND);
                        this.clicks++;
                        this.next(this.disabledSlots == null ? 2 : 3);
                        break;
                    }

                    if (this.phaseTicks > 30) {
                        return BlockUser.Result.FAILED;
                    }

                    return BlockUser.Result.RUNNING;
                case 2:
                    if (!mc.level.getBlockState(this.pos).equals(this.before) || this.phaseTicks > 12) {
                        if (this.clicks >= 30) {
                            return BlockUser.Result.FAILED;
                        }

                        this.next(0);
                    }
                    break;
                case 3:
                    if (player.containerMenu instanceof CrafterMenu && this.phaseTicks >= 4) {
                        this.next(4);
                    } else if (this.phaseTicks > 40) {
                        return BlockUser.Result.FAILED;
                    }
                    break;
                case 4:
                    if (!(player.containerMenu instanceof CrafterMenu menu)) {
                        return BlockUser.Result.FAILED;
                    }

                    boolean changed = false;

                    for (int slot = 0; slot < 9; slot++) {
                        if (menu.isSlotDisabled(slot) != this.disabledSlots[slot] && !menu.getSlot(slot).hasItem()) {
                            boolean enable = !this.disabledSlots[slot];
                            menu.setSlotState(slot, enable);
                            mc.gameMode.handleSlotStateChanged(slot, menu.containerId, enable);
                            changed = true;
                        }
                    }

                    this.next(changed ? 5 : 6);
                    break;
                case 5:
                    if (this.phaseTicks >= 12) {
                        this.next(6);
                    }
                    break;
                case 6:
                    boolean right = player.containerMenu instanceof CrafterMenu menu && this.matches(menu);
                    player.closeContainer();
                    if (right) {
                        return BlockUser.Result.DONE;
                    }

                    if (++this.rounds >= 2) {
                        return BlockUser.Result.FAILED;
                    }

                    this.next(0);
                    break;
                default:
                    return BlockUser.Result.FAILED;
            }

            return BlockUser.Result.RUNNING;
        } else {
            return BlockUser.Result.FAILED;
        }
    }

    void close(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != null && this.disabledSlots != null && player.containerMenu instanceof CrafterMenu) {
            player.closeContainer();
        }
    }

    private static void emptyHand(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        if (inventory.getItem(inventory.getSelectedSlot()).getItem() instanceof BlockItem) {
            int fallback = -1;

            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.isEmpty()) {
                    inventory.setSelectedSlot(slot);
                    return;
                }

                if (fallback < 0 && !(stack.getItem() instanceof BlockItem)) {
                    fallback = slot;
                }
            }

            if (fallback >= 0) {
                inventory.setSelectedSlot(fallback);
            }
        }
    }

    private boolean matches(CrafterMenu menu) {
        for (int slot = 0; slot < 9; slot++) {
            if (menu.isSlotDisabled(slot) != this.disabledSlots[slot]) {
                return false;
            }
        }

        return true;
    }

    private void next(int value) {
        this.phase = value;
        this.phaseTicks = 0;
    }

    @Environment(EnvType.CLIENT)
    enum Result {
        RUNNING,
        DONE,
        FAILED;
    }
}
