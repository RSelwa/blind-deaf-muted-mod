package com.blinddeafmuted.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;

public class TestViewport {
    public static void test() {
        MinecraftClient mc = MinecraftClient.getInstance();
        RenderSystem.viewport(0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
    }
}
