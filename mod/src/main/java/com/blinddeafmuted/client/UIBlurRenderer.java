package com.blinddeafmuted.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.opengl.GL30;

/**
 * Renders a real GPU blur on a screen region using downscale/upscale.
 */
public final class UIBlurRenderer {
    private UIBlurRenderer() {}

    private static Framebuffer fboDown1;
    private static Framebuffer fboDown2;
    private static int cachedMainW, cachedMainH;

    public static void blurRegion(DrawContext context, int guiLeft, int guiTop,
                                  int guiRight, int guiBottom, float strength) {
        if (strength <= 0.001f) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer mainFbo = mc.getFramebuffer();
        int fbW = mainFbo.textureWidth;
        int fbH = mainFbo.textureHeight;
        if (fbW <= 0 || fbH <= 0) return;

        // Ensure Minecraft's batch is flushed to the main framebuffer
        context.draw();

        double scale = mc.getWindow().getScaleFactor();
        int pixLeft   = clamp((int) (guiLeft * scale), 0, fbW);
        int pixRight  = clamp((int) (guiRight * scale), 0, fbW);
        int pixTop    = clamp((int) (guiTop * scale), 0, fbH);
        int pixBottom = clamp((int) (guiBottom * scale), 0, fbH);

        // Framebuffer Y=0 is bottom
        int glBottom = fbH - pixBottom;
        int glTop    = fbH - pixTop;

        if (pixRight <= pixLeft || glTop <= glBottom) return;

        // Determine downscale factor based on strength (2 to 10)
        int factor = Math.max(2, (int) (strength * 10));
        int down1W = Math.max(4, fbW / factor);
        int down1H = Math.max(4, fbH / factor);
        int down2W = Math.max(2, down1W / 2);
        int down2H = Math.max(2, down1H / 2);

        try {
            ensureFbos(fbW, fbH, down1W, down1H, down2W, down2H);

            int prevReadFb = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int prevDrawFb = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

            // Step 1: Downscale main -> fboDown1. 
            // VERY IMPORTANT: Use GL_NEAREST here! If mainFbo has MSAA, GL_LINEAR will fail and result in black FBO.
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fboDown1.fbo);
            GL30.glBlitFramebuffer(0, 0, fbW, fbH, 0, 0, down1W, down1H, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);

            // Step 2: Downscale fboDown1 -> fboDown2 (GL_LINEAR is safe here because our FBOs are simple)
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fboDown1.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fboDown2.fbo);
            GL30.glBlitFramebuffer(0, 0, down1W, down1H, 0, 0, down2W, down2H, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_LINEAR);

            // Step 3: Upscale fboDown2 -> fboDown1
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fboDown2.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fboDown1.fbo);
            GL30.glBlitFramebuffer(0, 0, down2W, down2H, 0, 0, down1W, down1H, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_LINEAR);

            // Step 4: Upscale fboDown1 -> mainFbo (ONLY the target region)
            int srcLeft   = pixLeft   * down1W / fbW;
            int srcRight  = pixRight  * down1W / fbW;
            int srcBottom = glBottom  * down1H / fbH;
            int srcTop    = glTop     * down1H / fbH;

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fboDown1.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mainFbo.fbo);
            GL30.glBlitFramebuffer(srcLeft, srcBottom, srcRight, srcTop, pixLeft, glBottom, pixRight, glTop, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_LINEAR);

            // Restore Minecraft GL state
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFb);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFb);

        } catch (Exception e) {
            // Failsafe
        }
    }

    private static void ensureFbos(int mainW, int mainH,
                                   int d1W, int d1H, int d2W, int d2H) {
        if (fboDown1 != null && cachedMainW == mainW && cachedMainH == mainH
                && fboDown1.textureWidth == d1W && fboDown1.textureHeight == d1H) {
            return;
        }
        cleanup();
        fboDown1 = new SimpleFramebuffer(d1W, d1H, false);
        fboDown2 = new SimpleFramebuffer(d2W, d2H, false);
        fboDown1.setClearColor(0, 0, 0, 0);
        fboDown2.setClearColor(0, 0, 0, 0);
        fboDown1.initFbo(d1W, d1H);
        fboDown2.initFbo(d2W, d2H);
        
        // initFbo permanently changes the OpenGL viewport to the tiny FBO size.
        // We MUST restore it to the main window's framebuffer size immediately,
        // otherwise all subsequent UI rendering (like the inventory) will be drawn miniature!
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        com.mojang.blaze3d.systems.RenderSystem.viewport(0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());

        cachedMainW = mainW;
        cachedMainH = mainH;
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(val, max));
    }

    public static void cleanup() {
        if (fboDown1 != null) { fboDown1.delete(); fboDown1 = null; }
        if (fboDown2 != null) { fboDown2.delete(); fboDown2 = null; }
    }
}
