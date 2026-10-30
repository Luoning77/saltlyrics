// SPDX-License-Identifier: GPL-3.0-only
// Adapted from DangoMusic modern/src/mc1.20.1/.../McCompat.kt, see THIRD_PARTY_NOTICES.md.
package com.luoning.saltlyrics.modern

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.LightTexture
import org.joml.Matrix4f
import org.lwjgl.opengl.GL30

internal typealias GlStateManager = com.mojang.blaze3d.platform.GlStateManager
internal object GlCache {
    fun blend(on: Boolean) { if (on) GlStateManager._enableBlend() else GlStateManager._disableBlend() }
    fun colorMask(r: Boolean,g: Boolean,b: Boolean,a: Boolean) = GlStateManager._colorMask(r,g,b,a)
}
internal object McCompat {
    fun projection() = RenderSystem.getProjectionMatrix()
    fun modelView() = RenderSystem.getModelViewMatrix()
    fun beforeDraw() { BufferUploader.invalidate() }
    fun bindFramebuffer(target: Int,id: Int) = GL30.glBindFramebuffer(target,id)
    fun drawText(text: String,x: Float,y: Float,argb: Int,pose: Matrix4f) {
        val mc=Minecraft.getInstance()
        val buffers=mc.renderBuffers().bufferSource()
        BufferUploader.invalidate()
        mc.font.drawInBatch(text,x,y,argb,false,pose,buffers,Font.DisplayMode.SEE_THROUGH,0,LightTexture.FULL_BRIGHT)
        buffers.endBatch()
    }
}
