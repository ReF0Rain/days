package com.example.countdown.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * 自定义背景图的导入与清理。
 *
 * 设计取舍：把相册选中的图片**复制并降采样后**存进应用内部存储，而不是直接保存 content:// URI。
 * 原因：
 *  - 用户删掉原图或清理相册后，卡片背景不会变成一片空白
 *  - 不依赖长期 URI 授权，读取路径完全可控，卸载应用即一并清理
 *
 * 关键点：卡片背景的显示区域只有 210dp 高，原图可能有 4000px 宽。
 * 若原样存储，Coil 会按全分辨率解码进内存，多张卡片同时显示有 OOM 风险，
 * 而且白白占用几 MB 磁盘。所以这里统一：
 *   1) 按最长边 [MAX_EDGE] 计算 inSampleSize 解码
 *   2) 再按最长边精确缩放到 [MAX_EDGE]
 *   3) JPEG 以 [JPEG_QUALITY] 质量重编码（PNG 保留原格式以支持透明度）
 */
object BackgroundImageStore {

    private const val TAG = "BackgroundImageStore"
    private const val DIR_NAME = "backgrounds"

    /** 降采样后的最长边像素：覆盖 210dp * 2x/3x 密度绰绰有余 */
    private const val MAX_EDGE = 1440

    /** JPEG 重编码质量 */
    private const val JPEG_QUALITY = 85

    /** 单张图的最大可接受体积（超过则认为不是正常图片，拒绝） */
    private const val MAX_BYTES = 30L * 1024 * 1024

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /**
     * 把选择器返回的图片降采样后存入内部存储，返回可用于 Coil 加载的 file:// URI 字符串。
     * 失败时返回 null（调用方提示用户重试）。
     */
    fun import(context: Context, source: Uri): String? {
        val resolver = context.contentResolver

        // MIME 只作为编码格式的提示，真实格式还要看文件头
        val mime = runCatching { resolver.getType(source) }.getOrNull().orEmpty()
        if (mime.isNotEmpty() && !mime.startsWith("image/")) {
            Log.w(TAG, "不是图片类型: $mime")
            return null
        }

        // 1) 先只读边界，不分配像素内存
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            resolver.openInputStream(source)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            } ?: run {
                Log.w(TAG, "无法打开输入流")
                return null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "读取图片尺寸失败", t)
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "图片尺寸无效: ${bounds.outWidth}x${bounds.outHeight}")
            return null
        }
        val sourceEdge = maxOf(bounds.outWidth, bounds.outHeight)

        // 2) 计算采样率，让解码出来的位图不超过 MAX_EDGE 的两倍，避免解码时内存峰值过高
        var sample = 1
        while (sourceEdge / (sample * 2) >= MAX_EDGE) sample *= 2

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = try {
            resolver.openInputStream(source)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "解码图片失败", t)
            null
        }
        if (decoded == null) {
            Log.w(TAG, "解码结果为空")
            return null
        }

        // 3) 精确缩放到最长边 MAX_EDGE
        val scaled = scaleToMaxEdge(decoded, MAX_EDGE)
        if (scaled !== decoded) decoded.recycle()

        // 4) 写入文件
        val keepAlpha = mime.contains("png") || mime.contains("webp")
        val ext = if (keepAlpha) "png" else "jpg"
        val format = if (keepAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val target = File(dir(context), "${UUID.randomUUID()}.$ext")

        val written = try {
            target.outputStream().use { out ->
                scaled.compress(format, JPEG_QUALITY, out)
            }
        } catch (t: IOException) {
            Log.w(TAG, "写入背景图失败", t)
            false
        } finally {
            scaled.recycle()
        }

        if (!written || !target.exists() || target.length() <= 0L) {
            target.delete()
            Log.w(TAG, "背景图写入结果无效")
            return null
        }
        if (target.length() > MAX_BYTES) {
            target.delete()
            Log.w(TAG, "背景图体积异常: ${target.length()}")
            return null
        }

        Log.d(
            TAG,
            "已导入背景图 ${target.name}：${bounds.outWidth}x${bounds.outHeight} " +
                "(sample=$sample) -> ${MAX_EDGE}px 内，${target.length() / 1024} KB"
        )
        return Uri.fromFile(target).toString()
    }

    /** 等比缩放到最长边不超过 [maxEdge]；已经足够小则原样返回 */
    private fun scaleToMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val edge = maxOf(bitmap.width, bitmap.height)
        if (edge <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / edge.toFloat()
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } catch (t: Throwable) {
            Log.w(TAG, "缩放失败，退回原图", t)
            bitmap
        }
    }

    /**
     * 删除不再需要的内部背景图。
     * 只处理本应用内部存储里的文件，外部 URI 一律忽略。
     */
    fun deleteIfUnused(context: Context, uriString: String?) {
        if (uriString.isNullOrBlank()) return
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme != "file") return
            val file = uri.path?.let { File(it) } ?: return
            val root = dir(context).canonicalPath
            if (!file.canonicalPath.startsWith(root)) return
            if (file.exists() && file.delete()) {
                Log.d(TAG, "已删除背景图: ${file.name}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "删除背景图失败", t)
        }
    }

    /** 当前占用空间 */
    fun usedBytes(context: Context): Long =
        dir(context).listFiles()?.sumOf { it.length() } ?: 0L
}
