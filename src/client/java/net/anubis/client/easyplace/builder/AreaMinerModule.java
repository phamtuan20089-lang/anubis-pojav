package net.anubis.client.easyplace.builder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.easyplace.inventory.InventoryCalm;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.MouseInputEvent;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.misc.FreecamModule;
import net.anubis.client.module.setting.ActionSetting;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.StringSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

@Environment(EnvType.CLIENT)
public final class AreaMinerModule extends ClientModule {
    private static final long PLAN_BUDGET_NANOS = 3000000L;
    private static final int PLAN_TARGETS = 400;
    private static final int BREAK_TRIES = 3;
    private static final int PLAN_FAILS = 2;
    private static final int SCAN_TICKS = 10;
    private static final int MEND_TICKS = 1200;
    private static final int MAX_CELLS = 2000000;
    private static final int AREA_COLOR = -11155201;
    private static final int TARGET_COLOR = -20416;
    private static final Predicate<ItemStack> XP = stack -> stack.is(Items.EXPERIENCE_BOTTLE);
    private final StringSetting corner1 = this.addSetting("corner-1", new StringSetting("Corner 1", "").hint("x y z"));
    private final StringSetting corner2 = this.addSetting("corner-2", new StringSetting("Corner 2", "").hint("x y z"));
    private final ActionSetting select = this.addSetting("select-area", new ActionSetting("Select Area With Clicks", this::selectArea));
    private final ActionSetting feet1 = this.addSetting("corner-1-feet", new ActionSetting("Corner 1 At Feet", () -> this.setCorner(this.corner1, false)));
    private final ActionSetting look1 = this.addSetting("corner-1-look", new ActionSetting("Corner 1 At Crosshair", () -> this.setCorner(this.corner1, true)));
    private final ActionSetting feet2 = this.addSetting("corner-2-feet", new ActionSetting("Corner 2 At Feet", () -> this.setCorner(this.corner2, false)));
    private final ActionSetting look2 = this.addSetting("corner-2-look", new ActionSetting("Corner 2 At Crosshair", () -> this.setCorner(this.corner2, true)));
    private final BooleanSetting useShard = this.addSetting("use-shard", new BooleanSetting("Use Shard Pickaxe", true));
    private final BooleanSetting eat = this.addSetting("eat", new BooleanSetting("Eat When Hungry", true));
    private final BooleanSetting mend = this.addSetting("mend", new BooleanSetting("Mend With XP Bottles", true));
    private final NumberSetting mendBelow = this.addSetting("mend-below", new NumberSetting("Mend Below Durability", 150.0F, 20.0F, 1000.0F).step(10.0F));
    private final BooleanSetting pauseInMenus = this.addSetting("pause-in-menus", new BooleanSetting("Pause In Menus", true));
    private final BooleanSetting showArea = this.addSetting("show-area", new BooleanSetting("Show Area", true));
    private final BooleanSetting chatFeedback = this.addSetting("chat-feedback", new BooleanSetting("Chat Feedback", true));
    private final Minecraft mc = Minecraft.getInstance();
    private BlockPos min;
    private BlockPos max;
    private ClientLevel seenLevel;
    private long tick;
    private long nextScan;
    private long noPlanUntil;
    private Breaker breaker;
    private boolean breakerShard;
    private Hop hop;
    private Eater eater;
    private StandPlanner planner;
    private List<StandPlanner.Step> route;
    private int routeIndex;
    private BlockPos routeStart;
    private boolean mending;
    private long mendStarted;
    private int mined;
    private int startCount;
    private int layer = Integer.MIN_VALUE;
    private final List<BlockPos> layerCells = new ArrayList<>();
    private final Map<Long, Integer> breakTries = new HashMap<>();
    private final Map<Long, Integer> planFails = new HashMap<>();
    private final Set<Long> unreachable = new HashSet<>();
    private final Set<Long> holding = new HashSet<>();
    private final Map<String, Long> lastFeedback = new HashMap<>();
    private int picking;
    private BlockPos firstPick;

    public AreaMinerModule() {
        super(
            "area-miner",
            "Area Miner",
            "Mines out everything between two corners, top layer first, walking and turning by hand like a player. A Shard Pickaxe cuts 3x3 wherever the whole cut stays inside the area; blocks holding back lava or water stay.",
            ModuleCategory.MISC
        );
        this.usesNotifications();
        this.mendBelow.visibleWhen(this.mend::get);
    }

    @Override
    public boolean persistEnabled() {
        return false;
    }

    @Override
    protected void onEnable() {
        LocalPlayer player = this.mc.player;
        if (player != null && this.mc.level != null) {
            this.on(TickEvent.TYPE, Stage.PRE, event -> this.step());
            this.on(Render3DEvent.TYPE, Stage.POST, this::render);
            this.on(MouseInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onMouse);
            BlockPos a = parse(this.corner1.get());
            BlockPos b = parse(this.corner2.get());
            if (a != null && b != null) {
                if (!this.begin(a, b)) {
                    this.requestDisable();
                }
            } else {
                this.startPicking();
            }
        } else {
            this.say(ModuleChat.body().warn("Join a world first."));
            this.requestDisable();
        }
    }

    private boolean begin(BlockPos a, BlockPos b) {
        BlockPos low = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos high = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        long volume = (long)(high.getX() - low.getX() + 1) * (high.getY() - low.getY() + 1) * (high.getZ() - low.getZ() + 1);
        if (volume > 2000000L) {
            this.say(ModuleChat.body().warn("The area is too big").text(" (" + volume + " blocks, at most 2000000)."));
            return false;
        } else {
            this.reset();
            this.min = low;
            this.max = high;
            this.seenLevel = this.mc.level;
            this.startCount = this.countLeft(this.mc.level);
            this.say(
                ModuleChat.body().text("Mining " + this.startCount + " blocks between " + this.min.toShortString() + " and " + this.max.toShortString() + ".")
            );
            return true;
        }
    }

    private void startPicking() {
        this.release();
        this.min = null;
        this.max = null;
        this.firstPick = null;
        this.picking = 1;
        this.say(ModuleChat.body().text("Hit the first corner block").text(" (or type both corners in the settings)."));
    }

    private void selectArea() {
        this.corner1.set("");
        this.corner2.set("");
        if (this.isEnabled()) {
            this.startPicking();
        } else {
            this.setEnabled(true);
        }
    }

    private void onMouse(MouseInputEvent event) {
        if (this.picking != 0 && event.button == 0 && event.action == 1) {
            if (this.mc.screen == null && this.mc.player != null && this.mc.level != null) {
                event.setCancelled(true);
                BlockPos pos = this.aimedBlock();
                if (pos == null) {
                    this.say(ModuleChat.body().warn("No block there").text(", look at a block."));
                } else {
                    String text = pos.getX() + " " + pos.getY() + " " + pos.getZ();
                    if (this.picking == 1) {
                        this.firstPick = pos;
                        this.corner1.set(text);
                        this.picking = 2;
                        this.say(ModuleChat.body().text("Corner 1 set to " + pos.toShortString() + ". Hit the second corner block."));
                    } else {
                        this.corner2.set(text);
                        this.say(ModuleChat.body().text("Corner 2 set to " + pos.toShortString() + "."));
                        if (this.begin(this.firstPick, pos)) {
                            this.picking = 0;
                        } else {
                            this.say(ModuleChat.body().text("Hit the second corner block again."));
                        }
                    }
                }
            }
        }
    }

    private BlockPos aimedBlock() {
        LocalPlayer player = this.mc.player;
        if (player != null && this.mc.level != null) {
            FreecamModule freecam = ModuleRegistry.get(FreecamModule.class);
            Vec3 origin;
            Vec3 direction;
            if (freecam != null && freecam.isEnabled()) {
                Camera camera = this.mc.gameRenderer.getMainCamera();
                origin = camera.position();
                direction = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
            } else {
                origin = player.getEyePosition(1.0F);
                direction = player.getViewVector(1.0F);
            }

            double reach = this.mc.options.getEffectiveRenderDistance() * 16.0;
            BlockHitResult hit = this.mc.level.clip(new ClipContext(origin, origin.add(direction.scale(reach)), Block.OUTLINE, Fluid.NONE, player));
            return hit.getType() == Type.BLOCK ? hit.getBlockPos() : null;
        } else {
            return null;
        }
    }

    @Override
    protected void onDisable() {
        this.release();
        this.planner = null;
        this.route = null;
        this.picking = 0;
        this.firstPick = null;
    }

    private void reset() {
        this.tick = 0L;
        this.nextScan = 0L;
        this.noPlanUntil = 0L;
        this.breaker = null;
        this.hop = null;
        this.eater = null;
        this.planner = null;
        this.route = null;
        this.mending = false;
        this.mined = 0;
        this.layer = Integer.MIN_VALUE;
        this.layerCells.clear();
        this.breakTries.clear();
        this.planFails.clear();
        this.unreachable.clear();
        this.holding.clear();
        this.lastFeedback.clear();
    }

    private void step() {
        LocalPlayer player = this.mc.player;
        ClientLevel level = this.mc.level;
        if (player != null && level != null && this.mc.gameMode != null) {
            if (this.picking == 0 && this.min != null) {
                this.tick++;
                if (level != this.seenLevel || !player.isAlive()) {
                    this.say(ModuleChat.body().warn("Stopped").text(", the world changed or you died."));
                    this.requestDisable();
                } else if (this.mc.screen != null && this.pauseInMenus.get()) {
                    this.release();
                } else if (this.eater != null) {
                    if (this.eater.tick(this.mc) != Eater.Result.RUNNING) {
                        this.eater = null;
                    }
                } else if (this.mending) {
                    this.mend(player);
                } else if (this.breaker != null) {
                    BlockState state = level.getBlockState(this.breaker.pos());
                    if (state.isAir() || Hands.hold(this.mc, player, toolFor(player, state, this.breakerShard))) {
                        Breaker.Result result = this.breaker.tick(this.mc);
                        if (result != Breaker.Result.RUNNING) {
                            BlockPos pos = this.breaker.pos();
                            this.breaker = null;
                            if (minable(level, pos) && this.breakTries.merge(pos.asLong(), 1, Integer::sum) >= 3) {
                                this.unreachable.add(pos.asLong());
                                this.feedback("gave-up", "Could not mine " + pos.toShortString() + ", leaving it", 40);
                            }

                            this.nextScan = 0L;
                        }
                    }
                } else if (this.hop != null) {
                    Hop.Result result = this.hop.tick(this.mc);
                    if (result != Hop.Result.RUNNING) {
                        this.hop = null;
                        if (result == Hop.Result.FAILED) {
                            this.route = null;
                            this.noPlanUntil = this.tick + 20L;
                        }
                    }
                } else if (this.eat.get() && Eater.hungry(player) && Eater.hasFood(player) && InventoryCalm.still(player)) {
                    this.release();
                    this.eater = new Eater();
                } else if (this.mendDue(player)) {
                    this.mending = true;
                    this.mendStarted = this.tick;
                    this.feedback("mend", "Tool is worn, mending it with XP bottles", 200);
                } else if (this.route != null) {
                    this.followRoute(level, player);
                } else if (this.planner != null) {
                    this.followPlanner();
                } else {
                    if (this.tick >= this.nextScan) {
                        this.nextScan = this.tick + 10L;
                        if (!this.pickLayer(level)) {
                            this.finish();
                            return;
                        }
                    }

                    this.dig(level, player);
                }
            }
        }
    }

    private boolean pickLayer(ClientLevel level) {
        this.layerCells.clear();
        MutableBlockPos cursor = new MutableBlockPos();

        for (int y = this.max.getY(); y >= this.min.getY(); y--) {
            for (int x = this.min.getX(); x <= this.max.getX(); x++) {
                for (int z = this.min.getZ(); z <= this.max.getZ(); z++) {
                    cursor.set(x, y, z);
                    long key = cursor.asLong();
                    if (!this.unreachable.contains(key) && level.hasChunkAt(cursor) && minable(level, cursor)) {
                        if (this.holdsFluid(level, cursor)) {
                            if (this.holding.add(key)) {
                                this.feedback("fluid", "Leaving " + cursor.toShortString() + ", it holds back lava or water", 100);
                            }
                        } else {
                            this.holding.remove(key);
                            this.layerCells.add(cursor.immutable());
                        }
                    }
                }
            }

            if (!this.layerCells.isEmpty()) {
                if (y != this.layer) {
                    this.layer = y;
                    this.planFails.clear();
                    this.feedback("layer", "Layer Y=" + y + ", " + this.layerCells.size() + (this.layerCells.size() == 1 ? " block" : " blocks"), 1);
                }

                return true;
            }
        }

        return false;
    }

    private void dig(ClientLevel level, LocalPlayer player) {
        if (!this.layerCells.isEmpty()) {
            Vec3 eye = player.getEyePosition();
            double reach = player.blockInteractionRange() - 0.3;
            List<BlockPos> near = new ArrayList<>();

            for (BlockPos pos : this.layerCells) {
                if (pos.getCenter().distanceTo(eye) <= reach && minable(level, pos)) {
                    near.add(pos);
                }
            }

            BlockPos under = player.blockPosition().below();
            near.sort(Comparator.<BlockPos>comparingInt(posx -> posx.equals(under) ? 1 : 0).thenComparingDouble(posx -> posx.getCenter().distanceToSqr(eye)));

            for (BlockPos pos : near) {
                if (Aim.at(this.mc, pos) != null) {
                    BlockState state = level.getBlockState(pos);
                    this.breakerShard = this.useShard.get() && this.cutInside(level, pos);
                    if (!Hands.hold(this.mc, player, toolFor(player, state, this.breakerShard))) {
                        return;
                    }

                    this.breaker = new Breaker(pos, true);
                    return;
                }
            }

            if (this.tick >= this.noPlanUntil) {
                List<StandPlanner.Target> targets = new ArrayList<>();
                Vec3 at = player.position();
                List<BlockPos> sorted = new ArrayList<>(this.layerCells);
                sorted.sort(Comparator.comparingDouble(posx -> posx.distToCenterSqr(at)));

                for (BlockPos pos : sorted) {
                    if (targets.size() >= 400) {
                        break;
                    }

                    targets.add(new StandPlanner.Target(pos, level.getBlockState(pos), StandPlanner.Use.BREAK));
                }

                this.planner = new StandPlanner(level, player, targets, feet -> false, null, null, null, null);
                this.followPlanner();
            }
        }
    }

    private void followPlanner() {
        StandPlanner.Status status = this.planner.step(System.nanoTime() + 3000000L);
        if (status != StandPlanner.Status.RUNNING) {
            StandPlanner done = this.planner;
            this.planner = null;
            if ((status == StandPlanner.Status.FOUND || status == StandPlanner.Status.APPROACH) && !done.route().isEmpty()) {
                this.route = done.route();
                this.routeIndex = 0;
                this.routeStart = this.mc.player == null ? null : this.mc.player.blockPosition();
            } else {
                this.noPlanUntil = this.tick + 40L;
                boolean gaveUp = false;

                for (BlockPos pos : this.layerCells) {
                    if (this.planFails.merge(pos.asLong(), 1, Integer::sum) >= 2) {
                        this.unreachable.add(pos.asLong());
                        gaveUp = true;
                    }
                }

                if (gaveUp) {
                    this.feedback("unreachable", "No spot gets at the rest of layer Y=" + this.layer + ", leaving those blocks", 40);
                }

                this.nextScan = 0L;
            }
        }
    }

    private void followRoute(ClientLevel level, LocalPlayer player) {
        if (this.routeIndex >= this.route.size()) {
            this.route = null;
            this.nextScan = 0L;
        } else if (player.onGround()) {
            BlockPos expected = this.routeIndex == 0 ? this.routeStart : this.route.get(this.routeIndex - 1).pos();
            BlockPos at = player.blockPosition();
            if (expected == null || at.getX() == expected.getX() && at.getZ() == expected.getZ() && Math.abs(at.getY() - expected.getY()) <= 1) {
                StandPlanner.Step next = this.route.get(this.routeIndex++);
                if (next.move() != StandPlanner.Move.WALK && next.move() != StandPlanner.Move.LEAP) {
                    this.route = null;
                    this.noPlanUntil = this.tick + 40L;
                } else {
                    boolean through = next.move() == StandPlanner.Move.WALK
                        && this.routeIndex < this.route.size()
                        && this.route.get(this.routeIndex).move() == StandPlanner.Move.WALK;
                    this.hop = new Hop(next.pos(), through, next.move() == StandPlanner.Move.LEAP);
                    if (next.move() == StandPlanner.Move.WALK && (Walker.risky(level, at) || Walker.risky(level, next.pos()))) {
                        if (next.pos().getY() >= at.getY()) {
                            this.hop.careful();
                        } else {
                            this.hop.slow();
                        }
                    }
                }
            } else {
                this.release();
                this.route = null;
                this.noPlanUntil = this.tick + 10L;
            }
        }
    }

    private static boolean minable(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !(state.getBlock() instanceof LiquidBlock) ? state.getDestroySpeed(level, pos) >= 0.0F : false;
    }

    private boolean holdsFluid(ClientLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).getFluidState().isEmpty()) {
            return true;
        }

        for (Direction side : Direction.values()) {
            if (side != Direction.DOWN && !level.getFluidState(pos.relative(side)).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private boolean cutInside(ClientLevel level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos cell = pos.offset(dx, dy, dz);
                    if (!this.inside(cell)) {
                        return false;
                    }

                    if (minable(level, cell) && this.holdsFluid(level, cell)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private boolean inside(BlockPos pos) {
        return this.min != null
            && pos.getX() >= this.min.getX()
            && pos.getX() <= this.max.getX()
            && pos.getY() >= this.min.getY()
            && pos.getY() <= this.max.getY()
            && pos.getZ() >= this.min.getZ()
            && pos.getZ() <= this.max.getZ();
    }

    private static Predicate<ItemStack> toolFor(LocalPlayer player, BlockState state, boolean shard) {
        Inventory inventory = player.getInventory();
        ItemStack best = ItemStack.EMPTY;
        float bestSpeed = 1.0F;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && isShard(stack) == shard) {
                float speed = stack.getDestroySpeed(state);
                if (speed > bestSpeed || speed == bestSpeed && !best.isEmpty() && stack.isCorrectToolForDrops(state) && !best.isCorrectToolForDrops(state)) {
                    best = stack;
                    bestSpeed = speed;
                }
            }
        }

        if (best.isEmpty()) {
            return stackx -> !isShard(stackx);
        }

        ItemStack chosen = best;
        return stackx -> ItemStack.isSameItemSameComponents(stackx, chosen);
    }

    static boolean isShard(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES) && TunnelMinerModule.shardName(stack);
    }

    private boolean mendDue(LocalPlayer player) {
        if (this.mend.get() && (Hands.has(player, XP) || XP.test(player.getOffhandItem()))) {
            ItemStack held = player.getMainHandItem();
            return held.isDamageableItem() && mendable(held) ? held.getMaxDamage() - held.getDamageValue() < this.mendBelow.get() : false;
        } else {
            return false;
        }
    }

    private void mend(LocalPlayer player) {
        ItemStack held = player.getMainHandItem();
        boolean repaired = held.isDamageableItem() && held.getDamageValue() == 0;
        boolean out = !XP.test(player.getOffhandItem()) && !Hands.has(player, XP);
        if (repaired || out || this.tick - this.mendStarted > 1200L || !held.isDamageableItem()) {
            this.mc.options.keyUse.setDown(false);
            this.mending = false;
            if (repaired) {
                this.feedback("mended", "Tool mended", 200);
            } else if (out) {
                this.feedback("no-xp", "Out of XP bottles, mining on", 200);
            }
        } else if (!XP.test(player.getOffhandItem())) {
            this.mc.options.keyUse.setDown(false);
            if (this.tick % 5L == 0L && InventoryCalm.still(player)) {
                Hands.toOffhand(this.mc, player, XP);
            }
        } else {
            Aim.turn(this.mc, player.getYRot(), 80.0F, 22.0F);
            this.mc.options.keyUse.setDown(player.getXRot() > 60.0F);
        }
    }

    private static boolean mendable(ItemStack stack) {
        return stack.getEnchantments().keySet().stream().anyMatch(enchantment -> enchantment.is(Enchantments.MENDING));
    }

    private int countLeft(ClientLevel level) {
        int count = 0;
        MutableBlockPos cursor = new MutableBlockPos();

        for (int y = this.min.getY(); y <= this.max.getY(); y++) {
            for (int x = this.min.getX(); x <= this.max.getX(); x++) {
                for (int z = this.min.getZ(); z <= this.max.getZ(); z++) {
                    cursor.set(x, y, z);
                    if (level.hasChunkAt(cursor) && minable(level, cursor)) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    private void finish() {
        this.release();
        ClientLevel level = this.mc.level;
        int left = level == null ? 0 : this.countLeft(level);
        ModuleChat.Body body = ModuleChat.body().good("Area cleared").text(", " + Math.max(0, this.startCount - left) + " blocks mined.");
        if (!this.unreachable.isEmpty()) {
            body.text(" ")
                .warn(this.unreachable.size() + (this.unreachable.size() == 1 ? " block" : " blocks") + " could not be reached")
                .text(" (" + some(this.unreachable) + ").");
        }

        if (!this.holding.isEmpty()) {
            body.text(
                " "
                    + this.holding.size()
                    + (this.holding.size() == 1 ? " block was" : " blocks were")
                    + " left to hold back lava or water ("
                    + some(this.holding)
                    + ")."
            );
        }

        this.say(body);
        this.requestDisable();
    }

    private static String some(Set<Long> keys) {
        List<String> shown = new ArrayList<>();

        for (long key : keys) {
            if (shown.size() == 4) {
                break;
            }

            shown.add(BlockPos.of(key).toShortString());
        }

        return String.join("; ", shown) + (keys.size() > 4 ? "; ..." : "");
    }

    private void render(Render3DEvent event) {
        if (this.picking != 0) {
            BlockPos aimed = this.aimedBlock();
            BlockPos from = this.picking == 2 ? this.firstPick : aimed;
            BlockPos to = aimed != null ? aimed : from;
            if (from != null) {
                Gizmos.cuboid(
                    new AABB(
                        Math.min(from.getX(), to.getX()),
                        Math.min(from.getY(), to.getY()),
                        Math.min(from.getZ(), to.getZ()),
                        Math.max(from.getX(), to.getX()) + 1,
                        Math.max(from.getY(), to.getY()) + 1,
                        Math.max(from.getZ(), to.getZ()) + 1
                    ),
                    GizmoStyle.stroke(-11155201, 2.0F)
                );
                if (this.picking == 2) {
                    Gizmos.cuboid(new AABB(this.firstPick), GizmoStyle.stroke(-20416, 2.0F));
                }
            }
        } else if (this.showArea.get() && this.min != null) {
            Gizmos.cuboid(
                new AABB(this.min.getX(), this.min.getY(), this.min.getZ(), this.max.getX() + 1, this.max.getY() + 1, this.max.getZ() + 1),
                GizmoStyle.stroke(-11155201, 2.0F)
            );
            if (this.breaker != null) {
                Gizmos.cuboid(this.breaker.pos(), GizmoStyle.stroke(-20416, 2.0F));
            }
        }
    }

    private void setCorner(StringSetting corner, boolean looking) {
        LocalPlayer player = this.mc.player;
        if (player != null) {
            BlockPos pos = player.blockPosition();
            if (looking) {
                if (!(this.mc.hitResult instanceof BlockHitResult hit) || hit.getType() != Type.BLOCK) {
                    this.say(ModuleChat.body().warn("Look at a block first."));
                    return;
                }

                pos = hit.getBlockPos();
            }

            corner.set(pos.getX() + " " + pos.getY() + " " + pos.getZ());
            this.say(ModuleChat.body().text(corner.name() + " set to " + pos.toShortString() + "."));
        }
    }

    private static BlockPos parse(String text) {
        String[] parts = text.trim().replace(",", " ").split("\\s+");
        if (parts.length != 3) {
            return null;
        }

        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void release() {
        if (this.breaker != null) {
            this.breaker.release(this.mc);
        }

        this.breaker = null;
        if (this.hop != null) {
            this.hop.release(this.mc);
        }

        this.hop = null;
        if (this.eater != null) {
            this.eater.release(this.mc);
        }

        this.eater = null;
        this.mending = false;
        this.mc.options.keyUse.setDown(false);
        this.mc.options.keyUp.setDown(false);
        this.mc.options.keyDown.setDown(false);
        this.mc.options.keyLeft.setDown(false);
        this.mc.options.keyRight.setDown(false);
        this.mc.options.keyJump.setDown(false);
        this.mc.options.keySprint.setDown(false);
        this.mc.options.keyShift.setDown(false);
        if (this.mc.gameMode != null) {
            this.mc.gameMode.stopDestroyBlock();
        }
    }

    private void say(ModuleChat.Body body) {
        ModuleChat.send(this, body);
    }

    private void feedback(String key, String message, int minimumIntervalTicks) {
        if (this.chatFeedback.get()) {
            long last = this.lastFeedback.getOrDefault(key, -4611686018427387904L);
            if (this.tick - last >= minimumIntervalTicks) {
                this.lastFeedback.put(key, this.tick);
                ModuleChat.send(this, message);
            }
        }
    }
}
