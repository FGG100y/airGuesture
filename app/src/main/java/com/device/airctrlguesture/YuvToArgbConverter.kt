package com.device.airctrlguesture

import android.graphics.Bitmap
import android.media.Image
import java.nio.ByteBuffer

/**
 * 通用的 YUV_420_888 转 ARGB_8888 Bitmap 工具类
 * 解决 Buffer not large enough for pixels 和 类型不匹配问题
 */
object YuvToArgbConverter {

    /**
      * 将 YUV_420_888 格式的 Image 转换为 ARGB_8888 格式的 Bitmap（无缓存版本）
      */
    fun convert(image: Image, applyMirror: Boolean = false): Bitmap =
        convertWithReuse(image, applyMirror, null, null, null)

    /**
     * 带缓存版本的转换方法，减少GC压力
     * @param reuseBitmap 可复用的Bitmap（尺寸需匹配）
     * @param reusePixels 可复用的像素数组（尺寸需匹配 width * height）
     * @param reuseMirrorPixels 可复用的镜像像素数组（仅镜像时需要）
     */
    fun convertWithReuse(
        image: Image,
        applyMirror: Boolean = false,
        reuseBitmap: Bitmap?,
        reusePixels: IntArray?,
        reuseMirrorPixels: IntArray? = null
    ): Bitmap {
        val width = image.width
        val height = image.height

        // 获取 YUV 三个平面的数据
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        // 计算每个平面的步长（stride = 一行的字节数，可能大于宽度）
        val yStride = yPlane.rowStride
        val uvStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val bitmap = if (reuseBitmap != null && reuseBitmap.width == width && reuseBitmap.height == height) {
            reuseBitmap
        } else {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        val argbPixels = if (reusePixels != null && reusePixels.size == width * height) {
            reusePixels
        } else {
            IntArray(width * height)
        }

        // YUV_420_888 转 ARGB 核心算法
        var pixelIndex = 0
        for (row in 0 until height) {
            for (col in 0 until width) {
                // 计算 Y 分量（显式转换为 Int，解决类型不匹配）
                val yIndex = (row * yStride + col).toInt()
                val y = yBuffer.get(yIndex).toInt() and 0xff

                // 计算 UV 分量（420 格式：每 2x2 像素共享一组 UV）
                val uvRow = row / 2
                val uvCol = col / 2
                // 显式转换为 Int，解决类型不匹配
                val uvIndex = (uvRow * uvStride + uvCol * uvPixelStride).toInt()
                val u = uBuffer.get(uvIndex).toInt() and 0xff
                val v = vBuffer.get(uvIndex).toInt() and 0xff

                // YUV 转 RGB（BT.601 标准）
                val y1 = y - 16
                val u1 = u - 128
                val v1 = v - 128

                var r = (1.164 * y1 + 1.596 * v1).toInt()
                var g = (1.164 * y1 - 0.813 * v1 - 0.391 * u1).toInt()
                var b = (1.164 * y1 + 2.018 * u1).toInt()

                // 限制 RGB 值在 0-255 范围内
                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)

                // 组装 ARGB 像素（A=255 不透明）
                val alpha = 0xff000000.toInt() // 不透明 Alpha 通道（显式转 Int）
                argbPixels[pixelIndex++] = alpha or (r shl 16) or (g shl 8) or b
            }
        }

        if (applyMirror) {
            val mirroredPixels = if (reuseMirrorPixels != null && reuseMirrorPixels.size == width * height) {
                reuseMirrorPixels
            } else {
                IntArray(width * height)
            }
            for (row in 0 until height) {
                for (col in 0 until width) {
                    val originalIndex = (row * width + col).toInt()
                    val mirroredIndex = (row * width + (width - 1 - col)).toInt()
                    mirroredPixels[mirroredIndex] = argbPixels[originalIndex]
                }
            }
            bitmap.setPixels(mirroredPixels, 0, width, 0, 0, width, height)
        } else {
            bitmap.setPixels(argbPixels, 0, width, 0, 0, width, height)
        }

        return bitmap
    }

    /**
      * 处理 ImageProxy 旋转（适配相机预览方向）- 无缓存版本
      */
    fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap =
        rotateBitmapWithReuse(bitmap, rotationDegrees, null)

    /**
     * 带缓存版本的旋转方法
     * @param reuseBitmap 可复用的Bitmap（尺寸需匹配旋转后的尺寸）
     * @return 旋转后的Bitmap（可能是reuseBitmap或新创建的）
     */
    fun rotateBitmapWithReuse(bitmap: Bitmap, rotationDegrees: Int, reuseBitmap: Bitmap?): Bitmap {
        if (rotationDegrees == 0) return bitmap

        val newWidth: Int
        val newHeight: Int
        if (rotationDegrees == 90 || rotationDegrees == 270) {
            newWidth = bitmap.height
            newHeight = bitmap.width
        } else {
            newWidth = bitmap.width
            newHeight = bitmap.height
        }

        if (reuseBitmap != null && reuseBitmap.width == newWidth && reuseBitmap.height == newHeight) {
            val canvas = android.graphics.Canvas(reuseBitmap)
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

            val matrix = android.graphics.Matrix()
            when (rotationDegrees) {
                90 -> {
                    matrix.postRotate(90f)
                    matrix.postTranslate(newWidth.toFloat(), 0f)
                }
                180 -> {
                    matrix.postRotate(180f)
                    matrix.postTranslate(newWidth.toFloat(), newHeight.toFloat())
                }
                270 -> {
                    matrix.postRotate(270f)
                    matrix.postTranslate(0f, newHeight.toFloat())
                }
            }
            canvas.drawBitmap(bitmap, matrix, null)
            return reuseBitmap
        }

        val matrix = android.graphics.Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}