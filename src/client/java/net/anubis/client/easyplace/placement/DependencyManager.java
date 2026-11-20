package net.anubis.client.easyplace.placement;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.util.LayerRange;
import java.util.function.LongPredicate;
import net.anubis.client.easyplace.hopper.HopperPlacementHandler;
import net.anubis.client.easyplace.mixin.InvokerBlockItem;
import net.anubis.client.easyplace.util.BlockStateUtil;
import net.anubis.client.easyplace.util.PlacementUtil;
import net.anubis.client.easyplace.util.StateRules;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.VoxelShape;

@Environment(EnvType.CLIENT)
public final class DependencyManager {
    private static final int SEARCH_RADIUS = 5;
    private final Minecraft mc;
    private final HopperPlacementHandler hopperHandler = new HopperPlacementHandler();
    private BuildScope currentScope;
    private double reachSpare;
    private double feetY = Double.NaN;
    private boolean builderEyes;
    private static final double BUILDER_SPARE = 0.3;
    private static final double REACH_SPARE = 0.6499999999999999;
    private static final double STEADY_SHIFT = 0.02;
    private static final float[][] STEADY_TURNS = new float[][]{{0.5F, 0.0F}, {-0.5F, 0.0F}, {0.0F, 0.5F}, {0.0F, -0.5F}};

    public DependencyManager(Minecraft mc) {
        this.mc = mc;
    }

    public PlacementTarget searchForPlaceables(
        WorldSchematic schematicWorld,
        boolean hopperForceFacing,
        boolean fovFilter,
        float fovDegrees,
        double maxYawChange,
        float lastYawDelta,
        BuildScope scope,
        LongPredicate held
    ) {
        if (this.mc.player != null && this.mc.level != null && schematicWorld != null) {
            BlockPos center = this.mc.player.blockPosition();
            Vec3 eyePos = new Vec3(this.mc.player.getX(), this.mc.player.getEyeY(), this.mc.player.getZ());
            this.feetY = this.mc.player.getY();
            this.builderEyes = scope != null;
            this.reachSpare = scope != null ? 0.3 : 0.0;
            float currentYaw = this.mc.player.getYRot();
            PlacementTarget best = null;
            double bestScore = Double.MAX_VALUE;

            for (int dx = -5; dx <= 5; dx++) {
                for (int dy = -5; dy <= 5; dy++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        int x = center.getX() + dx;
                        int y = center.getY() + dy;
                        int z = center.getZ() + dz;
                        BlockPos pos = new BlockPos(x, y, z);
                        if ((scope == null || scope.allows(pos)) && !held.test(pos.asLong())) {
                            BlockState expected = schematicWorld.getBlockState(pos);
                            if (!expected.isAir() && BlockStateUtil.isPrimaryPlacementState(expected) && (scope != null || this.isInRenderLayer(pos))) {
                                BlockState current = this.mc.level.getBlockState(pos);
                                boolean secondSlab = StateRules.slabHalf(current, expected);
                                if ((secondSlab || isReplaceable(current))
                                    && !StateRules.placed(current, expected)
                                    && (secondSlab || !this.mc.player.getBoundingBox().intersects(new AABB(pos)))) {
                                    this.currentScope = scope;
                                    PlacementTarget placement = secondSlab
                                        ? this.secondSlab(expected, current, pos, eyePos, scope != null)
                                        : this.possibleToPlace(expected, x, y, z, eyePos, hopperForceFacing, scope != null);
                                    if (placement != null) {
                                        if (fovFilter) {
                                            double fdx = x + 0.5 - eyePos.x;
                                            double fdy = y + 0.5 - eyePos.y;
                                            double fdz = z + 0.5 - eyePos.z;
                                            double distSq = fdx * fdx + fdy * fdy + fdz * fdz;
                                            if (distSq < 1.0E-4) {
                                                continue;
                                            }

                                            double yawRad = Math.toRadians(currentYaw);
                                            double pitchRad = Math.toRadians(this.mc.player.getXRot());
                                            double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
                                            double lookY = -Math.sin(pitchRad);
                                            double lookZ = Math.cos(yawRad) * Math.cos(pitchRad);
                                            double dot = (fdx * lookX + fdy * lookY + fdz * lookZ) / Math.sqrt(distSq);
                                            dot = Math.max(-1.0, Math.min(1.0, dot));
                                            double angleDeg = Math.toDegrees(Math.acos(dot));
                                            if (angleDeg > fovDegrees * 0.5) {
                                                continue;
                                            }
                                        }

                                        double yawDiff = Math.abs(Mth.wrapDegrees(placement.yaw() - currentYaw));
                                        if (!(yawDiff > maxYawChange)) {
                                            double dist = eyePos.distanceToSqr(new Vec3(x + 0.5, y + 0.5, z + 0.5));
                                            double score = yawDiff * 2.0 + dist * 0.5;
                                            if (scope != null) {
                                                score = yawDiff * 0.2 - Math.sqrt(dist) * 20.0 + (y - center.getY() + 5) * scope.lowWeight();
                                            }

                                            if (lastYawDelta != 0.0F) {
                                                double candidateDelta = Mth.wrapDegrees(placement.yaw() - currentYaw);
                                                if (Math.signum(candidateDelta) == -Math.signum(lastYawDelta) && Math.abs(candidateDelta) > 10.0) {
                                                    score += Math.abs(candidateDelta) * 5.0;
                                                }
                                            }

                                            if (score < bestScore) {
                                                bestScore = score;
                                                best = placement;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return best;
        } else {
            return null;
        }
    }

    private PlacementTarget possibleToPlace(BlockState toPlace, int x, int y, int z, Vec3 eyePos, boolean hopperForceFacing, boolean wholeInventory) {
        BlockPos targetPos = new BlockPos(x, y, z);
        boolean skipChestType = this.partnerMissing(toPlace, targetPos);
        int slot = this.findItem(toPlace, wholeInventory);
        if (slot < 0) {
            return null;
        }

        for (Direction against : Direction.values()) {
            BlockPos placeAgainstPos = targetPos.relative(against);
            BlockState placeAgainstState = this.mc.level.getBlockState(placeAgainstPos);
            if (!isReplaceable(placeAgainstState) && toPlace.canSurvive(this.mc.level, targetPos)) {
                Direction clickedFace = against.getOpposite();
                if (!hopperForceFacing || this.hopperHandler.faceCanProduceExpectedFacing(toPlace, clickedFace)) {
                    boolean sneak = !placeAgainstPos.equals(targetPos) && Clicks.usesClick(this.mc.level, placeAgainstPos, this.wholeInventory(slot));
                    boolean visible = PlacementUtil.isFaceVisibleToEye(placeAgainstPos, clickedFace, this.builderEye(eyePos, sneak))
                        || this.builderEyes && !sneak && PlacementUtil.isFaceVisibleToEye(placeAgainstPos, clickedFace, this.builderEye(eyePos, true));
                    if (visible) {
                        VoxelShape shape = placeAgainstState.getShape(this.mc.level, placeAgainstPos);
                        if (!shape.isEmpty()) {
                            AABB aabb = shape.bounds();

                            for (Vec3 multiplier : aabbSideMultipliers(against)) {
                                double placeX = placeAgainstPos.getX() + aabb.minX * multiplier.x + aabb.maxX * (1.0 - multiplier.x);
                                double placeY = placeAgainstPos.getY() + aabb.minY * multiplier.y + aabb.maxY * (1.0 - multiplier.y);
                                double placeZ = placeAgainstPos.getZ() + aabb.minZ * multiplier.z + aabb.maxZ * (1.0 - multiplier.z);
                                Vec3 cursor = new Vec3(placeX, placeY, placeZ);
                                PlacementTarget target = this.tryAim(toPlace, targetPos, placeAgainstPos, clickedFace, cursor, eyePos, slot, skipChestType);
                                if (target != null) {
                                    return target;
                                }
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    public boolean placeableFrom(BlockState expected, BlockPos pos, Vec3 eyePos, boolean hopperForceFacing) {
        if (this.mc.player != null && this.mc.level != null) {
            BlockState current = this.mc.level.getBlockState(pos);
            this.reachSpare = 0.6499999999999999;
            double wasFeet = this.feetY;
            boolean wasBuilder = this.builderEyes;
            this.feetY = eyePos.y - this.mc.player.getEyeHeight(Pose.STANDING);
            this.builderEyes = true;

            try {
                if (StateRules.slabHalf(current, expected)) {
                    return this.secondSlab(expected, current, pos, eyePos, true) != null;
                } else {
                    return isReplaceable(current) && !StateRules.placed(current, expected)
                        ? this.possibleToPlace(expected, pos.getX(), pos.getY(), pos.getZ(), eyePos, hopperForceFacing, true) != null
                        : false;
                }
            } finally {
                this.reachSpare = 0.0;
                this.feetY = wasFeet;
                this.builderEyes = wasBuilder;
            }
        } else {
            return false;
        }
    }

    private boolean partnerMissing(BlockState toPlace, BlockPos targetPos) {
        if (!toPlace.hasProperty(BlockStateProperties.CHEST_TYPE)) {
            return true;
        }

        ChestType type = toPlace.getValue(BlockStateProperties.CHEST_TYPE);
        if (type == ChestType.SINGLE) {
            return true;
        }

        Direction facing = toPlace.getValue(BlockStateProperties.HORIZONTAL_FACING);
        Direction partnerDir = type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
        BlockState partnerState = this.mc.level.getBlockState(targetPos.relative(partnerDir));
        return !partnerState.hasProperty(BlockStateProperties.CHEST_TYPE)
            || partnerState.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE
            || partnerState.getValue(BlockStateProperties.HORIZONTAL_FACING) != facing;
    }

    public boolean verify(BlockState desired, BlockPos targetPos, BlockHitResult hit, float yaw, float pitch, int slot, boolean sneaking) {
        return this.mc.player != null && this.mc.level != null && slot >= 0
            ? this.wouldPlace(desired, targetPos, hit, new float[]{yaw, pitch}, slot, this.partnerMissing(desired, targetPos), sneaking)
            : false;
    }

    public String wouldBe(BlockPos targetPos, BlockHitResult hit, float yaw, float pitch, int slot, boolean sneaking) {
        if (this.mc.player != null && this.mc.level != null && slot >= 0) {
            LocalPlayer player = this.mc.player;
            Input keys = player.input.keyPresses;
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof BlockItem blockItem) {
                float originalYaw = player.getYRot();
                float originalPitch = player.getXRot();
                player.setYRot(yaw);
                player.setXRot(pitch);
                player.input.keyPresses = new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), keys.jump(), sneaking, keys.sprint());

                try {
                    BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
                    if (!context.canPlace()) {
                        return "cannot place";
                    } else {
                        return !context.getClickedPos().equals(targetPos)
                            ? "goes to " + context.getClickedPos().toShortString()
                            : ((InvokerBlockItem)blockItem).easyplace$getPlacementState(context) + " facing " + context.getHorizontalDirection();
                    }
                } catch (Exception e) {
                    return e.toString();
                } finally {
                    player.setYRot(originalYaw);
                    player.setXRot(originalPitch);
                    player.input.keyPresses = keys;
                }
            } else {
                return "no block item";
            }
        } else {
            return "?";
        }
    }

    private PlacementTarget secondSlab(BlockState expected, BlockState current, BlockPos pos, Vec3 eyePos, boolean wholeInventory) {
        int slot = this.findItem(expected, wholeInventory);
        if (slot < 0) {
            return null;
        }

        boolean bottom = current.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.BOTTOM;
        Direction face = bottom ? Direction.UP : Direction.DOWN;
        double faceY = pos.getY() + 0.5;
        if (bottom ? !(eyePos.y <= faceY + 0.05) : !(eyePos.y >= faceY - 0.05)) {
            for (double[] offset : new double[][]{{0.5, 0.5}, {0.25, 0.5}, {0.75, 0.5}, {0.5, 0.25}, {0.5, 0.75}}) {
                Vec3 cursor = new Vec3(pos.getX() + offset[0], faceY, pos.getZ() + offset[1]);
                PlacementTarget target = this.tryAim(expected, pos, pos, face, cursor, eyePos, slot, true);
                if (target != null) {
                    return target;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private PlacementTarget tryAim(
        BlockState toPlace, BlockPos targetPos, BlockPos againstPos, Direction clickedFace, Vec3 cursor, Vec3 eyePos, int slot, boolean skipChestType
    ) {
        boolean supportSneak = !againstPos.equals(targetPos) && Clicks.usesClick(this.mc.level, againstPos, this.wholeInventory(slot));
        PlacementTarget target = this.aimWith(toPlace, targetPos, againstPos, clickedFace, cursor, eyePos, slot, skipChestType, supportSneak, false);
        if (target != null) {
            return target;
        } else {
            return supportSneak || !(toPlace.getBlock() instanceof ChestBlock) && !this.builderEyes
                ? null
                : this.aimWith(toPlace, targetPos, againstPos, clickedFace, cursor, eyePos, slot, skipChestType, true, true);
        }
    }

    private PlacementTarget aimWith(
        BlockState toPlace,
        BlockPos targetPos,
        BlockPos againstPos,
        Direction clickedFace,
        Vec3 cursor,
        Vec3 eyePos,
        int slot,
        boolean skipChestType,
        boolean sneaking,
        boolean sneakFlag
    ) {
        Vec3 eye = this.builderEye(eyePos, sneaking);
        float[] rot = PlacementUtil.calcRotation(eye, cursor);
        double reach = this.mc.player.blockInteractionRange() - this.reachSpare;
        BlockHitResult result = PlacementUtil.rayTraceFrom(this.mc.player, eye, rot[0], rot[1], reach);
        if (result == null || result.getType() != Type.BLOCK) {
            return null;
        } else if (!result.getBlockPos().equals(againstPos) || result.getDirection() != clickedFace) {
            return null;
        } else if (!this.wouldPlace(toPlace, targetPos, result, rot, slot, skipChestType, sneaking)) {
            return null;
        } else {
            return !this.steady(toPlace, targetPos, result, rot, slot, skipChestType, sneaking)
                ? null
                : new PlacementTarget(targetPos, againstPos, clickedFace, clickedFace, slot, rot[0], rot[1], 0, result.getLocation(), sneakFlag);
        }
    }

    private boolean steady(BlockState toPlace, BlockPos targetPos, BlockHitResult result, float[] rot, int slot, boolean skipChestType, boolean sneaking) {
        Vec3 at = result.getLocation();
        Direction face = result.getDirection();

        for (Direction side : Direction.values()) {
            if (side.getAxis() != face.getAxis()) {
                Vec3 moved = at.add(side.getStepX() * 0.02, side.getStepY() * 0.02, side.getStepZ() * 0.02);
                BlockHitResult nudged = new BlockHitResult(moved, face, result.getBlockPos(), result.isInside());
                if (!this.wouldPlace(toPlace, targetPos, nudged, rot, slot, skipChestType, sneaking)) {
                    return false;
                }
            }
        }

        for (float[] turn : STEADY_TURNS) {
            if (!this.wouldPlace(toPlace, targetPos, result, new float[]{rot[0] + turn[0], rot[1] + turn[1]}, slot, skipChestType, sneaking)) {
                return false;
            }
        }

        return true;
    }

    private Vec3 builderEye(Vec3 eyePos, boolean sneaking) {
        return this.builderEyes && !Double.isNaN(this.feetY)
            ? new Vec3(eyePos.x, this.feetY + this.mc.player.getEyeHeight(sneaking ? Pose.CROUCHING : Pose.STANDING), eyePos.z)
            : eyePos;
    }

    private int findItem(BlockState desired, boolean wholeInventory) {
        Item required = BlockStateUtil.requiredItem(desired);
        if (required != null && required instanceof BlockItem && this.mc.player != null) {
            Inventory inventory = this.mc.player.getInventory();
            int end = wholeInventory ? 36 : 9;

            for (int i = 0; i < end; i++) {
                if (inventory.getItem(i).is(required)) {
                    return i;
                }
            }

            return -1;
        } else {
            return -1;
        }
    }

    private boolean wholeInventory(int slot) {
        return slot >= 9 || this.currentScope != null;
    }

    private boolean wouldPlace(BlockState desired, BlockPos targetPos, BlockHitResult result, float[] rot, int slot, boolean skipChestType, boolean sneaking) {
        LocalPlayer player = this.mc.player;
        Input keys = player.input.keyPresses;
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack.getItem() instanceof BlockItem blockItem) {
            float originalYaw = player.getYRot();
            float originalPitch = player.getXRot();
            float delta = Mth.wrapDegrees(rot[0] - originalYaw);
            player.setYRot(originalYaw + delta);
            player.setXRot(rot[1]);
            player.input.keyPresses = new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), keys.jump(), sneaking, keys.sprint());

            try {
                BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, result);
                if (context.canPlace() && context.getClickedPos().equals(targetPos)) {
                    BlockState wouldBe = ((InvokerBlockItem)blockItem).easyplace$getPlacementState(context);
                    if (wouldBe == null) {
                        return false;
                    } else if (desired.getBlock() instanceof ChestBlock && wouldBe.getBlock() instanceof ChestBlock) {
                        ChestType want = desired.getValue(BlockStateProperties.CHEST_TYPE);
                        ChestType got = wouldBe.getValue(BlockStateProperties.CHEST_TYPE);
                        return got == want || got == ChestType.SINGLE && skipChestType ? StateRules.wouldFit(wouldBe, desired, true) : false;
                    } else {
                        return StateRules.wouldFit(wouldBe, desired, false);
                    }
                } else {
                    return false;
                }
            } catch (Exception ignored) {
                return false;
            } finally {
                player.setYRot(originalYaw);
                player.setXRot(originalPitch);
                player.input.keyPresses = keys;
            }
        } else {
            return false;
        }
    }

    private static Vec3[] aabbSideMultipliers(Direction dir) {
        return switch (dir) {
            case DOWN -> new Vec3[]{
                new Vec3(0.5, 0.0, 0.5),
                new Vec3(0.1, 0.0, 0.5),
                new Vec3(0.9, 0.0, 0.5),
                new Vec3(0.5, 0.0, 0.1),
                new Vec3(0.5, 0.0, 0.9),
                new Vec3(0.03, 0.0, 0.5),
                new Vec3(0.97, 0.0, 0.5),
                new Vec3(0.5, 0.0, 0.03),
                new Vec3(0.5, 0.0, 0.97),
                new Vec3(0.25, 0.0, 0.25),
                new Vec3(0.75, 0.0, 0.25),
                new Vec3(0.25, 0.0, 0.75),
                new Vec3(0.75, 0.0, 0.75)
            };
            case UP -> new Vec3[]{
                new Vec3(0.5, 1.0, 0.5),
                new Vec3(0.1, 1.0, 0.5),
                new Vec3(0.9, 1.0, 0.5),
                new Vec3(0.5, 1.0, 0.1),
                new Vec3(0.5, 1.0, 0.9),
                new Vec3(0.25, 1.0, 0.25),
                new Vec3(0.75, 1.0, 0.25),
                new Vec3(0.25, 1.0, 0.75),
                new Vec3(0.75, 1.0, 0.75)
            };
            default -> {
                double x = dir.getStepX() == 0 ? 0.5 : (1 + dir.getStepX()) / 2.0;
                double z = dir.getStepZ() == 0 ? 0.5 : (1 + dir.getStepZ()) / 2.0;
                yield new Vec3[]{
                    new Vec3(x, 0.5, z),
                    new Vec3(x, 0.25, z),
                    new Vec3(x, 0.75, z),
                    new Vec3(dir.getStepX() == 0 ? 0.15 : x, 0.5, dir.getStepZ() == 0 ? 0.15 : z),
                    new Vec3(dir.getStepX() == 0 ? 0.85 : x, 0.5, dir.getStepZ() == 0 ? 0.85 : z)
                };
            }
        };
    }

    private static boolean isReplaceable(BlockState state) {
        if (state.isAir()) {
            return true;
        } else {
            Block block = state.getBlock();
            if (block instanceof LiquidBlock) {
                return true;
            } else if (block instanceof CropBlock && state.hasProperty(CropBlock.AGE) && state.getValue(CropBlock.AGE) == 7) {
                return true;
            } else {
                return !(block instanceof FireBlock) && !(block instanceof ChorusFlowerBlock) ? state.canBeReplaced() : true;
            }
        }
    }

    private boolean isInRenderLayer(BlockPos pos) {
        try {
            LayerRange range = DataManager.getRenderLayerRange();
            return range == null || range.isPositionWithinRange(pos);
        } catch (Exception e) {
            return true;
        }
    }
}
