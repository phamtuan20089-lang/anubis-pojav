package net.anubis.client.easyplace.builder;

import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
final class Eater {
    static final int HUNGRY = 14;
    private static final int EAT_TICKS = 70;
    private final Predicate<ItemStack> food;
    private int ticks;
    private int startFood = -1;

    Eater() {
        this(Eater::edible);
    }

    Eater(Predicate<ItemStack> food) {
        this.food = food;
    }

    static boolean hungry(LocalPlayer player) {
        return player.getFoodData().getFoodLevel() <= 14 || player.getHealth() < player.getMaxHealth() && player.getFoodData().getFoodLevel() < 18;
    }

    static boolean hasFood(LocalPlayer player) {
        return Hands.has(player, Eater::edible);
    }

    static boolean edible(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food != null && food.nutrition() > 0
            ? !stack.is(Items.CHORUS_FRUIT)
                && !stack.is(Items.ROTTEN_FLESH)
                && !stack.is(Items.SPIDER_EYE)
                && !stack.is(Items.POISONOUS_POTATO)
                && !stack.is(Items.PUFFERFISH)
                && !stack.is(Items.SUSPICIOUS_STEW)
                && !stack.is(Items.ENCHANTED_GOLDEN_APPLE)
                && !stack.is(Items.GOLDEN_APPLE)
                && !stack.is(Items.CHICKEN)
            : false;
    }

    Eater.Result tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != null && mc.gameMode != null) {
            if (this.startFood < 0) {
                this.startFood = player.getFoodData().getFoodLevel();
            }

            if (player.getFoodData().getFoodLevel() > this.startFood) {
                this.release(mc);
                return Eater.Result.DONE;
            }

            if (++this.ticks > 70) {
                this.release(mc);
                return Eater.Result.FAILED;
            }

            if (!Hands.hold(mc, player, this.food)) {
                return Eater.Result.RUNNING;
            }

            Aim.turn(mc, player.getYRot(), -75.0F, 22.0F);
            mc.options.keyUse.setDown(player.getXRot() < -60.0F);
            return Eater.Result.RUNNING;
        } else {
            return Eater.Result.FAILED;
        }
    }

    void release(Minecraft mc) {
        mc.options.keyUse.setDown(false);
    }

    @Environment(EnvType.CLIENT)
    enum Result {
        RUNNING,
        DONE,
        FAILED;
    }
}
