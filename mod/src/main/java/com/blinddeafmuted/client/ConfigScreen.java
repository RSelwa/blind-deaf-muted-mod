package com.blinddeafmuted.client;

import com.blinddeafmuted.common.ConfigUpdatePayload;
import com.blinddeafmuted.common.ModConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Live tuning menu: one slider per {@link ModConfig} knob (blind fog distance, deaf/muted voice
 * cutoffs + volumes, event timers, …). Opened with the config keybind (see {@code ConfigMenu}).
 *
 * <p><b>No restart, ever.</b> Dragging a slider previews the value locally at once (so
 * client-side vision knobs — fog / ambient loudness — change under you live) and, on release,
 * sends the whole edited config to the server ({@link ConfigUpdatePayload}). The server persists
 * it and re-broadcasts, so the server-enforced audio ({@code VoiceFx}) and everyone else's client
 * pick it up within a round-trip. Closing the screen sends a final sync (covers keyboard nudges).
 *
 * <p>Access is intentionally open to every player (the cheat risk was accepted) — no op gate.
 *
 * <p>The field↔slider mapping is by index into {@link ModConfig#toArray()}; the specs below must
 * stay in that same order. Myopia blur radius is deliberately absent — it's a GLSL constant in
 * {@code myopia.fsh}, not a runtime uniform, so it can't be nudged live yet.
 */
public final class ConfigScreen extends Screen {

    /** How a slider renders its value + the sensible drag range for each knob. */
    private enum Style { HZ, GAIN, BLOCKS, MINUTES, PERCENT, SECONDS }

    private record Spec(int index, float min, float max, Style style, String labelKey) {}

    /** One spec per ModConfig field, in ModConfig.toArray() order. */
    private static final Spec[] SPECS = {
            new Spec(0, 40f, 1200f, Style.HZ, "config.blind-deaf-muted.deafLowpassHz"),
            new Spec(1, 0f, 2f, Style.GAIN, "config.blind-deaf-muted.deafVolume"),
            new Spec(2, 100f, 2000f, Style.HZ, "config.blind-deaf-muted.deafMegaphoneLowpassHz"),
            new Spec(3, 0f, 5f, Style.GAIN, "config.blind-deaf-muted.deafMegaphoneVolume"),
            new Spec(4, 40f, 1200f, Style.HZ, "config.blind-deaf-muted.mutedLowpassHz"),
            new Spec(5, 0f, 4f, Style.GAIN, "config.blind-deaf-muted.mutedVolume"),
            new Spec(6, 40f, 1200f, Style.HZ, "config.blind-deaf-muted.mutedMegaphoneLowpassHz"),
            new Spec(7, 0f, 4f, Style.GAIN, "config.blind-deaf-muted.mutedMegaphoneVolume"),
            new Spec(8, 0.5f, 20f, Style.BLOCKS, "config.blind-deaf-muted.blindFogHardEnd"),
            new Spec(9, 1f, 40f, Style.BLOCKS, "config.blind-deaf-muted.blindFogMediumEnd"),
            new Spec(10, 0f, 1f, Style.GAIN, "config.blind-deaf-muted.deafEnvVolume"),
            new Spec(11, 0.5f, 30f, Style.MINUTES, "config.blind-deaf-muted.eventMinMinutes"),
            new Spec(12, 0.5f, 60f, Style.MINUTES, "config.blind-deaf-muted.eventMaxMinutes"),
            new Spec(13, 0f, 1f, Style.PERCENT, "config.blind-deaf-muted.randomizerChestChance"),
            new Spec(14, 1f, 30f, Style.SECONDS, "config.blind-deaf-muted.megaphoneBurstSeconds"),
            new Spec(15, 5f, 600f, Style.SECONDS, "config.blind-deaf-muted.megaphoneCooldownSeconds"),
    };

    /** Working copy edited by the sliders; rebuilt into a ModConfig on each send. */
    private final float[] working = ClientConfigState.get().toArray();
    private final List<ParamSlider> sliders = new ArrayList<>();

    public ConfigScreen() {
        super(Text.translatable("config.blind-deaf-muted.title"));
    }

    @Override
    protected void init() {
        sliders.clear();
        int rows = 8;                     // 16 knobs over 2 columns
        int sliderW = 150, sliderH = 20, gapY = 24, topY = 34;
        int leftX = this.width / 2 - sliderW - 5;
        int rightX = this.width / 2 + 5;

        for (Spec spec : SPECS) {
            int col = spec.index() / rows;
            int row = spec.index() % rows;
            int x = (col == 0) ? leftX : rightX;
            int y = topY + row * gapY;
            ParamSlider slider = new ParamSlider(spec, x, y, sliderW, sliderH);
            sliders.add(slider);
            addDrawableChild(slider);
        }

        int bottomY = topY + rows * gapY + 6;
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("config.blind-deaf-muted.reset"), b -> resetDefaults())
                .dimensions(leftX, bottomY, sliderW, 20).build());
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.done"), b -> close())
                .dimensions(rightX, bottomY, sliderW, 20).build());
    }

    private void resetDefaults() {
        float[] def = ModConfig.DEFAULT.toArray();
        System.arraycopy(def, 0, working, 0, working.length);
        for (ParamSlider s : sliders) s.syncFromWorking();
        ClientConfigState.set(ModConfig.fromArray(working)); // instant local preview
        sendUpdate();
    }

    /** Push the current working config to the server (persist + broadcast). */
    private void sendUpdate() {
        if (ClientPlayNetworking.canSend(ConfigUpdatePayload.ID)) {
            ClientPlayNetworking.send(new ConfigUpdatePayload(ModConfig.fromArray(working)));
        }
    }

    @Override
    public void close() {
        sendUpdate(); // final sync — catches keyboard nudges that never fired a mouse release
        super.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFF);
    }

    /** A slider bound to one {@code working[index]} knob, mapping the normalized 0..1 handle
     *  position onto the knob's [min,max] range and formatting the label for its style. */
    private final class ParamSlider extends SliderWidget {
        private final Spec spec;

        ParamSlider(Spec spec, int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(), normalized(spec, working[spec.index()]));
            this.spec = spec;
            updateMessage();
        }

        private static double normalized(Spec spec, float raw) {
            return net.minecraft.util.math.MathHelper.clamp(
                    (raw - spec.min()) / (spec.max() - spec.min()), 0.0, 1.0);
        }

        /** Re-seed the handle + label from the working array (after a reset). */
        void syncFromWorking() {
            this.value = normalized(spec, working[spec.index()]);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            float raw = (float) (spec.min() + this.value * (spec.max() - spec.min()));
            setMessage(Text.translatable(spec.labelKey())
                    .append(Text.literal(": " + format(spec.style(), raw))));
        }

        @Override
        protected void applyValue() {
            working[spec.index()] = (float) (spec.min() + this.value * (spec.max() - spec.min()));
            // Instant local preview for client-side effects (fog / ambient volume). Server-enforced
            // audio updates once the release-triggered packet round-trips.
            ClientConfigState.set(ModConfig.fromArray(working));
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            sendUpdate();
        }
    }

    private static String format(Style style, float v) {
        return switch (style) {
            case HZ -> Math.round(v) + " Hz";
            case GAIN -> String.format("%.2f×", v);
            case BLOCKS -> String.format("%.1f", v) + " blk";
            case MINUTES -> String.format("%.1f", v) + " min";
            case PERCENT -> Math.round(v * 100f) + "%";
            case SECONDS -> Math.round(v) + " s";
        };
    }
}
