package com.blinddeafmuted.client;

import java.util.ArrayList;
import java.util.List;

import com.blinddeafmuted.common.ConfigUpdatePayload;
import com.blinddeafmuted.common.ModConfig;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

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
    private enum Style { HZ, GAIN, BLOCKS, MINUTES, PERCENT, SECONDS, HFGAIN, HEADER }

    /** Tabs — each knob belongs to exactly one, and only the active tab's sliders are shown so the
     *  grid stays short and readable instead of one giant scroll of everything. */
    private enum Category {
        DEAF("config.blind-deaf-muted.cat.deaf"),
        MUTED("config.blind-deaf-muted.cat.muted"),
        BLIND("config.blind-deaf-muted.cat.blind"),
        RELIEF("config.blind-deaf-muted.cat.relief"),
        OTHER("config.blind-deaf-muted.cat.other");

        final String labelKey;
        Category(String labelKey) { this.labelKey = labelKey; }
    }

    private record Spec(int index, float min, float max, Style style, String labelKey, Category cat) {}

    /** One spec per (shown) ModConfig field, grouped by tab. Order within a tab = display order. */
    private static final Spec[] SPECS = {
            // ---- DEAF: how a deaf player hears voices (0-3) + the game world (10, 21-23) ----
            new Spec(0, 40f, 1200f, Style.HZ, "config.blind-deaf-muted.deafLowpassHz", Category.DEAF),
            new Spec(1, 0f, 50f, Style.GAIN, "config.blind-deaf-muted.deafVolume", Category.DEAF),
            new Spec(2, 100f, 2000f, Style.HZ, "config.blind-deaf-muted.deafMegaphoneLowpassHz", Category.DEAF),
            new Spec(3, 0f, 50f, Style.GAIN, "config.blind-deaf-muted.deafMegaphoneVolume", Category.DEAF),
            new Spec(10, 0f, 8f, Style.GAIN, "config.blind-deaf-muted.deafEnvVolume", Category.DEAF),
            new Spec(21, 0.000000000001f, 0.02f, Style.HFGAIN, "config.blind-deaf-muted.deafMuffleGainHf", Category.DEAF),
            new Spec(22, 0f, 1f, Style.GAIN, "config.blind-deaf-muted.deafMuffleGain", Category.DEAF),
            new Spec(23, 2f, 64f, Style.BLOCKS, "config.blind-deaf-muted.deafMuffleRange", Category.DEAF),
            // ---- MUTED: how a muted speaker's mic sounds (4-7) ----
            new Spec(4, 20f, 1200f, Style.HZ, "config.blind-deaf-muted.mutedLowpassHz", Category.MUTED),
            new Spec(5, 0f, 50f, Style.GAIN, "config.blind-deaf-muted.mutedVolume", Category.MUTED),
            new Spec(6, 40f, 1200f, Style.HZ, "config.blind-deaf-muted.mutedMegaphoneLowpassHz", Category.MUTED),
            new Spec(7, 0f, 50f, Style.GAIN, "config.blind-deaf-muted.mutedMegaphoneVolume", Category.MUTED),
            // ---- BLIND: myopia post-effect (19-20). Fog knobs 8-9 stay hidden (no live effect). ----
            new Spec(19, 0f, 2f, Style.GAIN, "config.blind-deaf-muted.myopiaBlurStrength", Category.BLIND),
            new Spec(20, 0f, 1f, Style.PERCENT, "config.blind-deaf-muted.myopiaDarkness", Category.BLIND),
            // ---- RELIEF: relief potion stats and deaf-relief downsides (tinnitus + ghosts) ----
            new Spec(-1, 0, 0, Style.HEADER, "config.blind-deaf-muted.cat.relief.general", Category.RELIEF),
            new Spec(16, 0f, 1f, Style.PERCENT, "config.blind-deaf-muted.reliefReductionPercent", Category.RELIEF),
            new Spec(17, 1f, 32f, Style.BLOCKS, "config.blind-deaf-muted.reliefRangeBlocks", Category.RELIEF),
            new Spec(18, 5f, 300f, Style.SECONDS, "config.blind-deaf-muted.reliefDurationSeconds", Category.RELIEF),
            new Spec(-1, 0, 0, Style.HEADER, "config.blind-deaf-muted.cat.deaf", Category.RELIEF),
            new Spec(24, 0f, 2f, Style.GAIN, "config.blind-deaf-muted.deafReliefTinnitusVolume", Category.RELIEF),
            new Spec(25, 0.1f, 5f, Style.SECONDS, "config.blind-deaf-muted.deafReliefTinnitusFadeSeconds", Category.RELIEF),
            new Spec(26, 1f, 30f, Style.SECONDS, "config.blind-deaf-muted.deafReliefTinnitusDurationSeconds", Category.RELIEF),
            new Spec(27, 1f, 60f, Style.SECONDS, "config.blind-deaf-muted.deafReliefVoicesIntervalMinSeconds", Category.RELIEF),
            new Spec(28, 1f, 60f, Style.SECONDS, "config.blind-deaf-muted.deafReliefVoicesIntervalMaxSeconds", Category.RELIEF),
            new Spec(29, 2f, 64f, Style.BLOCKS, "config.blind-deaf-muted.deafReliefVoicesNearbyRangeBlocks", Category.RELIEF),
            new Spec(-1, 0, 0, Style.HEADER, "config.blind-deaf-muted.cat.muted", Category.RELIEF),
            new Spec(30, 0.5f, 60f, Style.SECONDS, "config.blind-deaf-muted.mutedReliefNoiseIntervalMinSeconds", Category.RELIEF),
            new Spec(31, 0.5f, 60f, Style.SECONDS, "config.blind-deaf-muted.mutedReliefNoiseIntervalMaxSeconds", Category.RELIEF),
            new Spec(32, 0f, 2f, Style.GAIN, "config.blind-deaf-muted.mutedReliefNoiseVolume", Category.RELIEF),
            new Spec(-1, 0, 0, Style.HEADER, "config.blind-deaf-muted.cat.blind", Category.RELIEF),
            new Spec(33, 0f, 1f, Style.PERCENT, "config.blind-deaf-muted.blindReliefNauseaStrength", Category.RELIEF),
            // ---- OTHER: events, randomizer, megaphone timing ----
            new Spec(11, 0.5f, 30f, Style.MINUTES, "config.blind-deaf-muted.eventMinMinutes", Category.OTHER),
            new Spec(12, 0.5f, 60f, Style.MINUTES, "config.blind-deaf-muted.eventMaxMinutes", Category.OTHER),
            new Spec(13, 0f, 1f, Style.PERCENT, "config.blind-deaf-muted.randomizerChestChance", Category.OTHER),
            new Spec(14, 1f, 30f, Style.SECONDS, "config.blind-deaf-muted.megaphoneBurstSeconds", Category.OTHER),
            new Spec(15, 5f, 600f, Style.SECONDS, "config.blind-deaf-muted.megaphoneCooldownSeconds", Category.OTHER),
    };

    /** Working copy edited by the sliders; rebuilt into a ModConfig on each send. */
    private final float[] working = ClientConfigState.get().toArray();
    private final List<ParamSlider> sliders = new ArrayList<>();
    private final List<ParamHeader> headers = new ArrayList<>();

    /** Which tab is showing. Kept across init() calls (resize / tab switch) so the view is stable. */
    private Category activeCategory = Category.DEAF;
    /** Tab buttons, rebuilt each init(); rendered + click-routed manually like the other widgets. */
    private final List<ButtonWidget> tabButtons = new ArrayList<>();

    // Scroll state — the knob grid can be taller than the window (small screen / big GUI scale),
    // so it lives in a clipped, scrollable viewport while the Reset/Done buttons stay pinned.
    private int scrollY = 0;      // current scroll offset in px (0 = top)
    private int maxScroll = 0;    // 0 when everything already fits
    private int viewportTop, viewportBottom;
    private ButtonWidget resetButton, doneButton;
    private ParamSlider focusedSlider;   // drag + keyboard target (sliders aren't Screen children here)
    private boolean scrollbarDragging;
    private static final int SCROLL_STEP = 20;
    private static final int SCROLLBAR_W = 4;

    public ConfigScreen() {
        super(Text.translatable("config.blind-deaf-muted.title"));
    }

    @Override
    protected void init() {
        sliders.clear();
        headers.clear();
        tabButtons.clear();

        // ---- Tab row across the top: one button per category, active tab shown greyed (disabled) ----
        int tabW = 96, tabH = 20, tabGap = 4;
        Category[] cats = Category.values();
        int totalW = cats.length * tabW + (cats.length - 1) * tabGap;
        int tabX = this.width / 2 - totalW / 2;
        int tabY = 30;
        for (Category cat : cats) {
            ButtonWidget tab = ButtonWidget.builder(Text.translatable(cat.labelKey), b -> {
                if (activeCategory != cat) {
                    activeCategory = cat;
                    scrollY = 0;
                    clearAndInit(); // rebuild the screen for the new tab
                }
            }).dimensions(tabX, tabY, tabW, tabH).build();
            tab.active = (cat != activeCategory); // the active tab is disabled → reads as "selected"
            addDrawableChild(tab);   // for click routing (via super.mouseClicked)
            tabButtons.add(tab);     // for manual rendering (this screen doesn't call super.render)
            tabX += tabW + tabGap;
        }

        // ---- Sliders for the active tab only ----
        int sliderW = 150, sliderH = 20, gapY = 22, topY = 58;
        int leftX = this.width / 2 - sliderW - 5;
        int rightX = this.width / 2 + 5;

        List<Spec> shown = new ArrayList<>();
        for (Spec s : SPECS) if (s.cat() == activeCategory) shown.add(s);

        int col = 0;
        int currentY = topY;
        for (Spec spec : shown) {
            if (spec.style == Style.HEADER) {
                if (col == 1) {
                    currentY += gapY; // finish current row
                }
                currentY += 10; // header top margin
                ParamHeader header = new ParamHeader(spec, this.width / 2, currentY);
                headers.add(header);
                currentY += 20; // header height
                col = 0; // reset column for next items
            } else {
                int x = (col == 0) ? leftX : rightX;
                ParamSlider slider = new ParamSlider(spec, x, currentY, sliderW, sliderH);
                slider.baseY = currentY;
                sliders.add(slider);
                if (col == 1) {
                    currentY += gapY;
                }
                col = (col + 1) % 2;
            }
        }
        if (col == 1) currentY += gapY;
        int contentBottom = currentY;

        // Buttons pinned to the bottom of the window, always on screen regardless of scroll.
        int buttonsY = this.height - 28;
        resetButton = ButtonWidget.builder(
                        Text.translatable("config.blind-deaf-muted.reset"), b -> resetDefaults())
                .dimensions(leftX, buttonsY, sliderW, 20).build();
        doneButton = ButtonWidget.builder(
                        Text.translatable("gui.done"), b -> close())
                .dimensions(rightX, buttonsY, sliderW, 20).build();
        addDrawableChild(resetButton);
        addDrawableChild(doneButton);

        // The scrollable viewport sits between the tab row and the buttons.
        viewportTop = topY - 4;
        viewportBottom = buttonsY - 6;
        maxScroll = Math.max(0, contentBottom - viewportBottom);
        scrollY = net.minecraft.util.math.MathHelper.clamp(scrollY, 0, maxScroll);
        reflow();
    }

    /** Reposition every slider from its laid-out base Y minus the current scroll offset. */
    private void reflow() {
        for (ParamSlider s : sliders) s.setY(s.baseY - scrollY);
        for (ParamHeader h : headers) h.setY(h.baseY - scrollY);
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
        } else {
            // The server hasn't advertised this channel — wrong mod version on the server,
            // or the player is in singleplayer. Surface it so it's not a silent no-op.
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.sendMessage(
                        Text.literal("[BDM] Config NOT sent — server may be missing the mod or running an old version")
                                .formatted(net.minecraft.util.Formatting.RED), false);
            }
        }
    }

    @Override
    public void close() {
        sendUpdate(); // final sync — catches keyboard nudges that never fired a mouse release
        super.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFF);

        // Tab row (above the clipped viewport, always visible).
        for (ButtonWidget tab : tabButtons) tab.render(context, mouseX, mouseY, delta);

        // Clip the sliders to the viewport so scrolled-out rows don't bleed over the title/buttons.
        context.enableScissor(0, viewportTop, this.width, viewportBottom);
        for (ParamSlider s : sliders) s.render(context, mouseX, mouseY, delta);
        for (ParamHeader h : headers) h.render(context);
        context.disableScissor();

        renderScrollbar(context);

        // Buttons live outside the clip so they're always visible.
        resetButton.render(context, mouseX, mouseY, delta);
        doneButton.render(context, mouseX, mouseY, delta);
    }

    private void renderScrollbar(DrawContext context) {
        if (maxScroll <= 0) return; // everything fits — no bar
        int trackX = this.width - SCROLLBAR_W - 2;
        int trackH = viewportBottom - viewportTop;
        context.fill(trackX, viewportTop, trackX + SCROLLBAR_W, viewportBottom, 0x80000000);
        int thumbH = Math.max(20, (int) ((long) trackH * trackH / (trackH + maxScroll)));
        int thumbY = viewportTop + (int) ((long) (trackH - thumbH) * scrollY / maxScroll);
        context.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, 0xFFAAAAAA);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0) {
            scrollY = net.minecraft.util.math.MathHelper.clamp(
                    scrollY - (int) (verticalAmount * SCROLL_STEP), 0, maxScroll);
            reflow();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Scrollbar thumb / track jump.
        if (maxScroll > 0 && mouseX >= this.width - SCROLLBAR_W - 2 && mouseX <= this.width - 2
                && mouseY >= viewportTop && mouseY < viewportBottom) {
            scrollbarDragging = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        // Sliders (only when the click is inside the visible viewport).
        if (mouseY >= viewportTop && mouseY < viewportBottom) {
            for (ParamSlider s : sliders) {
                if (s.mouseClicked(mouseX, mouseY, button)) {
                    focusedSlider = s;
                    return true;
                }
            }
        }
        focusedSlider = null;
        return super.mouseClicked(mouseX, mouseY, button); // buttons
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrollbarDragging) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        if (focusedSlider != null && focusedSlider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollbarDragging = false;
        if (focusedSlider != null) {
            // Routes to SliderWidget.onRelease → sendUpdate(). Keep the ref for keyboard nudges.
            return focusedSlider.mouseReleased(mouseX, mouseY, button)
                    || super.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (focusedSlider != null && focusedSlider.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Map a mouse Y within the scrollbar track onto the scroll offset. */
    private void updateScrollFromMouse(double mouseY) {
        int trackH = viewportBottom - viewportTop;
        double frac = (mouseY - viewportTop) / Math.max(1, trackH);
        scrollY = net.minecraft.util.math.MathHelper.clamp((int) (frac * maxScroll), 0, maxScroll);
        reflow();
    }

    private final class ParamHeader {
        private final Text text;
        private final int x;
        int baseY;
        private int y;

        ParamHeader(Spec spec, int x, int y) {
            this.text = Text.translatable(spec.labelKey()).formatted(net.minecraft.util.Formatting.YELLOW, net.minecraft.util.Formatting.BOLD);
            this.x = x;
            this.baseY = y;
            this.y = y;
        }

        void setY(int y) {
            this.y = y;
        }

        void render(DrawContext context) {
            context.drawCenteredTextWithShadow(textRenderer, text, x, y, 0xFFFFFF);
        }
    }

    /** A slider bound to one {@code working[index]} knob, mapping the normalized 0..1 handle
     *  position onto the knob's [min,max] range and formatting the label for its style. */
    private final class ParamSlider extends SliderWidget {
        private final Spec spec;
        /** Un-scrolled Y in the grid; reflow() sets the live Y = baseY - scrollY. */
        int baseY;

        ParamSlider(Spec spec, int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(), normalized(spec, working[spec.index()]));
            this.spec = spec;
            this.baseY = y;
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
            case HFGAIN -> String.format("%.2f%%", v * 100f);
            case HEADER -> "";
        };
    }
}
