package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.anubis.client.module.impl.render.XrayModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionCompiler.Results;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(SectionCompiler.class)
public abstract class MixinSectionCompiler {
    @WrapOperation(
        method = "compile",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState anubis$xrayBlockState(RenderSectionRegion region, BlockPos pos, Operation<BlockState> original) {
        BlockState state = original.call(region, pos);
        XrayModule.Snapshot snapshot = XrayModule.active;
        if (snapshot != null && !state.isAir()) {
            float[] alphaSlot = XrayModule.CURRENT_ALPHA.get();
            float alpha = snapshot.alphaFor(region, state, pos);
            alphaSlot[0] = alpha;
            return alpha <= 0.0F ? Blocks.AIR.defaultBlockState() : state;
        } else {
            return state;
        }
    }

    @WrapOperation(
        method = "compile",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemBlockRenderTypes;getChunkRenderType(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;"
        )
    )
    private ChunkSectionLayer anubis$xrayBlockLayer(BlockState state, Operation<ChunkSectionLayer> original) {
        if (XrayModule.active == null) {
            return original.call(state);
        }

        float alpha = XrayModule.CURRENT_ALPHA.get()[0];
        return alpha > 0.0F && alpha < 1.0F ? ChunkSectionLayer.TRANSLUCENT : original.call(state);
    }

    @WrapOperation(
        method = "compile",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemBlockRenderTypes;getRenderLayer(Lnet/minecraft/world/level/material/FluidState;)Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;"
        )
    )
    private ChunkSectionLayer anubis$xrayFluidLayer(FluidState fluidState, Operation<ChunkSectionLayer> original) {
        if (XrayModule.active == null) {
            return original.call(fluidState);
        }

        float alpha = XrayModule.CURRENT_ALPHA.get()[0];
        return alpha > 0.0F && alpha < 1.0F ? ChunkSectionLayer.TRANSLUCENT : original.call(fluidState);
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private void anubis$xrayResetAlpha(
        SectionPos sectionPos,
        RenderSectionRegion region,
        VertexSorting vertexSorting,
        SectionBufferBuilderPack bufferPack,
        CallbackInfoReturnable<Results> cir
    ) {
        XrayModule.CURRENT_ALPHA.get()[0] = 1.0F;
    }
}
