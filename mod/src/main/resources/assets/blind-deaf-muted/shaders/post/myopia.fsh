#version 150

uniform sampler2D InSampler;
uniform sampler2D InDepthSampler;

in vec2 texCoord;
in vec2 sampleStep; // oneTexel * BlurDir (from the vanilla post/blur vertex shader)

out vec4 fragColor;

// --- Tunables (calibrate in-game) ---------------------------------------------
// Distance (in blocks) within which everything stays sharp — "the couple of blocks
// right in front of you". Past FULL_BLUR_BLOCKS the image is fully smeared.
const float SHARP_BLOCKS    = 2.5;
const float FULL_BLUR_BLOCKS = 9.0;
// Max per-axis blur spread, in texels, at full strength. Higher = mushier far field.
const float MAX_TEXEL_RADIUS = 9.0;
// How many taps per side. More = smoother blur, slightly more cost.
const int TAPS = 6;
// Minecraft's camera near plane. Used to turn the non-linear depth buffer back into
// an approximate eye-space distance in blocks (far plane is negligible at close range:
//   depth ~= 1 - near/z   =>   z ~= near / (1 - depth)).
const float NEAR = 0.05;
// ------------------------------------------------------------------------------

void main() {
    float d = texture(InDepthSampler, texCoord).r;
    float dist = NEAR / max(1.0e-4, 1.0 - d); // approx distance to the fragment, in blocks
    float amt = smoothstep(SHARP_BLOCKS, FULL_BLUR_BLOCKS, dist);
    float radius = amt * MAX_TEXEL_RADIUS;

    vec4 center = texture(InSampler, texCoord);
    if (radius < 0.5) {
        fragColor = center; // near field: leave it crisp
        return;
    }

    vec4 sum = center;
    float wsum = 1.0;
    for (int i = 1; i <= TAPS; i++) {
        float t = float(i) / float(TAPS);
        float off = t * radius;
        float w = 1.0 - t * 0.6; // gentle triangular-ish falloff
        sum += texture(InSampler, texCoord + sampleStep * off) * w;
        sum += texture(InSampler, texCoord - sampleStep * off) * w;
        wsum += 2.0 * w;
    }
    fragColor = sum / wsum;
}
