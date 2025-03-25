/*
 * Copyright (c) 2024 ModCore Inc. All rights reserved.
 *
 * This code is part of ModCore Inc.'s Essential Mod repository and is protected
 * under copyright registration # TX0009138511. For the full license, see:
 * https://github.com/EssentialGG/Essential/blob/main/LICENSE
 *
 * You may not use, copy, reproduce, modify, sell, license, distribute,
 * commercialize, or otherwise exploit, or create derivative works based
 * upon, this file or any other in this repository, all of which is reserved by Essential.
 */
package gg.essential.util

import gg.essential.model.util.Color
import gg.essential.universal.UGraphics
import gg.essential.util.image.GpuTexture
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL30

//#if MC>=12105
//$$ import com.mojang.blaze3d.opengl.GlStateManager
//#else
import net.minecraft.client.renderer.GlStateManager
//#endif

//#if MC>=11700
//$$ import org.lwjgl.opengl.GL30.glBindFramebuffer
//$$ import org.lwjgl.opengl.GL30.glFramebufferTexture2D
//$$ import org.lwjgl.opengl.GL30.glGenFramebuffers
//#elseif MC>=11400
//$$ import com.mojang.blaze3d.platform.GlStateManager.bindFramebuffer as glBindFramebuffer
//$$ import com.mojang.blaze3d.platform.GlStateManager.framebufferTexture2D as glFramebufferTexture2D
//$$ import com.mojang.blaze3d.platform.GlStateManager.genFramebuffers as glGenFramebuffers
//#else
import net.minecraft.client.renderer.OpenGlHelper.glBindFramebuffer
import net.minecraft.client.renderer.OpenGlHelper.glFramebufferTexture2D
import net.minecraft.client.renderer.OpenGlHelper.glGenFramebuffers
//#endif

abstract class GlGpuTexture(private val format: GpuTexture.Format) : GpuTexture {
    override fun copyFrom(sources: Iterable<GpuTexture.CopyOp>) {
        val prevScissor = GL11.glGetBoolean(GL11.GL_SCISSOR_TEST)
        if (prevScissor) GL11.glDisable(GL11.GL_SCISSOR_TEST)

        val prevDrawFrameBufferBinding = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        val prevReadFrameBufferBinding = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)

        val attachment = if (format.isColor) GL30.GL_COLOR_ATTACHMENT0 else GL30.GL_DEPTH_ATTACHMENT
        val bufferBit = if (format.isColor) GL11.GL_COLOR_BUFFER_BIT else GL11.GL_DEPTH_BUFFER_BIT

        glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, tmpFrameBuffer)
        glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, tmpFrameBuffer2)
        glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, this.glId, 0)

        for ((src, srcX, srcY, destX, destY, width, height) in sources) {
            glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, src.glId, 0)
            GL30.glBlitFramebuffer(
                srcX, srcY, srcX + width, srcY + height,
                destX, destY, destX + width, destY + height,
                bufferBit, GL11.GL_NEAREST
            )
        }

        glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, 0, 0)
        glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, 0, 0)
        glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFrameBufferBinding)
        glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFrameBufferBinding)

        if (prevScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST)
    }

    override fun clearColor(color: Color) {
        val prevDrawFrameBufferBinding = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, tmpFrameBuffer)
        glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, glId, 0)

        //#if MC>=12105
        //$$ GlStateManager._colorMask(true, true, true, true)
        //#else
        GlStateManager.colorMask(true, true, true, true)
        //#endif
        UGraphics.clearColor(color.r.toFloat() / 255, color.r.toFloat() / 255, color.r.toFloat() / 255, color.a.toFloat() / 255)
        glClear(GL11.GL_COLOR_BUFFER_BIT)

        glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0)
        glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFrameBufferBinding)
    }

    override fun clearDepth(depth: Float) {
        val prevDrawFrameBufferBinding = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, tmpFrameBuffer)
        glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, glId, 0)

        //#if MC>=12105
        //$$ GlStateManager._depthMask(true)
        //#else
        GlStateManager.depthMask(true)
        //#endif
        UGraphics.clearDepth(depth.toDouble())
        glClear(GL11.GL_DEPTH_BUFFER_BIT)

        glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0)
        glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFrameBufferBinding)
    }

    private fun glClear(bits: Int) {
        val previousScissorState = GL11.glGetBoolean(GL11.GL_SCISSOR_TEST)
        GL11.glDisable(GL11.GL_SCISSOR_TEST)

        GL11.glClear(bits)

        if (previousScissorState) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST)
        }
    }

    override fun readPixelColor(x: Int, y: Int): Color {
        val prevReadFrameBufferBinding = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, tmpFrameBuffer)
        glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, glId, 0)
        val result = glReadPixelColor(x, y)
        glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0)
        glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFrameBufferBinding)
        return result
    }

    override fun readPixelDepth(x: Int, y: Int): Float {
        val prevReadFrameBufferBinding = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, tmpFrameBuffer)
        glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, glId, 0)
        val result = glReadPixelDepth(x, y)
        glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0)
        glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFrameBufferBinding)
        return result
    }

    companion object {
        private val tmpFrameBuffer by lazy { glGenFramebuffers() }
        private val tmpFrameBuffer2 by lazy { glGenFramebuffers() }
        private val tmpFloatBuffer = BufferUtils.createFloatBuffer(4)

        private fun glReadPixelColor(x: Int, y: Int): Color {
            GL11.glReadPixels(
                x,
                y,
                1,
                1,
                GL_RGBA,
                GL_FLOAT,
                tmpFloatBuffer,
            )
            return with(tmpFloatBuffer) {
                Color(
                    r = (get(0) * 255).toUInt().toUByte(),
                    g = (get(0) * 255).toUInt().toUByte(),
                    b = (get(0) * 255).toUInt().toUByte(),
                    a = (get(0) * 255).toUInt().toUByte(),
                )
            }
        }

        private fun glReadPixelDepth(x: Int, y: Int): Float {
            GL11.glReadPixels(
                x,
                y,
                1,
                1,
                GL_DEPTH_COMPONENT,
                GL_FLOAT,
                tmpFloatBuffer,
            )
            return tmpFloatBuffer.get(0)
        }
    }
}
