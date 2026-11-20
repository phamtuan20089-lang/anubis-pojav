package net.anubis.client.easyplace.builder;

import fi.dy.masa.litematica.world.WorldSchematic;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.anubis.client.easyplace.util.BlockStateUtil;
import net.anubis.client.easyplace.util.StateRules;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Direction.Plane;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GlazedTerracottaBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;

@Environment(EnvType.CLIENT)
final class BuildOrder {
    static final int STAGE_NORMAL = 0;
    static final int STAGE_PISTON = 1;
    static final int STAGE_OBSERVER = 2;
    static final int STAGE_POWER = 3;
    private static final int MAX_TRACK = 512;

    private BuildOrder() {
    }

    static boolean powerOrTnt(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.REDSTONE_BLOCK || block instanceof RedstoneTorchBlock || block instanceof DaylightDetectorBlock || block instanceof TntBlock;
    }

    static boolean late(WorldSchematic schematic, BlockPos pos, BlockState expected, boolean machineLast) {
        return stage(schematic, pos, expected, machineLast) > 0;
    }

    static int stage(WorldSchematic schematic, BlockPos pos, BlockState expected, boolean machineLast) {
        int own = ownStage(schematic, pos, expected, machineLast);
        if (own != 0) {
            return own;
        }

        BlockPos support = hangsOn(expected, pos);
        return support == null ? 0 : ownStage(schematic, support, schematic.getBlockState(support), machineLast);
    }

    private static int ownStage(WorldSchematic schematic, BlockPos pos, BlockState expected, boolean machineLast) {
        if (powerOrTnt(expected) || pushedIntoTnt(schematic, pos, expected) != null) {
            return 3;
        } else if (!machineLast) {
            return 0;
        } else {
            Block block = expected.getBlock();
            if (block instanceof PistonBaseBlock) {
                return 1;
            } else {
                return block instanceof ObserverBlock ? 2 : 0;
            }
        }
    }

    static boolean pistonWouldFire(ClientLevel level, BlockPos pos) {
        return level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above());
    }

    static BlockPos pushedIntoTnt(WorldSchematic schematic, BlockPos pos, BlockState expected) {
        if (!expected.isAir() && !(expected.getBlock() instanceof TntBlock)) {
            for (Direction way : Direction.values()) {
                BlockState behind = schematic.getBlockState(pos.relative(way.getOpposite()));
                if (behind.getBlock() instanceof PistonBaseBlock && behind.getValue(BlockStateProperties.FACING) == way) {
                    BlockPos ahead = pos.relative(way);
                    if (schematic.getBlockState(ahead).getBlock() instanceof TntBlock) {
                        return ahead;
                    }
                }
            }

            return null;
        } else {
            return null;
        }
    }

    static BlockPos railBefore(ClientLevel level, WorldSchematic schematic, BlockPos pos, BlockState expected, Predicate<BlockPos> gaveUp) {
        List<BlockPos> track = track(schematic, pos);
        int index = track.indexOf(pos);
        if (index <= 0) {
            return null;
        }

        BlockPos before = track.get(index - 1);
        return !gaveUp.test(before) && !(level.getBlockState(before).getBlock() instanceof BaseRailBlock) ? before : null;
    }

    static boolean railShapeOff(BlockState actual, BlockState expected) {
        RailShape want = railShape(expected);
        RailShape got = railShape(actual);
        return want != null && got != null && want != got;
    }

    private static List<BlockPos> track(WorldSchematic schematic, BlockPos pos) {
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> stack = new ArrayList<>(List.of(pos));
        BlockPos start = null;

        while (!stack.isEmpty() && seen.size() < 512) {
            BlockPos at = stack.remove(stack.size() - 1);
            if (seen.add(at)) {
                List<BlockPos> links = links(schematic, at);
                if (links.size() < 2 && (start == null || before(at, start))) {
                    start = at;
                }

                stack.addAll(links);
            }
        }

        if (start == null) {
            for (BlockPos rail : seen) {
                if (start == null || before(rail, start)) {
                    start = rail;
                }
            }
        }

        List<BlockPos> order = new ArrayList<>();
        Set<BlockPos> laid = new HashSet<>();
        BlockPos at = start;

        while (at != null && laid.add(at)) {
            order.add(at);
            BlockPos next = null;

            for (BlockPos link : links(schematic, at)) {
                if (!laid.contains(link) && (next == null || before(link, next))) {
                    next = link;
                }
            }

            at = next;
        }

        return order;
    }

    private static boolean before(BlockPos a, BlockPos b) {
        if (a.getY() != b.getY()) {
            return a.getY() < b.getY();
        } else {
            return a.getX() != b.getX() ? a.getX() < b.getX() : a.getZ() < b.getZ();
        }
    }

    private static List<BlockPos> links(WorldSchematic schematic, BlockPos pos) {
        List<BlockPos> out = new ArrayList<>(2);
        RailShape shape = railShape(schematic.getBlockState(pos));
        if (shape == null) {
            return out;
        }

        for (BlockPos end : ends(pos, shape)) {
            BlockPos other = railAt(schematic, end);
            if (other != null) {
                RailShape otherShape = railShape(schematic.getBlockState(other));

                for (BlockPos back : ends(other, otherShape)) {
                    if (pos.equals(railAt(schematic, back))) {
                        out.add(other);
                        break;
                    }
                }
            }
        }

        return out;
    }

    private static BlockPos railAt(WorldSchematic schematic, BlockPos cell) {
        if (railShape(schematic.getBlockState(cell)) != null) {
            return cell;
        } else {
            return railShape(schematic.getBlockState(cell.below())) != null ? cell.below() : null;
        }
    }

    private static List<BlockPos> ends(BlockPos pos, RailShape shape) {
        return switch (shape) {
            case NORTH_SOUTH -> List.of(pos.north(), pos.south());
            case EAST_WEST -> List.of(pos.east(), pos.west());
            case ASCENDING_EAST -> List.of(pos.east().above(), pos.west());
            case ASCENDING_WEST -> List.of(pos.west().above(), pos.east());
            case ASCENDING_NORTH -> List.of(pos.north().above(), pos.south());
            case ASCENDING_SOUTH -> List.of(pos.south().above(), pos.north());
            case SOUTH_EAST -> List.of(pos.south(), pos.east());
            case SOUTH_WEST -> List.of(pos.south(), pos.west());
            case NORTH_WEST -> List.of(pos.north(), pos.west());
            case NORTH_EAST -> List.of(pos.north(), pos.east());
        };
    }

    private static RailShape railShape(BlockState state) {
        if (state.getBlock() instanceof BaseRailBlock rail) {
            Property<RailShape> property = rail.getShapeProperty();
            return state.hasProperty(property) ? state.getValue(property) : null;
        } else {
            return null;
        }
    }

    static boolean lookOnly(BlockState state) {
        Block block = state.getBlock();
        return block instanceof StairBlock
            || block instanceof AbstractFurnaceBlock
            || block instanceof BarrelBlock
            || block instanceof RotatedPillarBlock
            || block instanceof CarvedPumpkinBlock
            || block instanceof GlazedTerracottaBlock
            || block instanceof FenceGateBlock
            || block instanceof LoomBlock
            || block instanceof StonecutterBlock
            || block instanceof LecternBlock
            || block instanceof BeehiveBlock
            || block instanceof AnvilBlock;
    }

    static List<BlockState> turnedVariants(BlockState state) {
        List<BlockState> out = new ArrayList<>();

        for (Property<?> property : List.of(
            BlockStateProperties.FACING, BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.AXIS, BlockStateProperties.HORIZONTAL_AXIS
        )) {
            if (state.hasProperty(property)) {
                for (Object value : property.getPossibleValues()) {
                    BlockState turned = with(state, property, value);
                    if (!turned.equals(state)) {
                        out.add(turned);
                    }
                }
                break;
            }
        }

        return out;
    }

    private static BlockState with(BlockState state, Property property, Object value) {
        return state.setValue(property, (Comparable)value);
    }

    static BlockPos hangsOn(BlockState state, BlockPos pos) {
        Block block = state.getBlock();
        if (block instanceof WallTorchBlock || block instanceof RedstoneWallTorchBlock || block instanceof WallSignBlock || block instanceof LadderBlock) {
            return pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
        }

        if (block instanceof FaceAttachedHorizontalDirectionalBlock) {
            return switch ((AttachFace)state.getValue(BlockStateProperties.ATTACH_FACE)) {
                case FLOOR -> pos.below();
                case CEILING -> pos.above();
                case WALL -> pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
            };
        } else {
            return !(block instanceof RedStoneWireBlock)
                    && !(block instanceof DiodeBlock)
                    && !(block instanceof BaseRailBlock)
                    && !(block instanceof CarpetBlock)
                    && !(block instanceof BasePressurePlateBlock)
                    && !(block instanceof BaseTorchBlock)
                ? null
                : pos.below();
        }
    }

    static List<BlockPos> supportCells(BlockState expected, BlockPos pos) {
        Direction look = StateRules.lookFor(expected);
        if (look == null) {
            look = StateRules.flatLookFor(expected);
        }

        if (look != null) {
            List<BlockPos> cells = new ArrayList<>(List.of(pos.relative(look), pos.below()));

            for (Direction side : Plane.HORIZONTAL) {
                if (side != look) {
                    cells.add(pos.relative(side));
                }
            }

            if (look != Direction.UP) {
                cells.add(pos.above());
            }

            return cells;
        } else {
            Block block = expected.getBlock();
            if (block instanceof HopperBlock) {
                Direction spout = expected.getValue(BlockStateProperties.FACING_HOPPER);
                return spout == Direction.DOWN ? List.of(pos.below(), pos.above()) : List.of(pos.relative(spout));
            } else if (hangsOn(expected, pos) != null) {
                return List.of();
            } else if (expected.hasProperty(BlockStateProperties.AXIS)) {
                Axis axis = expected.getValue(BlockStateProperties.AXIS);
                return List.of(
                    pos.relative(Direction.fromAxisAndDirection(axis, AxisDirection.NEGATIVE)),
                    pos.relative(Direction.fromAxisAndDirection(axis, AxisDirection.POSITIVE))
                );
            } else if (expected.hasProperty(BlockStateProperties.SLAB_TYPE)) {
                return expected.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP ? List.of(pos.above()) : List.of(pos.below());
            } else if (expected.hasProperty(BlockStateProperties.HALF)) {
                return expected.getValue(BlockStateProperties.HALF) == Half.TOP ? List.of(pos.above()) : List.of(pos.below());
            } else {
                return List.of(pos.below(), pos.north(), pos.south(), pos.east(), pos.west(), pos.above());
            }
        }
    }

    static boolean waits(ClientLevel level, WorldSchematic schematic, BlockPos pos, BlockState expected, Predicate<BlockPos> gaveUp, boolean machineLast) {
        Block block = expected.getBlock();
        BlockPos tnt = pushedIntoTnt(schematic, pos, expected);
        if (tnt != null && !(level.getBlockState(tnt).getBlock() instanceof TntBlock)) {
            return true;
        }

        if (block instanceof PistonBaseBlock) {
            return pistonWouldFire(level, pos);
        }

        if (block instanceof BaseRailBlock && railBefore(level, schematic, pos, expected, gaveUp) != null) {
            return true;
        }

        if (block instanceof ObserverBlock) {
            BlockPos watched = pos.relative(expected.getValue(BlockStateProperties.FACING));
            BlockState wanted = schematic.getBlockState(watched);
            if (BlockStateUtil.requiredItem(wanted) == null || stage(schematic, watched, wanted, machineLast) > 2 || gaveUp.test(watched)) {
                return false;
            } else {
                return wanted.getBlock() instanceof ObserverBlock && watched.relative(wanted.getValue(BlockStateProperties.FACING)).equals(pos)
                    ? false
                    : !StateRules.placed(level.getBlockState(watched), wanted);
            }
        } else if (block instanceof FallingBlock) {
            return FallingBlock.isFree(level.getBlockState(pos.below()));
        } else if (!expected.hasProperty(BlockStateProperties.WATERLOGGED)
            && expected.getFluidState().isSource()
            && expected.getFluidState().is(FluidTags.WATER)) {
            return !level.getFluidState(pos).isSource() || !level.getFluidState(pos).is(FluidTags.WATER);
        } else {
            return block instanceof TntBlock ? level.hasNeighborSignal(pos) : false;
        }
    }
}
