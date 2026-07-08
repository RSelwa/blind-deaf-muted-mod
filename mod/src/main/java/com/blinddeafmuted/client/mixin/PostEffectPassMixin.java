package com.blinddeafmuted.client.mixin;

import com.blinddeafmuted.client.ClientConfigState;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectPipeline;
import net.minecraft.client.gl.ShaderProgram;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes the MYOPIA blur strength live-tunable from the config slider menu.
 *
 * <p><b>Why rewrite the field, not call a uniform setter.</b> In 1.21.4 a post-effect pass
 * uploads its uniforms at draw time (inside the frame-graph render lambda) by reading its own
 * {@code uniforms} list — the baked {@code List<PostEffectPipeline.Uniform>} from the pipeline
 * JSON — and calling {@code GlUniform.set(values, size)} right before drawing. {@code setUniforms()}
 * runs AFTER the draw (cleanup), and neither {@code GlUniform.set(float)} nor
 * {@code set(ShaderProgramDefinition.Uniform)} from a later hook survives to the GPU. The ONLY
 * value that actually reaches the shader is the one sitting in the {@code uniforms} list at draw
 * time. So we rewrite that list's {@code BlurStrength} entry to the live config value at the head
 * of {@link PostEffectPass#render}, every frame — the change takes effect with no pipeline reload.
 *
 * <p>Program-agnostic and safe: we only touch a {@code BlurStrength} entry, which only our
 * {@code blind-deaf-muted:myopia} program declares; every other pass is left untouched.
 */
@Mixin(PostEffectPass.class)
public abstract class PostEffectPassMixin {

    @Shadow @Final private ShaderProgram program;
    @Shadow @Final @Mutable private List<PostEffectPipeline.Uniform> uniforms;

    @Inject(method = "render", at = @At("HEAD"))
    private void blinddeafmuted$applyLiveBlurStrength(CallbackInfo ci) {
        // Fast skip: only our myopia program declares a BlurStrength uniform.
        if (this.program == null || this.program.getUniform("BlurStrength") == null) return;

        float strength = ClientConfigState.get().myopiaBlurStrength();
        float darkness = ClientConfigState.get().myopiaDarkness();
        List<PostEffectPipeline.Uniform> rebuilt = new ArrayList<>(this.uniforms.size());
        boolean sawBlur = false, sawDark = false;
        for (PostEffectPipeline.Uniform u : this.uniforms) {
            switch (u.name()) {
                case "BlurStrength" -> {
                    rebuilt.add(new PostEffectPipeline.Uniform("BlurStrength", List.of(strength)));
                    sawBlur = true;
                }
                case "Darkness" -> {
                    rebuilt.add(new PostEffectPipeline.Uniform("Darkness", List.of(darkness)));
                    sawDark = true;
                }
                default -> rebuilt.add(u);
            }
        }
        // If the JSON somehow lacked an entry, add it so the value still applies.
        if (!sawBlur) rebuilt.add(new PostEffectPipeline.Uniform("BlurStrength", List.of(strength)));
        if (!sawDark) rebuilt.add(new PostEffectPipeline.Uniform("Darkness", List.of(darkness)));
        this.uniforms = rebuilt;
    }
}
