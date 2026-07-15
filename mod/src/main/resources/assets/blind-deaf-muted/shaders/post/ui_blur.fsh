#version 150

// Simple 2D Gaussian blur fragment shader for UI elements.
// No depth buffer needed — just blurs the color input uniformly.

uniform sampler2D InSampler;
uniform vec2 InSize;
uniform vec2 OutSize;
uniform vec2 BlurDir;   // (1,0) for horizontal pass, (0,1) for vertical pass
uniform float Radius;   // blur radius in texels

in vec2 texCoord;
in vec2 sampleStep;     // oneTexel * BlurDir from the vanilla blur vertex shader

out vec4 fragColor;

const int MAX_TAPS = 16;

void main() {
    vec4 sum = texture(InSampler, texCoord);
    float wsum = 1.0;

    float r = max(Radius, 0.0);
    int taps = int(min(r, float(MAX_TAPS)));

    for (int i = 1; i <= MAX_TAPS; i++) {
        if (i > taps) break;
        float t = float(i) / max(float(taps), 1.0);
        float off = t * r;
        // Gaussian-ish weight: exp(-2 * t^2)
        float w = exp(-2.0 * t * t);
        sum += texture(InSampler, texCoord + sampleStep * off) * w;
        sum += texture(InSampler, texCoord - sampleStep * off) * w;
        wsum += 2.0 * w;
    }

    fragColor = sum / wsum;
}
