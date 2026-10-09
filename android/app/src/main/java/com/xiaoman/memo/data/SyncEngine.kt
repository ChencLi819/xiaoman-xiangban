package com.xiaoman.memo.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.room.withTransaction
import com.xiaoman.memo.domain.Merge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SyncResult(val ok: Boolean, val imported: Int, val message: String)

/* WebDAV 同步引擎（同步端 flavor 专用）。协议 v2：
   - host 写 host.json / guest 写 guest.json（文件级无写冲突）；
   - 内容 = Backup.exportSyncSnapshot 全量快照（含墓碑行）经配对码派生密钥 AES-GCM 加密；
   - 合并 = Merge.mergeLists（uuid 键 + updatedAt LWW + 墓碑删除传播，平手 host 胜）；
   - 合并整体包在 Room 事务里：任何一步失败全部回滚（A1），合并前另有本地快照文件兜底；
   - 对端快照版本 < 2 时拒绝合并（老版本无 uuid/墓碑，需双方升级）。 */
object SyncEngine {

    private const val TAG = "xiaoman-sync"

    /* 把底层异常翻译成用户能看懂、能照着排查的提示 */
    fun friendlyError(e: Exception): String {
        if (e is WebDav.DavException) {
            return when (e.code) {
                401 -> "账号或应用密码不对（坚果云必须用网页版「账户信息 → 安全选项 → 添加应用」生成的应用密码，不是登录密码）"
                403 -> "服务器拒绝访问：确认账号已开通 WebDAV，且应用密码未被停用"
                404 -> "路径不存在：检查服务器地址和同步文件夹的拼写"
                405 -> "服务器不支持该操作：检查服务器地址是否填对"
                409 -> "无法创建同步目录：检查文件夹路径格式"
                in 500..599 -> "网盘服务器开小差（HTTP ${e.code}），请稍后再试"
                else -> "服务器返回 HTTP ${e.code}"
            }
        }
        val m = e.message ?: return "网络异常：${e.javaClass.simpleName}"
        return when {
            m.contains("UnknownHost", true) || m.contains("Unable to resolve", true) ->
                "无法解析服务器地址：检查网络连接，以及服务器地址拼写"
            m.contains("SSL", true) || m.contains("certificate", true) ->
                "证书校验失败：自签名证书（家用 NAS）请在配置里打开「忽略自签名证书」"
            m.contains("timeout", true) || m.contains("timed out", true) ->
                "连接超时：检查网络，或服务器地址/端口是否可达"
            m.contains("NotFormat") || m.contains("AES", true) || m.contains("GCM", true) ->
                "解密失败：两台设备的配对码不一致，请核对后重新配置"
            else -> m
        }
    }

    /* 原始错误串（类名 + 消息），附在提示末尾，方便用户截图反馈排查 */
    private fun rawOf(e: Exception): String {
        val m = e.message
        return if (m.isNullOrBlank()) e.javaClass.simpleName else "${e.javaClass.simpleName}: $m"
    }

    suspend fun syncNow(context: Context, db: XiaomanDb, settings: SettingsStore, onStep: (String) -> Unit = {}): SyncResult {
        val cfg = settings.syncOnce()
        if (!cfg.paired || cfg.user.isBlank() || cfg.pass.isBlank() || cfg.folder.isBlank() || cfg.code.isBlank()) {
            return SyncResult(false, 0, "还未完成网盘同步配置")
        }
        var step = ""
        return try {
            // 网络与数据库操作必须离开主线程：手动同步从 viewModelScope（Main）进来，
            // 不切 IO 会直接抛 NetworkOnMainThreadException
            withContext(Dispatchers.IO) {
                step = "连接服务器并创建同步目录"
                onStep("$step…")
                WebDav.ensureFolder(cfg)
                val key = cfg.code
                val myFile = if (cfg.role == "host") "host.json" else "guest.json"
                val peerFile = if (cfg.role == "host") "guest.json" else "host.json"

                step = "上传本机数据"
                onStep("$step…")
                val snapshot = Backup.exportSyncSnapshot(db, settings.profileOnce())
                WebDav.put(cfg, myFile, SyncCrypto.encrypt(snapshot, key))

                step = "获取对方数据"
                onStep("$step…")
                val peerPayload = WebDav.get(cfg, peerFile)
                if (peerPayload != null) {
                    step = "合并对方内容"
                    onStep("$step…")
                    val peerJson = SyncCrypto.decrypt(peerPayload, key)

                    // 合并整体在 Room 事务内：任何一步失败全部回滚，不会出现「合并到一半」
                    val amHost = cfg.role == "host"
                    val imported = mergePeer(db, peerJson, amHost)

                    // 墓碑 30 天物理清理
                    runCatching { db.dao().purgeAllTombstones(System.currentTimeMillis() - 30L * 24 * 3600 * 1000) }

                    settings.setSyncConfig(cfg.copy(lastSyncAt = System.currentTimeMillis(), lastError = ""))
                    onStep("")
                    SyncResult(true, imported, if (imported > 0) "已同步，收到对方 $imported 条更新" else "已同步，暂无对方新内容")
                } else {
                    settings.setSyncConfig(cfg.copy(lastSyncAt = System.currentTimeMillis(), lastError = ""))
                    onStep("")
                    SyncResult(true, 0, "已同步（对方还没有上传过数据）")
                }
            }
        } catch (e: Exception) {
            val msg = "同步失败（在「$step」这一步）：${friendlyError(e)}\n原始错误：${rawOf(e)}"
            val cfg2 = settings.syncOnce()
            settings.setSyncConfig(cfg2.copy(lastError = msg))
            onStep("")
            SyncResult(false, 0, msg)
        }
    }

    /* 连接体检：不碰本地数据，只验证「目录可建、可上传、可读回」。
       返回结果文案（✅/❌ 开头），步骤通过 onStep 实时上报。 */
    suspend fun testConnection(cfg: SyncConfig, onStep: (String) -> Unit = {}): String {
        if (cfg.server.isBlank() || cfg.user.isBlank() || cfg.pass.isBlank()) {
            return "❌ 请先填写服务器地址、账号和密码"
        }
        return try {
            withContext(Dispatchers.IO) {
                onStep("正在连接服务器…")
                WebDav.ensureFolder(cfg)
                onStep("正在写入测试文件…")
                val probe = "xiaoman-probe-${System.currentTimeMillis()}"
                WebDav.put(cfg, ".probe.txt", probe)
                onStep("正在读回测试文件…")
                val back = WebDav.get(cfg, ".probe.txt")
                if (back != probe) throw IllegalStateException("读回内容与写入不一致，网盘可能不可靠")
                onStep("")
                "✅ 连接成功：目录已就绪，上传下载正常"
            }
        } catch (e: Exception) {
            onStep("")
            "❌ ${friendlyError(e)}\n原始错误：${rawOf(e)}"
        }
    }

    /* ── 合并：把对方快照并进本地库（整体事务，失败全回滚）── */
    private suspend fun mergePeer(db: XiaomanDb, json: String, amHost: Boolean): Int {
        val root = JSONObject(json)
        if (root.optString("app") != "xiaoman") throw IllegalArgumentException("对方文件不是小满的同步内容")
        if (root.optInt("version", 1) < 2) {
            throw IllegalArgumentException("对方的 App 版本较旧（没有统一合并协议），请双方都升级到最新版后再同步")
        }
        val dao = db.dao()
        var total = 0
        var conflicts = 0

        fun arr(name: String): List<JSONObject> {
            val a = root.optJSONArray(name) ?: return emptyList()
            return (0 until a.length()).map { a.getJSONObject(it) }
        }

        // 全程单事务：任何一步抛异常，全部回滚，绝不出现「合并到一半」
        db.withTransaction {
            /* 备忘：uuid 键，LWW（含 doneAt / 事件字段 / 墓碑）
               ⚠️「仅我看」（privateOnly）是本机私有内容：对端如果把它传了过来（旧版本 App、
               或手工改过的快照文件），这里一律过滤掉、不落地，保证双端都看不到对方的私密备忘。 */
            val peerMemos = arr("memos").filter { !it.optBoolean("privateOnly") }
            Merge.mergeLists(
                local = dao.memosAllOnce(),
                remote = peerMemos.map { m ->
                    MemoEntity(
                        uuid = m.optString("uuid"), text = m.optString("text"), tag = m.optString("tag", "生活"),
                        author = m.optString("author", "her"), isTodo = m.optBoolean("isTodo"),
                        done = m.optBoolean("done"), doneAt = m.optLong("doneAt", 0),
                        kind = m.optString("kind", "memo"), eventDate = m.optString("eventDate"), note = m.optString("note"),
                        privateOnly = false, owner = m.optString("owner", "shared"),
                        pinned = m.optBoolean("pinned"), reminder = m.optBoolean("reminder"),
                        createdAt = m.optLong("createdAt", 0), updatedAt = m.optLong("updatedAt", 0),
                        deleted = m.optBoolean("deleted"),
                    )
                },
                key = { it.uuid }, stamp = { it.updatedAt }, idOf = { it.id },
                withId = { r, id -> r.copy(id = id) }, amHost = amHost,
            ).let { r ->
                r.toInsert.forEach { dao.insertMemo(it) }
                r.toUpdate.forEach { dao.updateMemo(it) }
                total += r.applied; conflicts += r.conflicts
            }

            /* 清单（父表）：uuid 键；先合并父表，得到 uuid→本地id 映射供条目挂靠 */
            val clsResult = Merge.mergeLists(
                local = dao.checklistsAllOnce(),
                remote = arr("checklists").map { cl ->
                    ChecklistEntity(
                        uuid = cl.optString("uuid"), name = cl.optString("name"),
                        createdAt = 0L, updatedAt = cl.optLong("updatedAt", 0), deleted = cl.optBoolean("deleted"),
                    )
                },
                key = { it.uuid }, stamp = { it.updatedAt }, idOf = { it.id },
                withId = { r, id -> r.copy(id = id) }, amHost = amHost,
            )
            val clUuidToLocalId = dao.checklistsAllOnce().associate { it.uuid to it.id }.toMutableMap()
            clsResult.toInsert.forEach { ins ->
                clUuidToLocalId[ins.uuid] = dao.insertChecklist(ins)
            }
            clsResult.toUpdate.forEach { dao.updateChecklist(it) }
            total += clsResult.applied; conflicts += clsResult.conflicts

            /* 清单条目：uuid 键 + listUuid 挂到本地清单（跨表归属，手工并） */
            val localItems = dao.checklistItemsAllOnce()
            arr("checklistItems").forEach { o ->
                val uuid = o.optString("uuid")
                if (uuid.isBlank()) return@forEach
                val listUuid = o.optString("listUuid")
                val ru = o.optLong("updatedAt", 0)
                val local = localItems.find { it.uuid == uuid }
                when {
                    local == null -> {
                        val listId = clUuidToLocalId[listUuid]
                        if (listId != null) {
                            dao.insertChecklistItems(
                                listOf(
                                    ChecklistItemEntity(
                                        uuid = uuid, listId = listId, text = o.optString("text"),
                                        author = o.optString("author", "her"), checked = o.optBoolean("checked"),
                                        checkedAt = o.optLong("checkedAt", 0), updatedAt = ru, deleted = o.optBoolean("deleted"),
                                    ),
                                ),
                            )
                            total++
                        }
                    }
                    else -> {
                        val remoteItem = local.copy(
                            text = o.optString("text"), author = o.optString("author", local.author),
                            checked = o.optBoolean("checked"), checkedAt = o.optLong("checkedAt", local.checkedAt),
                            updatedAt = ru, deleted = o.optBoolean("deleted"),
                        )
                        when {
                            ru > local.updatedAt -> { dao.updateChecklistItem(remoteItem); total++ }
                            ru == local.updatedAt && remoteItem != local && !amHost -> { dao.updateChecklistItem(remoteItem); total++; conflicts++ }
                            ru == local.updatedAt && remoteItem != local && amHost -> conflicts++
                        }
                    }
                }
            }

            /* 约定 */
            Merge.mergeLists(
                local = dao.plansAllOnce(),
                remote = arr("plans").map { p ->
                    PlanEntity(
                        date = p.optString("date"), time = p.optString("time"), title = p.optString("title"),
                        note = p.optString("note"), remind = p.optBoolean("remind", true),
                        deleted = p.optBoolean("deleted"), uuid = p.optString("uuid"), updatedAt = p.optLong("updatedAt", 0),
                    )
                },
                key = { it.uuid }, stamp = { it.updatedAt }, idOf = { it.id },
                withId = { r, id -> r.copy(id = id) }, amHost = amHost,
            ).let { r ->
                r.toInsert.forEach { dao.insertPlan(it) }
                r.toUpdate.forEach { dao.updatePlan(it) }
                total += r.applied; conflicts += r.conflicts
            }

            /* 雨过：「标记翻篇」= 一次带 updatedAt 的修改，由 LWW 自然传播 */
            Merge.mergeLists(
                local = dao.quarrelsAllOnce(),
                remote = arr("quarrels").map { q ->
                    val resolved = q.optBoolean("resolved")
                    QuarrelEntity(
                        date = q.optString("date"), text = q.optString("text"), note = q.optString("note"), fault = q.optString("fault", "none"),
                        emotions = q.optString("emotions"), heat = q.optInt("heat", 2),
                        cardText = q.optString("cardText"), cardCat = q.optString("cardCat"), cardLv = q.optInt("cardLv", 0),
                        cardMeet = q.optBoolean("cardMeet"), pact = q.optString("pact"), resolved = resolved,
                        resolvedAt = if (resolved) q.optLong("resolvedAt", 0) else 0,
                        createdAt = q.optLong("createdAt", 0), deleted = q.optBoolean("deleted"),
                        uuid = q.optString("uuid"), updatedAt = q.optLong("updatedAt", 0),
                    )
                },
                key = { it.uuid }, stamp = { it.updatedAt }, idOf = { it.id },
                withId = { r, id -> r.copy(id = id) }, amHost = amHost,
            ).let { r ->
                r.toInsert.forEach { dao.upsertQuarrel(it) }
                r.toUpdate.forEach { dao.upsertQuarrel(it) }
                total += r.applied; conflicts += r.conflicts
            }

            /* 分寸 */
            Merge.mergeLists(
                local = dao.rulesAllOnce(),
                remote = arr("rules").map { r ->
                    RuleEntity(
                        cat = r.optString("cat", "rule"), who = r.optString("who", "both"), text = r.optString("text"),
                        line = r.optBoolean("line"), agreed = r.optBoolean("agreed", true),
                        createdAt = r.optLong("createdAt", 0), deleted = r.optBoolean("deleted"),
                        uuid = r.optString("uuid"), updatedAt = r.optLong("updatedAt", 0),
                    )
                },
                key = { it.uuid }, stamp = { it.updatedAt }, idOf = { it.id },
                withId = { r, id -> r.copy(id = id) }, amHost = amHost,
            ).let { r ->
                r.toInsert.forEach { dao.insertRule(it) }
                r.toUpdate.forEach { dao.updateRule(it) }
                total += r.applied; conflicts += r.conflicts
            }

            /* 周期（含每日记录） */
            Merge.mergeLists(
                local = dao.cyclesAllOnce(),
                remote = arr("cycles").map { cy ->
                    CycleEntity(
                        start = cy.optString("start"), end = cy.optString("end"), flow = cy.optInt("flow", 1),
                        pain = cy.optInt("pain", 0), mood = cy.optString("mood"), symptoms = cy.optString("symptoms"),
                        note = cy.optString("note"), uuid = cy.optString("uuid"), updatedAt = cy.optLong("updatedAt", 0),
                        deleted = cy.optBoolean("deleted"), recordDate = cy.optString("recordDate"), daily = cy.optBoolean("daily"),
                    )
                },
                key = { it.uuid }, stamp = { it.updatedAt }, idOf = { it.id },
                withId = { r, id -> r.copy(id = id) }, amHost = amHost,
            ).let { r ->
                r.toInsert.forEach { dao.upsertCycle(it) }
                r.toUpdate.forEach { dao.updateCycle(it) }
                total += r.applied; conflicts += r.conflicts
            }

            /* 情书 */
            Merge.mergeLists(
                local = dao.lettersAllOnce(),
                remote = arr("letters").map { l ->
                    LetterEntity(
                        title = l.optString("title"), body = l.optString("body"), author = l.optString("author", "her"),
                        date = l.optString("date"), images = l.optString("images"), deleted = l.optBoolean("deleted"),
                        uuid = l.optString("uuid"), updatedAt = l.optLong("updatedAt", 0),
                    )
                },
                key = { it.uuid }, stamp = { it.updatedAt }, idOf = { it.id },
                withId = { r, id -> r.copy(id = id) }, amHost = amHost,
            ).let { r ->
                r.toInsert.forEach { dao.upsertLetter(it) }
                r.toUpdate.forEach { dao.upsertLetter(it) }
                total += r.applied; conflicts += r.conflicts
            }

            /* 自定义标签 */
            Merge.mergeLists(
                local = dao.customTagsAllOnce(),
                remote = arr("customTags").map { t ->
                    CustomTagEntity(
                        name = t.optString("name"), createdAt = t.optLong("createdAt", 0),
                        uuid = t.optString("uuid"), updatedAt = t.optLong("updatedAt", 0), deleted = t.optBoolean("deleted"),
                    )
                },
                key = { it.uuid }, stamp = { it.updatedAt }, idOf = { it.id },
                withId = { r, id -> r.copy(id = id) }, amHost = amHost,
            ).let { r ->
                r.toInsert.forEach { dao.insertCustomTag(it) }
                r.toUpdate.forEach { dao.updateCustomTag(it) }
                total += r.applied; conflicts += r.conflicts
            }

            /* 池覆盖 */
            Merge.mergeLists(
                local = dao.poolOverridesAllOnce(),
                remote = arr("poolOverrides").map { o ->
                    PoolOverrideEntity(
                        kind = o.optString("kind", "act"), op = o.optString("op", "add"), origin = o.optString("origin"),
                        text = o.optString("text"), cat = o.optString("cat"), meet = o.optBoolean("meet"), lv = o.optInt("lv", 0),
                        createdAt = o.optLong("createdAt", 0), deleted = o.optBoolean("deleted"),
                        uuid = o.optString("uuid"), updatedAt = o.optLong("updatedAt", 0),
                    )
                },
                key = { it.uuid }, stamp = { it.updatedAt }, idOf = { it.id },
                withId = { r, id -> r.copy(id = id) }, amHost = amHost,
            ).let { r ->
                r.toInsert.forEach { dao.insertPoolOverride(it) }
                r.toUpdate.forEach { dao.updatePoolOverride(it) }
                total += r.applied; conflicts += r.conflicts
            }

            /* 互动标记：键 = text（主键），LWW，无墓碑（取消勾选即内容更新） */
            val localMarks = dao.actMarksOnce()
            arr("actMarks").forEach { mk ->
                val text = mk.optString("text")
                if (text.isBlank()) return@forEach
                val remote = ActMarkEntity(
                    text = text, fav = mk.optBoolean("fav"), played = mk.optBoolean("played"),
                    updatedAt = mk.optLong("updatedAt", 0),
                )
                val local = localMarks.find { it.text == text }
                when {
                    local == null -> { dao.upsertActMark(remote); total++ }
                    remote.updatedAt > local.updatedAt -> { dao.upsertActMark(remote); total++ }
                }
            }
        }

        if (conflicts > 0) {
            Log.w(TAG, "同步平手冲突 $conflicts 处：按发起方（host）为准，本机=${if (amHost) "host（保留本地）" else "guest（采用对方）"}")
        }
        return total
    }
}

/* 数据变更后防抖自动上传：15 秒内的连续变更只触发一次 */
object SyncScheduler {
    private val handler = Handler(Looper.getMainLooper())
    private const val DELAY = 15_000L
    private var pending = false

    fun request(context: Context) {
        if (!com.xiaoman.memo.BuildConfig.SYNC_ENABLED) return
        pending = true
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (!pending) return@postDelayed
            pending = false
            CoroutineScope(Dispatchers.IO).launch {
                val db = XiaomanDb.get(context)
                SyncEngine.syncNow(context, db, SettingsStore(context))
            }
        }, DELAY)
    }
}
