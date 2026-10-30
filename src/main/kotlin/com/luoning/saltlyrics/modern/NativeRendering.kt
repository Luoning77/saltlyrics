// SPDX-License-Identifier: GPL-3.0-only
// Adapted from DangoMusic ModernGfx.kt world/text paths; see THIRD_PARTY_NOTICES.md.
package com.luoning.saltlyrics.modern

import net.minecraft.client.Minecraft
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13

object RenderPlatform {
    private val mc get()=Minecraft.getInstance()
    val player get()=if(mc.level != null) CameraPlayer() else null
    val fov get()=mc.options.fov().get().toDouble()
    val windowWidth get()=mc.window.width
    val windowHeight get()=mc.window.height
    class CameraPlayer {
        private val camera get()=Minecraft.getInstance().gameRenderer.mainCamera
        val yaw get()=camera.yRot
        val pitch get()=camera.xRot
        val eyeX get()=camera.position.x
        val eyeY get()=camera.position.y
        val eyeZ get()=camera.position.z
    }
}
object NativeWorld {
    val basePose=Matrix4f()
    private val camera get()=Minecraft.getInstance().gameRenderer.mainCamera.position
    val cameraX get()=camera.x
    val cameraY get()=camera.y
    val cameraZ get()=camera.z
    fun resync() {
        Gl.blend(true)
        Gl.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ZERO)
        Gl.depthTest(false); Gl.depthMask(false); Gl.cull(false); Gl.activeTexture(GL13.GL_TEXTURE0)
    }
    fun begin() { Fixed.reset(basePose); resync() }
    fun push(rx: Double,ry: Double,rz: Double,yaw: Float,pitch: Float,scale: Float) {
        Fixed.push()
        Fixed.pose.translate(rx.toFloat(),ry.toFloat(),rz.toFloat())
            .rotateY(Math.toRadians(-yaw.toDouble()).toFloat())
            .rotateX(Math.toRadians(pitch.toDouble()).toFloat()).scale(-scale,-scale,scale)
    }
    fun pop()=Fixed.pop()
}
object NativeText {
    fun width(text: String)=Minecraft.getInstance().font.width(text).toFloat()
    fun push()=Fixed.push()
    fun pop()=Fixed.pop()
    fun translate(x: Float,y: Float) { Fixed.pose.translate(x,y,0f) }
    fun rotate(degrees: Float) { Fixed.pose.rotateZ(Math.toRadians(degrees.toDouble()).toFloat()) }
    fun scale(x: Float,y: Float) { Fixed.pose.scale(x,y,1f) }
    fun pixelText(text: String,x: Float,y: Float,argb: Int,shadow: Boolean) {
        if(shadow) {
            val color=((argb and 0xFCFCFC) shr 2) or (argb and 0xFF000000.toInt())
            val offset=if(text.all { it.code<0x2E80 }) 1f else 0.5f
            McCompat.drawText(text,x+offset,y+offset,color,Fixed.pose)
        }
        McCompat.drawText(text,x,y,argb,Fixed.pose)
        NativeWorld.resync()
    }
}
