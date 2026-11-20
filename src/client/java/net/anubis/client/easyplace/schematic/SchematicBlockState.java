package net.anubis.client.easyplace.schematic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public record SchematicBlockState(BlockState state, Identifier blockId, Map<String, String> properties) {
    public static SchematicBlockState of(BlockState state) {
        Map<String, String> properties = new LinkedHashMap<>();
        state.getValues().forEach((property, value) -> properties.put(property.getName(), value.toString()));
        return new SchematicBlockState(state, BuiltInRegistries.BLOCK.getKey(state.getBlock()), Collections.unmodifiableMap(properties));
    }

    public String debugString() {
        if (this.properties.isEmpty()) {
            return this.blockId.toString();
        }

        StringBuilder result = new StringBuilder(this.blockId.toString()).append('[');
        this.properties.forEach((name, value) -> result.append(name).append('=').append(value).append(','));
        result.setCharAt(result.length() - 1, ']');
        return result.toString();
    }
}
