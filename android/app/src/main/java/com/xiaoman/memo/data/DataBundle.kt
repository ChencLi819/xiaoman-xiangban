package com.xiaoman.memo.data

import android.content.Context
import com.xiaoman.memo.util.ImageStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/* 完整迁移包（.zip）：JSON 备份 + 情书/相册图片。
   用途是「换签名 / 换机器 / debug 版 ↔ 正式版」时把数据整包搬过去——
   普通「数据导出（JSON）」不含图片，图片路径也只在本机有效。

   包结构（固定，便于人工检查）：
     backup.json     —— Backup.exportFromDb 的内容（不含图片文件本身）
     photos.json     —— 相册索引（路径存文件名，导入时按新目录重算）
     files/photos/…  —— 相册图片
     files/letters/… —— 情书图片

   ⚠️ 迁移包含私密内容，导出后请自行保管；导入是「追加式」，不会清空本机已有数据。 */
object DataBundle {

    data class Result(val backup: Backup.ImportResult, val photos: Int)

    private const val META = "backup.json"
    private const val PHOTOS = "photos.json"
    private const val SUB_PHOTOS = "photos"
    private const val SUB_LETTERS = "letters"

    /** 导出到指定文件；返回导出的图片张数 */
    suspend fun export(context: Context, db: XiaomanDb, settings: SettingsStore, out: File): Int {
        val json = Backup.exportFromDb(db, settings.profileOnce())
        val photoRows = db.dao().photosAllOnce().filter { !it.deleted }
        val photoIndex = JSONArray()
        photoRows.forEach { p ->
            photoIndex.put(
                JSONObject().put("name", File(p.path).name)
                    .put("ts", p.ts).put("author", p.author).put("note", p.note),
            )
        }
        var files = 0
        out.parentFile?.mkdirs()
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(META))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(PHOTOS))
            zip.write(photoIndex.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            listOf(SUB_PHOTOS, SUB_LETTERS).forEach { sub ->
                val dir = ImageStore.dir(context, sub)
                dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                    if (!f.isFile) return@forEach
                    zip.putNextEntry(ZipEntry("files/$sub/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    files++
                }
            }
        }
        return files
    }

    /** 从迁移包恢复：先落图片与相册索引，再把 JSON 里指向旧目录的图片路径改成本机路径，最后追加导入。 */
    suspend fun import(context: Context, db: XiaomanDb, settings: SettingsStore, zipFile: File): Result {
        var meta = ""
        var photosJson = ""
        val extracted = mutableMapOf<String, String>()   // 文件名 -> 本机绝对路径
        val cache = File(context.cacheDir, "bundle_import").apply { mkdirs() }

        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            var e: ZipEntry? = zip.nextEntry
            while (e != null) {
                val name = e.name
                // 防目录穿越：只接受固定结构，且名字里不许出现 ..
                if (!name.contains("..")) {
                    when {
                        name == META -> meta = zip.readBytes().toString(Charsets.UTF_8)
                        name == PHOTOS -> photosJson = zip.readBytes().toString(Charsets.UTF_8)
                        name.startsWith("files/$SUB_PHOTOS/") || name.startsWith("files/$SUB_LETTERS/") -> {
                            val sub = if (name.startsWith("files/$SUB_PHOTOS/")) SUB_PHOTOS else SUB_LETTERS
                            val fileName = name.substringAfterLast('/')
                            if (fileName.isNotBlank()) {
                                val target = File(ImageStore.dir(context, sub), fileName)
                                target.outputStream().use { o -> zip.copyTo(o) }
                                extracted["$sub/$fileName"] = target.absolutePath
                                extracted[fileName] = target.absolutePath
                            }
                        }
                        else -> Unit
                    }
                }
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        if (meta.isBlank()) throw IllegalArgumentException("不是小满的完整迁移包（缺少 backup.json）")

        // 旧路径 -> 本机路径：备份里存的是上一台设备/上一个包名的绝对路径，只有文件名可复用
        fun remap(path: String): String {
            val base = File(path).name
            return extracted[base] ?: path
        }

        val root = JSONObject(meta)
        root.optJSONArray("letters")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val raw = o.optString("images")
                if (raw.isBlank()) continue
                val mapped = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    .joinToString(",") { remap(it) }
                o.put("images", mapped)
            }
        }

        val backupResult = Backup.import(db, root.toString(), settings)

        // 相册索引：只补本机不存在、且文件确实落地的
        var added = 0
        if (photosJson.isNotBlank()) {
            val existing = db.dao().photosAllOnce().map { it.path }.toSet()
            val rows = mutableListOf<PhotoEntity>()
            val arr = JSONArray(photosJson)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name")
                val path = extracted[name] ?: continue
                if (!File(path).exists() || path in existing) continue
                rows += PhotoEntity(
                    path = path, ts = o.optLong("ts", System.currentTimeMillis()),
                    author = o.optString("author", "both"), note = o.optString("note"),
                )
            }
            if (rows.isNotEmpty()) {
                db.dao().insertPhotos(rows)
                added = rows.size
            }
        }
        cache.deleteRecursively()
        return Result(backupResult, added)
    }
}
