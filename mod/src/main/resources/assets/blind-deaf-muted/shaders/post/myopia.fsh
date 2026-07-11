#version 150

uniform sampler2D InSampler;
uniform sampler2D InDepthSampler;
uniform vec2 InSize;    // render target size in px — used to keep the vignette circle round
uniform vec2 BlurDir;   // (1,0) on pass 1, (0,1) on pass 2 — vignette only on pass 2
uniform float Intensity; // -1 = relief potion, 0 = soft (cane held), 1 = hard (no cane) — set per pipeline
uniform float BlurStrength; // 0 = no far-field blur, 1 = full effect — live-tuned from config (ConfigScreen)
uniform float Darkness; // 0 = no haze/vignette darkening (pure blur), 1 = full — live-tuned from config

in vec2 texCoord;
in vec2 sampleStep; // oneTexel * BlurDir (from the vanilla post/blur vertex shader)

out vec4 fragColor;

// --- Three intensities of myopia, blended by the Intensity uniform -------------
// RELIEF = Potion of Relief (blind-deaf-muted:myopia_relief, Intensity -1): near-clear
// sight — long sharp range, only a faint far smear, vignette pushed off-screen.
// SOFT = cane held (the pipeline blind-deaf-muted:myopia, Intensity 0): a generous
// clear hole, gentle blur, floor so the edges are only dimmed — you get usable sight.
// HARD = no cane (blind-deaf-muted:myopia_hard, Intensity 1): a tiny clear hole, heavy
// blur that starts almost at the camera, and a near-black surround — barely any sight.
// Every "_R" (relief) / "_S" (soft) / "_H" (hard) triple below is blended by
// pick() in main(): Intensity -1→0 mixes relief→soft, 0→1 mixes soft→hard.

// Distance (in blocks) staying sharp, and where the blur maxes out. Soft (cane) reaches
// further before blurring — the cane "extends" your usable sight a bit.
const float SHARP_BLOCKS_R     = 10.0;
const float SHARP_BLOCKS_S     = 1.55;
const float SHARP_BLOCKS_H     = 0.20;
const float FULL_BLUR_BLOCKS_R = 40.0;
const float FULL_BLUR_BLOCKS_S = 3.4;
const float FULL_BLUR_BLOCKS_H = 1.0;
// Max per-axis blur spread, in texels, at full strength. Higher = mushier far field.
const float MAX_TEXEL_RADIUS_R = 12.0;
const float MAX_TEXEL_RADIUS_S = 50.0;
const float MAX_TEXEL_RADIUS_H = 86.0;
// Vignette radii, aspect-corrected screen units (0 = centre, ~0.5 = screen edge). Soft
// (cane) clear circle is wider — again, the cane extends how much you can see. Hard (no
// cane) is now much closer to soft than before, just a step harder. Relief pushes both
// radii past the screen corner (~0.9 at 16:9) — effectively no vignette.
const float VIGNETTE_CLEAR_R = 0.95; // inside this radius: fully visible
const float VIGNETTE_CLEAR_S = 0.75;
const float VIGNETTE_CLEAR_H = 0.28;
const float VIGNETTE_BLACK_R = 1.60; // past this radius: darkest
const float VIGNETTE_BLACK_S = 0.95;
const float VIGNETTE_BLACK_H = 0.75;
// Dark-GRAY haze the far field + surround fade into (was a pure-black vignette). Distant
// stuff washes into soft dark-gray shapes instead of sharp black silhouettes. Hard mode
// hazes a little more strongly. Lower HAZE_COLOR = darker gray; higher = lighter.
const vec3  HAZE_COLOR      = vec3(0.17);
const float HAZE_STRENGTH_R = 0.12;
const float HAZE_STRENGTH_S = 0.35;
const float HAZE_STRENGTH_H = 0.80;

// How many taps per side. More = smoother blur, slightly more cost. (Must be const.)
const int TAPS = 26;
// Minecraft's camera near plane. Turns the non-linear depth buffer back into an
// approximate eye-space distance in blocks:  z ~= near / (1 - depth).
const float NEAR = 0.05;
// ------------------------------------------------------------------------------

// Resolve one parameter from its relief/soft/hard triple via the Intensity uniform:
// -1→0 blends relief→soft, 0→1 blends soft→hard.
float pick(float r, float s, float h) {
    return mix(r, mix(s, h, clamp(Intensity, 0.0, 1.0)), clamp(Intensity + 1.0, 0.0, 1.0));
}

void main() {
    // Resolve this frame's params from the relief/soft/hard triples via Intensity.
    float sharpBlocks    = pick(SHARP_BLOCKS_R,     SHARP_BLOCKS_S,     SHARP_BLOCKS_H);
    float fullBlurBlocks = pick(FULL_BLUR_BLOCKS_R, FULL_BLUR_BLOCKS_S, FULL_BLUR_BLOCKS_H);
    float maxTexelRadius = pick(MAX_TEXEL_RADIUS_R, MAX_TEXEL_RADIUS_S, MAX_TEXEL_RADIUS_H);
    float vClear         = pick(VIGNETTE_CLEAR_R,   VIGNETTE_CLEAR_S,   VIGNETTE_CLEAR_H);
    float vBlack         = pick(VIGNETTE_BLACK_R,   VIGNETTE_BLACK_S,   VIGNETTE_BLACK_H);
    float hazeStr        = pick(HAZE_STRENGTH_R,    HAZE_STRENGTH_S,    HAZE_STRENGTH_H);

    float d = texture(InDepthSampler, texCoord).r;
    float dist = NEAR / max(1.0e-4, 1.0 - d); // approx distance to the fragment, in blocks
    float amt = smoothstep(sharpBlocks, fullBlurBlocks, dist);
    float radius = amt * maxTexelRadius * BlurStrength; // BlurStrength=0 → no blur, 1 → full

    vec4 color = texture(InSampler, texCoord);
    if (radius >= 0.5) {
        // Far field: average TAPS samples either side along the blur axis.
        vec4 sum = color;
        float wsum = 1.0;
        for (int i = 1; i <= TAPS; i++) {
            float t = float(i) / float(TAPS);
            float off = t * radius;
            float w = 1.0 - t * 0.6; // gentle triangular-ish falloff
            sum += texture(InSampler, texCoord + sampleStep * off) * w;
            sum += texture(InSampler, texCoord - sampleStep * off) * w;
            wsum += 2.0 * w;
        }
        color = sum / wsum;
    }

    // Applied ONLY on the second (vertical) pass, so these run once (not squared by the
    // two blur passes).
    if (BlurDir.y > 0.5) {
        // Gray haze: wash the blurred far field toward dark gray so distant things read as
        // soft dark-gray shapes, not sharp silhouettes. Scales with the blur amount, so the
        // near/clear field is untouched and only the far field greys out. Darkness scales it
        // (0 = no haze, pure blur).
        color.rgb = mix(color.rgb, HAZE_COLOR, amt * hazeStr * Darkness);

        // Tunnel-vision vignette: fade the surround toward the SAME dark gray (not pure
        // black), so the edge is a soft dark-gray stroke rather than a hard black frame.
        // Darkness scales it too: at 0 the surround stays fully visible (vv=1 everywhere).
        vec2 p = texCoord - 0.5;
        p.x *= InSize.x / max(1.0, InSize.y); // correct aspect so the clear zone is a circle
        float r = length(p);
        float vis = 1.0 - smoothstep(vClear, vBlack, r);
        float vv = mix(1.0, vis, Darkness);
        color.rgb = mix(HAZE_COLOR, color.rgb, vv);
    }

    fragColor = color;
}
