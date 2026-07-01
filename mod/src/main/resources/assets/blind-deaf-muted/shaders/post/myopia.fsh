#version 150

uniform sampler2D InSampler;
uniform sampler2D InDepthSampler;
uniform vec2 InSize;    // render target size in px — used to keep the vignette circle round
uniform vec2 BlurDir;   // (1,0) on pass 1, (0,1) on pass 2 — vignette only on pass 2
uniform float Intensity; // 0 = soft (cane held), 1 = hard (no cane) — set per pipeline

in vec2 texCoord;
in vec2 sampleStep; // oneTexel * BlurDir (from the vanilla post/blur vertex shader)

out vec4 fragColor;

// --- Two intensities of myopia, blended by the Intensity uniform ---------------
// SOFT = cane held (the pipeline blind-deaf-muted:myopia, Intensity 0): a generous
// clear hole, gentle blur, floor so the edges are only dimmed — you get usable sight.
// HARD = no cane (blind-deaf-muted:myopia_hard, Intensity 1): a tiny clear hole, heavy
// blur that starts almost at the camera, and a near-black surround — barely any sight.
// Every "_S" (soft) / "_H" (hard) pair below is mix()ed by Intensity in main().

// Distance (in blocks) staying sharp, and where the blur maxes out.
const float SHARP_BLOCKS_S     = 0.3;
const float SHARP_BLOCKS_H     = 0.12;
const float FULL_BLUR_BLOCKS_S = 1.5;
const float FULL_BLUR_BLOCKS_H = 0.7;
// Max per-axis blur spread, in texels, at full strength. Higher = mushier far field.
const float MAX_TEXEL_RADIUS_S = 50.0;
const float MAX_TEXEL_RADIUS_H = 60.0;
// Vignette radii, aspect-corrected screen units (0 = centre, ~0.5 = screen edge).
const float VIGNETTE_CLEAR_S = 0.48; // inside this radius: fully visible
const float VIGNETTE_CLEAR_H = 0.30;
const float VIGNETTE_BLACK_S = 0.72; // past this radius: darkest
const float VIGNETTE_BLACK_H = 0.50;
// Darkest the overlay ever gets (0 = pure black, 0.30 = 70% opaque so shapes show).
const float VIGNETTE_MIN_VIS_S = 0.30;
const float VIGNETTE_MIN_VIS_H = 0.12;

// How many taps per side. More = smoother blur, slightly more cost. (Must be const.)
const int TAPS = 22;
// Minecraft's camera near plane. Turns the non-linear depth buffer back into an
// approximate eye-space distance in blocks:  z ~= near / (1 - depth).
const float NEAR = 0.05;
// ------------------------------------------------------------------------------

void main() {
    // Resolve this frame's params from the soft/hard pair via the Intensity uniform.
    float sharpBlocks    = mix(SHARP_BLOCKS_S,     SHARP_BLOCKS_H,     Intensity);
    float fullBlurBlocks = mix(FULL_BLUR_BLOCKS_S, FULL_BLUR_BLOCKS_H, Intensity);
    float maxTexelRadius = mix(MAX_TEXEL_RADIUS_S, MAX_TEXEL_RADIUS_H, Intensity);
    float vClear         = mix(VIGNETTE_CLEAR_S,   VIGNETTE_CLEAR_H,   Intensity);
    float vBlack         = mix(VIGNETTE_BLACK_S,   VIGNETTE_BLACK_H,   Intensity);
    float vMinVis        = mix(VIGNETTE_MIN_VIS_S, VIGNETTE_MIN_VIS_H, Intensity);

    float d = texture(InDepthSampler, texCoord).r;
    float dist = NEAR / max(1.0e-4, 1.0 - d); // approx distance to the fragment, in blocks
    float amt = smoothstep(sharpBlocks, fullBlurBlocks, dist);
    float radius = amt * maxTexelRadius;

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

    // Tunnel-vision vignette — applied ONLY on the second (vertical) pass so it isn't
    // squared by running twice. Dark at the edges, clear circle in the middle.
    if (BlurDir.y > 0.5) {
        vec2 p = texCoord - 0.5;
        p.x *= InSize.x / max(1.0, InSize.y); // correct aspect so the clear zone is a circle
        float r = length(p);
        float vis = 1.0 - smoothstep(vClear, vBlack, r);
        vis = max(vis, vMinVis); // floor darkness so it isn't always pure black
        color.rgb *= vis;
    }

    fragColor = color;
}
