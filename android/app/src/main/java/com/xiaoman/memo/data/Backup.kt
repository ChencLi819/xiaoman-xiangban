package com.xiaoman.memo.data

import com.xiaoman.memo.domain.CycleMath
import com.xiaoman.memo.domain.Pools
import org.json.JSONArray
import org.json.JSONObject

/* 数据导出 / 导入 + 同步快照（JSON）。
   v2 格式：所有行带 uuid / updatedAt / deleted（同步 LWW + 墓碑所需）；
   清单条目从嵌套结构改为顶层 checklistItems 扁平数组（键 uuid + listUuid）。
   导入兼容 v1（旧版备份，无 uuid）与 v2。照片二进制不入备份。 */
object Backup {

    private fun freshUuid(): String = java.util.UUID.randomUUID().toString()

    /** 同步/备份用：直接从 DB 组装全量快照（含墓碑行，供对端合并）
     *  ⚠️「仅我看」（privateOnly）的备忘是**本机私有**：新增、修改、删除（含墓碑）一律不进同步快照，
     *  从数据源头保证它不上传。用户在「点滴」里看到的隐私承诺由此兑现。
     *  注意：若一条已同步出去的共同备忘之后被改成「仅我看」，对端会保留它最后一份旧副本
     *  （我们这边不再上传任何关于它的信息，包括删除墓碑，否则等于泄露「有一条私密记录」）。 */
    suspend fun exportSyncSnapshot(db: XiaomanDb, profile: Profile): String {
        val dao = db.dao()
        return snapshotJson(
            profile = profile,
            memos = dao.memosAllOnce().filter { !it.privateOnly },
            checklists = dao.checklistsAllOnce(),
            checklistItems = dao.checklistItemsAllOnce(),
            plans = dao.plansAllOnce(),
            quarrels = dao.quarrelsAllOnce(),
            rules = dao.rulesAllOnce(),
            cycles = dao.cyclesAllOnce(),
            letters = dao.lettersAllOnce(),
            actMarks = dao.actMarksOnce(),
            customTags = dao.customTagsAllOnce(),
            poolOverrides = dao.poolOverridesAllOnce(),
            moods = dao.moodsAllOnce(),
            mails = dao.mailsAllOnce(),
        )
    }

    /** 手动导出（设置页）：只导未删除内容，UI 展示友好 */
    suspend fun exportFromDb(db: XiaomanDb, profile: Profile): String {
        val dao = db.dao()
        val memos = dao.memosOnce()
        val cycles = dao.cyclesAllOnce().filter { !it.deleted }
        val overrides = dao.poolOverridesOnce()
        val state = AppUiState(
            profile = profile,
            memos = memos,
            checklists = dao.checklistsAllOnce().filter { !it.deleted },
            checklistItems = dao.checklistItemsAllOnce().filter { !it.deleted },
            plans = dao.plansAllOnce().filter { !it.deleted },
            quarrels = dao.quarrelsOnce(),
            rules = dao.rulesAllOnce().filter { !it.deleted },
            cycles = cycles,
            letters = dao.lettersOnce(),
            photos = emptyList(),
            actMarks = dao.actMarksOnce(),
            customTags = dao.customTagsOnce(),
            poolOverrides = overrides,
            moods = dao.moodsAllOnce().filter { !it.deleted },
            mails = dao.mailsAllOnce().filter { !it.deleted },
            actPool = Pools.effectiveAct(overrides),
            qPool = Pools.effectivePool(overrides),
            cycleStats = CycleMath.stats(cycles),
        )
        return snapshotJson(
            profile = state.profile,
            memos = state.memos,
            checklists = state.checklists,
            checklistItems = state.checklistItems,
            plans = state.plans,
            quarrels = state.quarrels,
            rules = state.rules,
            cycles = state.cycles,
            letters = state.letters,
            actMarks = state.actMarks,
            customTags = state.customTags,
            poolOverrides = state.poolOverrides,
            moods = state.moods,
            mails = state.mails,
        )
    }

    private fun snapshotJson(
        profile: Profile,
        memos: List<MemoEntity>,
        checklists: List<ChecklistEntity>,
        checklistItems: List<ChecklistItemEntity>,
        plans: List<PlanEntity>,
        quarrels: List<QuarrelEntity>,
        rules: List<RuleEntity>,
        cycles: List<CycleEntity>,
        letters: List<LetterEntity>,
        actMarks: List<ActMarkEntity>,
        customTags: List<CustomTagEntity>,
        poolOverrides: List<PoolOverrideEntity>,
        moods: List<MoodEntity>,
        mails: List<MailEntity>,
    ): String {
        val root = JSONObject()
        root.put("app", "xiaoman")
        root.put("version", 3)
        root.put("exportedAt", System.currentTimeMillis())
        root.put(
            "profile",
            JSONObject()
                .put("her", profile.herName)
                .put("him", profile.himName)
                .put("anniversary", profile.anniversary)
                .put("identity", profile.identity),
        )
        root.put("memos", JSONArray().apply {
            memos.forEach { m ->
                put(
                    JSONObject()
                        .put("uuid", m.uuid).put("text", m.text).put("tag", m.tag).put("tags", m.tags).put("author", m.author)
                        .put("isTodo", m.isTodo).put("done", m.done).put("doneAt", m.doneAt)
                        .put("kind", m.kind).put("eventDate", m.eventDate).put("note", m.note)
                        .put("privateOnly", m.privateOnly)
                        .put("owner", m.owner).put("pinned", m.pinned).put("reminder", m.reminder)
                        .put("createdAt", m.createdAt).put("updatedAt", m.updatedAt).put("deleted", m.deleted),
                )
            }
        })
        root.put("checklists", JSONArray().apply {
            checklists.forEach { cl ->
                put(JSONObject().put("uuid", cl.uuid).put("name", cl.name).put("updatedAt", cl.updatedAt).put("deleted", cl.deleted))
            }
        })
        root.put("checklistItems", JSONArray().apply {
            val clIdToUuid = checklists.associate { it.id to it.uuid }
            checklistItems.forEach { it0 ->
                put(
                    JSONObject()
                        .put("uuid", it0.uuid).put("listUuid", clIdToUuid[it0.listId] ?: "")
                        .put("text", it0.text).put("author", it0.author)
                        .put("checked", it0.checked).put("checkedAt", it0.checkedAt)
                        .put("updatedAt", it0.updatedAt).put("deleted", it0.deleted),
                )
            }
        })
        root.put("plans", JSONArray().apply {
            plans.forEach { p ->
                put(
                    JSONObject().put("uuid", p.uuid).put("date", p.date).put("time", p.time)
                        .put("title", p.title).put("note", p.note).put("remind", p.remind)
                        .put("updatedAt", p.updatedAt).put("deleted", p.deleted),
                )
            }
        })
        root.put("quarrels", JSONArray().apply {
            quarrels.forEach { q ->
                put(
                    JSONObject()
                        .put("uuid", q.uuid).put("date", q.date).put("text", q.text).put("note", q.note).put("fault", q.fault).put("emotions", q.emotions)
                        .put("heat", q.heat).put("cardText", q.cardText).put("cardCat", q.cardCat).put("cardLv", q.cardLv)
                        .put("cardMeet", q.cardMeet).put("pact", q.pact).put("resolved", q.resolved).put("resolvedAt", q.resolvedAt)
                        .put("createdAt", q.createdAt).put("updatedAt", q.updatedAt).put("deleted", q.deleted),
                )
            }
        })
        root.put("rules", JSONArray().apply {
            rules.forEach { r ->
                put(
                    JSONObject().put("uuid", r.uuid).put("cat", r.cat).put("who", r.who).put("text", r.text)
                        .put("line", r.line).put("agreed", r.agreed).put("createdAt", r.createdAt)
                        .put("updatedAt", r.updatedAt).put("deleted", r.deleted),
                )
            }
        })
        root.put("cycles", JSONArray().apply {
            cycles.forEach { cy ->
                put(
                    JSONObject()
                        .put("uuid", cy.uuid).put("start", cy.start).put("end", cy.end).put("flow", cy.flow).put("pain", cy.pain)
                        .put("mood", cy.mood).put("symptoms", cy.symptoms).put("note", cy.note)
                        .put("recordDate", cy.recordDate).put("daily", cy.daily)
                        .put("updatedAt", cy.updatedAt).put("deleted", cy.deleted),
                )
            }
        })
        root.put("customTags", JSONArray().apply {
            customTags.forEach { t ->
                put(JSONObject().put("uuid", t.uuid).put("name", t.name).put("createdAt", t.createdAt).put("updatedAt", t.updatedAt).put("deleted", t.deleted))
            }
        })
        root.put("poolOverrides", JSONArray().apply {
            poolOverrides.forEach { o ->
                put(
                    JSONObject()
                        .put("uuid", o.uuid).put("kind", o.kind).put("op", o.op).put("origin", o.origin)
                        .put("text", o.text).put("cat", o.cat).put("meet", o.meet).put("lv", o.lv)
                        .put("createdAt", o.createdAt).put("updatedAt", o.updatedAt).put("deleted", o.deleted),
                )
            }
        })
        root.put("letters", JSONArray().apply {
            letters.forEach { l ->
                put(
                    JSONObject().put("uuid", l.uuid).put("title", l.title).put("body", l.body).put("author", l.author)
                        .put("date", l.date).put("images", l.images).put("updatedAt", l.updatedAt).put("deleted", l.deleted),
                )
            }
        })
        root.put("actMarks", JSONArray().apply {
            actMarks.forEach { mk ->
                put(JSONObject().put("text", mk.text).put("fav", mk.fav).put("played", mk.played).put("updatedAt", mk.updatedAt))
            }
        })
        /* v7「心晴」：老版本读到不认识的字段会忽略，新版本读老快照则此数组为空，双向兼容 */
        root.put("moods", JSONArray().apply {
            moods.forEach { md ->
                put(
                    JSONObject().put("uuid", md.uuid).put("date", md.date).put("author", md.author)
                        .put("mood", md.mood).put("createdAt", md.createdAt)
                        .put("updatedAt", md.updatedAt).put("deleted", md.deleted),
                )
            }
        })
        /* v8「云笺」：只增不删。老版本读到未知字段会忽略，新版本读老快照则此数组为空 */
        root.put("mails", JSONArray().apply {
            mails.forEach { ml ->
                put(
                    JSONObject().put("uuid", ml.uuid).put("author", ml.author)
                        .put("title", ml.title).put("body", ml.body)
                        .put("sentAt", ml.sentAt).put("readAt", ml.readAt)
                        .put("updatedAt", ml.updatedAt).put("deleted", ml.deleted),
                )
            }
        })
        return root.toString(2)
    }

    data class ImportResult(val memos: Int, val checklists: Int, val plans: Int, val quarrels: Int, val rules: Int, val cycles: Int, val letters: Int, val tags: Int, val overrides: Int, val moods: Int, val mails: Int, val profile: Boolean) {
        val total: Int get() = memos + checklists + plans + quarrels + rules + cycles + letters + tags + overrides + moods + mails
    }

    /** 追加式导入：带 uuid 的行按 uuid 幂等（同键且更新则覆盖，否则跳过）；
        v1 旧行为内容键新增；周期按 start 覆盖内容；身份信息存在则应用。 */
    suspend fun import(db: XiaomanDb, json: String, settings: SettingsStore): ImportResult {
        val root = JSONObject(json)
        if (root.optString("app") != "xiaoman") throw IllegalArgumentException("不是小满的备份文件")
        val dao = db.dao()
        val now = System.currentTimeMillis()
        var nMemos = 0; var nCls = 0; var nPlans = 0; var nQ = 0; var nR = 0; var nC = 0; var nL = 0; var nTags = 0; var nOv = 0; var nMoods = 0; var nMails = 0; var hasProfile = false

        val prof = root.optJSONObject("profile")
        if (prof != null) {
            val her = prof.optString("her", "")
            val him = prof.optString("him", "")
            val anniv = prof.optString("anniversary", "")
            if (her.isNotBlank() || him.isNotBlank()) {
                settings.setNames(her, him)
                hasProfile = true
            }
            if (anniv.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                settings.setAnniversary(anniv)
                hasProfile = true
            }
        }

        suspend fun <T> arr(name: String, block: suspend (JSONObject) -> T): List<T> {
            val a = root.optJSONArray(name) ?: return emptyList()
            return (0 until a.length()).map { block(a.getJSONObject(it)) }
        }

        suspend fun strs(name: String): List<String> {
            val a = root.optJSONArray(name) ?: return emptyList()
            return (0 until a.length()).map { a.getString(it) }
        }

        val localMemos = dao.memosAllOnce()
        arr("memos") { m ->
            val uuid = m.optString("uuid")
            val match = if (uuid.isNotBlank()) localMemos.find { it.uuid == uuid } else null
            val base = MemoEntity(
                text = m.optString("text"), tag = m.optString("tag", "生活"), author = m.optString("author", "her"),
                /* v2 备份没有 tags 字段：回落到单标签 tag（v1/v2 备份都能正确导入） */
                tags = m.optString("tags").ifBlank { m.optString("tag", "生活") },
                isTodo = m.optBoolean("isTodo"), done = m.optBoolean("done"), doneAt = m.optLong("doneAt", 0),
                kind = m.optString("kind", "memo"), eventDate = m.optString("eventDate"), note = m.optString("note"),
                privateOnly = m.optBoolean("privateOnly"),
                owner = m.optString("owner", "shared"), pinned = m.optBoolean("pinned"), reminder = m.optBoolean("reminder"),
                createdAt = m.optLong("createdAt", now), updatedAt = m.optLong("updatedAt", now),
                deleted = m.optBoolean("deleted"),
            )
            if (match == null) dao.insertMemo(base.copy(uuid = uuid.ifBlank { freshUuid() }))
            else if (base.updatedAt > match.updatedAt) dao.updateMemo(base.copy(id = match.id, uuid = match.uuid))
            nMemos++
        }
        val localCls = dao.checklistsAllOnce()
        val localItems = dao.checklistItemsAllOnce()
        arr("checklists") { cl ->
            val uuid = cl.optString("uuid")
            val name = cl.optString("name")
            val match = if (uuid.isNotBlank()) localCls.find { it.uuid == uuid } else null
            val listId = when {
                match != null -> {
                    if (cl.optLong("updatedAt", 0) > match.updatedAt && name.isNotBlank() && name != match.name) {
                        dao.updateChecklist(match.copy(name = name, updatedAt = cl.optLong("updatedAt", match.updatedAt)))
                    }
                    match.id
                }
                else -> dao.insertChecklist(ChecklistEntity(name = name, createdAt = now, uuid = uuid.ifBlank { freshUuid() }, updatedAt = cl.optLong("updatedAt", now)))
            }
            val items = cl.optJSONArray("items") // v1 嵌套格式
            if (items != null) {
                val existing = localItems.filter { it.listId == listId }
                dao.insertChecklistItems((0 until items.length()).mapNotNull { i ->
                    val o = items.getJSONObject(i)
                    val text = o.optString("text")
                    if (existing.any { it.text == text && it.author == o.optString("author", "her") }) return@mapNotNull null
                    ChecklistItemEntity(listId = listId, text = text, author = o.optString("author", "her"), checked = o.optBoolean("checked"), checkedAt = if (o.optBoolean("checked")) now else 0)
                })
            }
            nCls++
        }
        arr("checklistItems") { o -> // v2 扁平格式
            val uuid = o.optString("uuid")
            if (localItems.any { it.uuid == uuid }) return@arr
            val listUuid = o.optString("listUuid")
            val listId = localCls.find { it.uuid == listUuid }?.id
                ?: dao.insertChecklist(
                    ChecklistEntity(name = "同步的清单", createdAt = now, uuid = listUuid.ifBlank { freshUuid() }, updatedAt = now),
                )
            if (localItems.none { it.listId == listId && it.text == o.optString("text") && it.author == o.optString("author", "her") }) {
                dao.insertChecklistItems(
                    listOf(
                        ChecklistItemEntity(
                            listId = listId, text = o.optString("text"), author = o.optString("author", "her"),
                            checked = o.optBoolean("checked"), checkedAt = o.optLong("checkedAt", 0),
                            uuid = uuid.ifBlank { freshUuid() }, updatedAt = o.optLong("updatedAt", now), deleted = o.optBoolean("deleted"),
                        ),
                    ),
                )
            }
        }
        val localPlans = dao.plansAllOnce()
        arr("plans") { p ->
            val uuid = p.optString("uuid")
            val match = if (uuid.isNotBlank()) localPlans.find { it.uuid == uuid } else null
            val base = PlanEntity(
                date = p.optString("date"), time = p.optString("time"), title = p.optString("title"),
                note = p.optString("note"), remind = p.optBoolean("remind", true),
                deleted = p.optBoolean("deleted"), uuid = uuid.ifBlank { freshUuid() }, updatedAt = p.optLong("updatedAt", now),
            )
            if (match == null) dao.insertPlan(base)
            else if (base.updatedAt > match.updatedAt) dao.updatePlan(base.copy(id = match.id))
            nPlans++
        }
        val localQ = dao.quarrelsAllOnce()
        arr("quarrels") { q ->
            val uuid = q.optString("uuid")
            val resolved = q.optBoolean("resolved")
            val base = QuarrelEntity(
                date = q.optString("date"), text = q.optString("text"), note = q.optString("note"), fault = q.optString("fault", "none"),
                emotions = q.optString("emotions"), heat = q.optInt("heat", 2),
                cardText = q.optString("cardText"), cardCat = q.optString("cardCat"), cardLv = q.optInt("cardLv", 0),
                cardMeet = q.optBoolean("cardMeet"), pact = q.optString("pact"), resolved = resolved,
                resolvedAt = if (resolved) q.optLong("resolvedAt", now) else 0,
                createdAt = q.optLong("createdAt", now), deleted = q.optBoolean("deleted"),
                uuid = uuid.ifBlank { freshUuid() }, updatedAt = q.optLong("updatedAt", now),
            )
            val match = if (uuid.isNotBlank()) localQ.find { it.uuid == uuid }
                else localQ.find { it.text == base.text && it.createdAt == base.createdAt }
            if (match == null) dao.upsertQuarrel(base)
            else if (base.updatedAt > match.updatedAt) dao.upsertQuarrel(base.copy(id = match.id))
            nQ++
        }
        val localRules = dao.rulesAllOnce()
        arr("rules") { r ->
            val uuid = r.optString("uuid")
            val match = if (uuid.isNotBlank()) localRules.find { it.uuid == uuid } else null
            val base = RuleEntity(
                cat = r.optString("cat", "rule"), who = r.optString("who", "both"), text = r.optString("text"),
                line = r.optBoolean("line"), agreed = r.optBoolean("agreed", true), createdAt = r.optLong("createdAt", now),
                deleted = r.optBoolean("deleted"), uuid = uuid.ifBlank { freshUuid() }, updatedAt = r.optLong("updatedAt", now),
            )
            if (match == null) dao.insertRule(base)
            else if (base.updatedAt > match.updatedAt) dao.updateRule(base.copy(id = match.id))
            nR++
        }
        val localCycles = dao.cyclesAllOnce()
        arr("cycles") { cy ->
            val start = cy.optString("start")
            if (start.isBlank()) return@arr
            val uuid = cy.optString("uuid")
            val base = CycleEntity(
                start = start, end = cy.optString("end"), flow = cy.optInt("flow", 1), pain = cy.optInt("pain", 0),
                mood = cy.optString("mood"), symptoms = cy.optString("symptoms"), note = cy.optString("note"),
                uuid = uuid.ifBlank { freshUuid() }, updatedAt = cy.optLong("updatedAt", now),
                deleted = cy.optBoolean("deleted"), recordDate = cy.optString("recordDate", start), daily = cy.optBoolean("daily"),
            )
            val match = if (uuid.isNotBlank()) localCycles.find { it.uuid == uuid } else localCycles.find { it.start == start && !it.daily }
            when {
                match == null -> dao.upsertCycle(base)
                base.updatedAt > match.updatedAt -> dao.updateCycle(base.copy(id = match.id))
            }
            nC++
        }
        val localTags = dao.customTagsAllOnce()
        arr("customTags") { t ->
            val name = t.optString("name")
            if (name.isBlank()) return@arr
            val uuid = t.optString("uuid")
            val match = if (uuid.isNotBlank()) localTags.find { it.uuid == uuid } else localTags.find { it.name == name }
            val base = CustomTagEntity(
                name = name, createdAt = t.optLong("createdAt", now),
                uuid = uuid.ifBlank { freshUuid() }, updatedAt = t.optLong("updatedAt", now), deleted = t.optBoolean("deleted"),
            )
            when {
                match == null -> { dao.insertCustomTag(base); nTags++ }
                base.updatedAt > match.updatedAt -> { dao.updateCustomTag(base.copy(id = match.id)); nTags++ }
            }
        }
        strs("customTags").forEach { name -> // v1 字符串数组格式
            if (name.isNotBlank() && localTags.none { it.name == name }) {
                dao.insertCustomTag(CustomTagEntity(name = name, createdAt = now))
                nTags++
            }
        }
        val localOv = dao.poolOverridesAllOnce()
        arr("poolOverrides") { o ->
            val uuid = o.optString("uuid")
            val base = PoolOverrideEntity(
                kind = o.optString("kind", "act"), op = o.optString("op", "add"), origin = o.optString("origin"),
                text = o.optString("text"), cat = o.optString("cat"), meet = o.optBoolean("meet"), lv = o.optInt("lv"),
                createdAt = o.optLong("createdAt", now), deleted = o.optBoolean("deleted"),
                uuid = uuid.ifBlank { freshUuid() }, updatedAt = o.optLong("updatedAt", now),
            )
            val match = if (uuid.isNotBlank()) localOv.find { it.uuid == uuid }
                else localOv.find { it.kind == base.kind && it.op == base.op && it.origin == base.origin && it.text == base.text }
            if (match == null) dao.insertPoolOverride(base)
            else if (base.updatedAt > match.updatedAt) dao.updatePoolOverride(base.copy(id = match.id))
            nOv++
        }
        val localMoods = dao.moodsAllOnce()
        arr("moods") { md ->
            val date = md.optString("date")
            val author = md.optString("author", "her")
            if (date.isBlank()) return@arr
            val uuid = md.optString("uuid")
            /* 没有 uuid 的老备份：按「日期 + 人」定位（一人一天一条） */
            val match = if (uuid.isNotBlank()) localMoods.find { it.uuid == uuid }
                else localMoods.find { it.date == date && it.author == author }
            val base = MoodEntity(
                date = date, author = author, mood = md.optString("mood", "tian"),
                createdAt = md.optLong("createdAt", now), deleted = md.optBoolean("deleted"),
                uuid = uuid.ifBlank { freshUuid() }, updatedAt = md.optLong("updatedAt", now),
            )
            when {
                match == null -> { dao.upsertMood(base); nMoods++ }
                base.updatedAt > match.updatedAt -> { dao.updateMood(base.copy(id = match.id)); nMoods++ }
            }
        }
        val localLetters = dao.lettersAllOnce()
        arr("letters") { l ->
            val uuid = l.optString("uuid")
            val match = if (uuid.isNotBlank()) localLetters.find { it.uuid == uuid } else null
            val base = LetterEntity(
                title = l.optString("title", "无题"), body = l.optString("body"), author = l.optString("author", "her"),
                date = l.optString("date"), images = l.optString("images"), deleted = l.optBoolean("deleted"),
                uuid = uuid.ifBlank { freshUuid() }, updatedAt = l.optLong("updatedAt", now),
            )
            if (match == null) dao.upsertLetter(base)
            else if (base.updatedAt > match.updatedAt) dao.upsertLetter(base.copy(id = match.id))
            nL++
        }
        val localMails = dao.mailsAllOnce()
        arr("mails") { ml ->
            val sentAt = ml.optLong("sentAt", 0)
            if (sentAt <= 0) return@arr
            val uuid = ml.optString("uuid")
            /* 云笺只能增：deleted 一律按 false 落地，绝不因为备份里带墓碑就抹掉信 */
            val base = MailEntity(
                author = ml.optString("author", "her"), title = ml.optString("title"), body = ml.optString("body"),
                sentAt = sentAt, readAt = ml.optLong("readAt", 0), deleted = false,
                uuid = uuid.ifBlank { freshUuid() }, updatedAt = ml.optLong("updatedAt", now),
            )
            val match = if (uuid.isNotBlank()) localMails.find { it.uuid == uuid } else null
            when {
                match == null -> { dao.insertMail(base); nMails++ }
                base.updatedAt > match.updatedAt -> { dao.updateMail(base.copy(id = match.id)); nMails++ }
            }
        }
        return ImportResult(nMemos, nCls, nPlans, nQ, nR, nC, nL, nTags, nOv, nMoods, nMails, hasProfile)
    }
}
