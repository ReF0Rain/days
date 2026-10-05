package com.example.countdown.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.UUID

/**
 * 自定义背景图的导入与清理。
 *
 * 设计取舍：把相册选中的图片**复制到应用内部存储**，而不是直接保存 content:// URI。
 * 原因：
 *  - 用户删掉原图或清理相册后，卡片背景不会变成一片空白
 *  - 不依赖长期 URI 授权（虽然 PickVisualMedia 可以持久授权，但仍有失效场景）
 *  - 读取路径完全可控，卸载应用即一并清理，不留垃圾
 *
 * 代价是占用一份磁盘空间：这里按"最长边不超过 [MAX_EDGE]"做一次降采样，
 * 避免用户选了一张 12MP 原图就吃掉十几 MB。
 */
object BackgroundImageStore {

    private const val TAG = "BackgroundImageStore"
    private const val DIR_NAME = "backgrounds"

    /** 降采样后的最长边像素，够卡片全屏显示，又不至于太大 */
    private const val MAX_EDGE = 1440

    /** 单张图的最大可接受体积（超过则认为不是照片，拒绝） */
    private const val MAX_BYTES = 20L * 1024 * 1024

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /**
     * 把选择器返回的图片复制进内部存储，返回可用于 Coil 加载的 file:// URI 字符串。
     * 失败时返回 null（调用方提示用户重试）。
     */
    fun import(context: Context, source: Uri): String? {
        return try {
            val resolver = context.contentResolver
            val mime = resolver.getType(source).orEmpty()
            if (mime.isNotEmpty() && !mime.startsWith("image/")) {
                Log.w(TAG, "不是图片类型: $mime")
                return null
            }

            val ext = when {
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                mime.contains("gif") -> "gif"
                else -> "jpg"
            }
            val target = File(dir(context), "${UUID.randomUUID()}.$ext")

            val copied = resolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            if (copied <= 0L || copied > MAX_BYTES) {
                Log.w(TAG, "图片体积异常: $copied")
                target.delete()
                return null
            }

            Log.d(TAG, "已导入背景图: ${target.name} ($copied bytes)")
            Uri.fromFile(target).toString()
        } catch (t: Throwable) {
            Log.w(TAG, "导入背景图失败", t)
            null
        }
    }

    /**
     * 删除不再被任何事件引用的内部背景图。
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

    /** 当前占用空间（设置页/调试用） */
    fun usedBytes(context: Context): Long =
        dir(context).listFiles()?.sumOf { it.length() } ?: 0L
}
