#version 150

uniform sampler2D InSampler;
uniform sampler2D InDepthSampler;
uniform vec2 InSize;   // render target size in px — used to keep the vignette circle round
uniform vec2 BlurDir;  // (1,0) on pass 1, (0,1) on pass 2 — vignette only on pass 2

in vec2 texCoord;
in vec2 sampleStep; // oneTexel * BlurDir (from the vanilla post/blur vertex shader)

out vec4 fragColor;

// --- Tunables (calibrate in-game) ---------------------------------------------
// Distance (in blocks) within which everything stays sharp — "the couple of blocks
// right in front of you". Past FULL_BLUR_BLOCKS the image is fully smeared.
const float SHARP_BLOCKS    = 0.3;
const float FULL_BLUR_BLOCKS = 1.5;
// Max per-axis blur spread, in texels, at full strength. Higher = mushier far field.
const float MAX_TEXEL_RADIUS = 60.0;
// How many taps per side. More = smoother blur, slightly more cost.
const int TAPS = 22;
// Minecraft's camera near plane. Used to turn the non-linear depth buffer back into
// an approximate eye-space distance in blocks (far plane is negligible at close range:
//   depth ~= 1 - near/z   =>   z ~= near / (1 - depth)).
const float NEAR = 0.05;
// --- Vignette (tunnel vision): black overlay with a clear circle in the middle ----
// Radii are in aspect-corrected screen units (0 = centre, ~0.5 = screen edge).
const float VIGNETTE_CLEAR = 0.16; // inside this radius: fully visible
const float VIGNETTE_BLACK = 0.40; // past this radius: fully black
// ------------------------------------------------------------------------------

void main() {
    float d = texture(InDepthSampler, texCoord).r;
    float dist = NEAR / max(1.0e-4, 1.0 - d); // approx distance to the fragment, in blocks
    float amt = smoothstep(SHARP_BLOCKS, FULL_BLUR_BLOCKS, dist);
    float radius = amt * MAX_TEXEL_RADIUS;

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
    // squared by running twice. Black at the edges, clear circle in the middle.
    if (BlurDir.y > 0.5) {
        vec2 p = texCoord - 0.5;
        p.x *= InSize.x / max(1.0, InSize.y); // correct aspect so the clear zone is a circle
        float r = length(p);
        float vis = 1.0 - smoothstep(VIGNETTE_CLEAR, VIGNETTE_BLACK, r);
        color.rgb *= vis;
    }

    fragColor = color;
}
