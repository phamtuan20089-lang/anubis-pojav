package net.anubis.client.module.impl.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.nio.ByteBuffer;
import java.util.OptionalInt;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class ColorGradingPass {
    private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
        .withLocation(Identifier.fromNamespaceAndPath("anubis", "pipeline/color_grading"))
        .withVertexShader("core/screenquad")
        .withFragmentShader(Identifier.fromNamespaceAndPath("anubis", "core/color_grading"))
        .withSampler("InSampler")
        .withUniform("ColorGradingConfig", UniformType.UNIFORM_BUFFER)
        .withoutBlend()
        .withColorWrite(true, false)
        .withDepthWrite(false)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withVertexFormat(DefaultVertexFormat.EMPTY, Mode.TRIANGLES)
        .build();
    private static final Supplier<String> PASS_LABEL = () -> "Anubis color grading";
    private static final Supplier<String> CONFIG_LABEL = () -> "Anubis color grading config";
    private static final int CONFIG_SIZE = 32;
    private static TextureTarget copy;
    private static GpuBuffer config;
    private static GpuBufferSlice configSlice;
    private static ByteBuffer configData;
    private static boolean invalidShaderLogged;

    private ColorGradingPass() {
    }

    static void apply(boolean worldRendered, float vibrance, float contrast, float exposure, float warmth, float vignette) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        GpuTexture mainColor = main.getColorTexture();
        if (worldRendered
            && mc.level != null
            && mc.isGameLoadFinished()
            && !mc.gameRenderer.isPanoramicMode()
            && mainColor != null
            && main.width > 0
            && main.height > 0) {
            if (copy == null) {
                allocate(main.width, main.height);
            } else if (copy.width != main.width || copy.height != main.height) {
                copy.resize(main.width, main.height);
            }

            GpuDevice device = RenderSystem.getDevice();
            if (shaderReady(device)) {
                CommandEncoder encoder = device.createCommandEncoder();
                encoder.copyTextureToTexture(mainColor, copy.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
                configData.putFloat(0, vibrance);
                configData.putFloat(4, contrast);
                configData.putFloat(8, exposure);
                configData.putFloat(12, warmth);
                configData.putFloat(16, vignette);
                encoder.writeToBuffer(configSlice, configData);

                try (RenderPass pass = encoder.createRenderPass(PASS_LABEL, main.getColorTextureView(), OptionalInt.empty())) {
                    pass.setPipeline(PIPELINE);
                    pass.setUniform("ColorGradingConfig", configSlice);
                    pass.bindTexture("InSampler", copy.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                    pass.draw(0, 3);
                }
            }
        }
    }

    private static boolean shaderReady(GpuDevice device) {
        if (device.precompilePipeline(PIPELINE).isValid()) {
            invalidShaderLogged = false;
            return true;
        }

        if (!invalidShaderLogged) {
            invalidShaderLogged = true;
            LOGGER.warn("Color Grading shader {} did not compile; the picture is left as it is", PIPELINE.getFragmentShader());
        }

        return false;
    }

    private static void allocate(int width, int height) {
        copy = new TextureTarget("Anubis color grading copy", width, height, false);
        config = RenderSystem.getDevice().createBuffer(CONFIG_LABEL, 136, 32L);
        configSlice = config.slice();
        configData = BufferUtils.createByteBuffer(32);
    }

    static void release() {
        if (copy != null) {
            copy.destroyBuffers();
            copy = null;
        }

        if (config != null) {
            config.close();
            config = null;
        }

        configSlice = null;
        configData = null;
    }
}
