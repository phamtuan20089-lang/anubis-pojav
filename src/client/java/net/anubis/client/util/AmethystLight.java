package net.anubis.client.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LayerLightEventListener;

@Environment(EnvType.CLIENT)
public final class AmethystLight {
    public static final int SMALL_BUD = 1;
    public static final int MEDIUM_BUD = 2;
    public static final int LARGE_BUD = 4;
    public static final int CLUSTER = 5;
    private static final Direction[] SIDES = Direction.values();

    private AmethystLight() {
    }

    public static void scan(ClientLevel level, LevelChunk chunk, AmethystLight.Sink out) {
        LevelChunkSection[] sections = chunk.getSections();
        int minSectionY = chunk.getMinSectionY();
        ChunkPos pos = chunk.getPos();
        MutableBlockPos cursor = new MutableBlockPos();

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section != null && !section.hasOnlyAir() && section.getStates().maybeHas(AmethystLight::isLining)) {
                int baseY = (minSectionY + i) * 16;

                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            if (section.getBlockState(x, y, z).getLightEmission() <= 0) {
                                cursor.set(pos.getMinBlockX() + x, baseY + y, pos.getMinBlockZ() + z);
                                int light = level.getBrightness(LightLayer.BLOCK, cursor);
                                if (isGrowthLight(light) && touchesLining(level, cursor) && isLocalMaximum(level, cursor, light)) {
                                    out.accept(cursor.asLong(), light);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void scanSources(ClientLevel level, LevelChunk chunk, AmethystLight.Sink out) {
        LevelChunkSection[] sections = chunk.getSections();
        int minSectionY = chunk.getMinSectionY();
        ChunkPos pos = chunk.getPos();
        AmethystLight.LightCache light = new AmethystLight.LightCache(level, pos.x, pos.z, minSectionY, sections.length);
        MutableBlockPos cursor = new MutableBlockPos();

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section != null && !section.hasOnlyAir() && !light.quiet(i) && section.getStates().maybeHas(AmethystLight::isLining)) {
                int baseY = (minSectionY + i) * 16;

                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (state.getLightEmission() <= 0) {
                                int wx = pos.getMinBlockX() + x;
                                int wy = baseY + y;
                                int wz = pos.getMinBlockZ() + z;
                                int value = light.get(wx, wy, wz);
                                int brightest = light.brightestAround(wx, wy, wz);
                                int source;
                                if (value == 0) {
                                    if (brightest != 4 && brightest != 3 || !isOpen(state)) {
                                        continue;
                                    }

                                    cursor.set(wx, wy, wz);
                                    if (!touchesLining(level, cursor) || !litOpening(level, light, cursor, brightest)) {
                                        continue;
                                    }

                                    source = brightest == 4 ? 5 : 4;
                                } else {
                                    if (!isGrowthLight(value) || brightest >= value) {
                                        continue;
                                    }

                                    cursor.set(wx, wy, wz);
                                    if (!touchesLining(level, cursor) || value == 4 && besideDarkOpening(level, light, cursor)) {
                                        continue;
                                    }

                                    source = value;
                                }

                                out.accept(cursor.asLong(), source);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean litOpening(ClientLevel level, AmethystLight.LightCache light, BlockPos pos, int brightest) {
        MutableBlockPos next = new MutableBlockPos();

        for (Direction direction : SIDES) {
            next.setWithOffset(pos, direction);
            if (light.get(next.getX(), next.getY(), next.getZ()) == brightest && isOpen(level.getBlockState(next))) {
                return true;
            }
        }

        return false;
    }

    private static boolean besideDarkOpening(ClientLevel level, AmethystLight.LightCache light, BlockPos pos) {
        MutableBlockPos next = new MutableBlockPos();

        for (Direction direction : SIDES) {
            next.setWithOffset(pos, direction);
            if (light.get(next.getX(), next.getY(), next.getZ()) == 0) {
                BlockState state = level.getBlockState(next);
                if (isOpen(state) && !state.is(Blocks.VOID_AIR)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isOpen(BlockState state) {
        return state.isAir() || state.is(Blocks.AMETHYST_CLUSTER) || state.is(Blocks.WATER);
    }

    public static boolean isGrowthLight(int light) {
        return light == 1 || light == 2 || light == 4 || light == 5;
    }

    public static boolean touchesLining(ClientLevel level, BlockPos pos) {
        for (Direction direction : SIDES) {
            if (isLining(level.getBlockState(pos.relative(direction)))) {
                return true;
            }
        }

        return false;
    }

    public static boolean isLining(BlockState state) {
        return state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST);
    }

    private static boolean isLocalMaximum(ClientLevel level, BlockPos pos, int light) {
        for (Direction direction : SIDES) {
            if (level.getBrightness(LightLayer.BLOCK, pos.relative(direction)) >= light) {
                return false;
            }
        }

        return true;
    }

    @Environment(EnvType.CLIENT)
    private static final class LightCache {
        private final LayerLightEventListener listener;
        private final int chunkX;
        private final int chunkZ;
        private final int minSectionY;
        private final int sectionCount;
        private final DataLayer[] layers;
        private final boolean[] fetched;

        private LightCache(ClientLevel level, int chunkX, int chunkZ, int minSectionY, int sectionCount) {
            this.listener = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.minSectionY = minSectionY - 1;
            this.sectionCount = sectionCount + 2;
            this.layers = new DataLayer[9 * this.sectionCount];
            this.fetched = new boolean[this.layers.length];
        }

        private DataLayer layer(int dx, int dz, int section) {
            int slot = ((dx + 1) * 3 + dz + 1) * this.sectionCount + section;
            if (!this.fetched[slot]) {
                this.fetched[slot] = true;
                DataLayer data = this.listener.getDataLayerData(SectionPos.of(this.chunkX + dx, this.minSectionY + section, this.chunkZ + dz));
                this.layers[slot] = data != null && !data.isEmpty() ? data : null;
            }

            return this.layers[slot];
        }

        int get(int x, int y, int z) {
            int dx = (x >> 4) - this.chunkX;
            int dz = (z >> 4) - this.chunkZ;
            int section = (y >> 4) - this.minSectionY;
            if (dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1 && section >= 0 && section < this.sectionCount) {
                DataLayer data = this.layer(dx, dz, section);
                return data == null ? 0 : data.get(x & 15, y & 15, z & 15);
            } else {
                return 0;
            }
        }

        int brightestAround(int x, int y, int z) {
            int best = this.get(x + 1, y, z);
            best = Math.max(best, this.get(x - 1, y, z));
            best = Math.max(best, this.get(x, y + 1, z));
            best = Math.max(best, this.get(x, y - 1, z));
            best = Math.max(best, this.get(x, y, z + 1));
            return Math.max(best, this.get(x, y, z - 1));
        }

        boolean quiet(int sectionIndex) {
            int section = sectionIndex + 1;
            return this.layer(0, 0, section) == null
                && this.layer(0, 0, section - 1) == null
                && this.layer(0, 0, section + 1) == null
                && this.layer(-1, 0, section) == null
                && this.layer(1, 0, section) == null
                && this.layer(0, -1, section) == null
                && this.layer(0, 1, section) == null;
        }
    }

    @FunctionalInterface
    @Environment(EnvType.CLIENT)
    public interface Sink {
        void accept(long var1, int var3);
    }
}
