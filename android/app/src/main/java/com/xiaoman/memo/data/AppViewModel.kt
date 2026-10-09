package com.xiaoman.memo.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoman.memo.domain.ActItem
import com.xiaoman.memo.domain.CycleMath
import com.xiaoman.memo.domain.CycleStats
import com.xiaoman.memo.domain.Pools
import com.xiaoman.memo.domain.Quarrel
import com.xiaoman.memo.util.Dates
import com.xiaoman.memo.widget.WidgetUpdater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUiState(
    val profile: Profile = Profile(),
    val memos: List<MemoEntity> = emptyList(),
    val checklists: List<ChecklistEntity> = emptyList(),
    val checklistItems: List<ChecklistItemEntity> = emptyList(),
    val plans: List<PlanEntity> = emptyList(),
    val quarrels: List<QuarrelEntity> = emptyList(),
    val rules: List<RuleEntity> = emptyList(),
    val cycles: List<CycleEntity> = emptyList(),
    val letters: List<LetterEntity> = emptyList(),
    val photos: List<PhotoEntity> = emptyList(),
    val actMarks: List<ActMarkEntity> = emptyList(),
    val customTags: List<CustomTagEntity> = emptyList(),
    val poolOverrides: List<PoolOverrideEntity> = emptyList(),
    val actPool: List<ActItem> = emptyList(),
    val qPool: List<Quarrel.PoolItem> = emptyList(),
    val cycleStats: CycleStats? = null,
    val phaseToday: CycleMath.Phase? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = XiaomanDb.get(app)
    private val dao = db.dao()
    val settings = SettingsStore(app)

    val ui: StateFlow<AppUiState> = combine(
        dao.memos(), dao.checklists(), dao.checklistItems(), dao.plans(),
        dao.quarrels(), dao.rules(), dao.cycles(), dao.letters(), dao.photos(), dao.actMarks(),
        dao.customTags(), dao.poolOverrides(), settings.profile,
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val memos = args[0] as List<MemoEntity>
        @Suppress("UNCHECKED_CAST")
        val cycles = args[6] as List<CycleEntity>
        @Suppress("UNCHECKED_CAST")
        val overrides = args[11] as List<PoolOverrideEntity>
        val st = CycleMath.stats(cycles)
        AppUiState(
            profile = args[12] as Profile,
            memos = memos,
            checklists = args[1] as List<ChecklistEntity>,
            checklistItems = args[2] as List<ChecklistItemEntity>,
            plans = args[3] as List<PlanEntity>,
            quarrels = args[4] as List<QuarrelEntity>,
            rules = args[5] as List<RuleEntity>,
            cycles = cycles,
            letters = args[7] as List<LetterEntity>,
            photos = args[8] as List<PhotoEntity>,
            actMarks = args[9] as List<ActMarkEntity>,
            customTags = args[10] as List<CustomTagEntity>,
            poolOverrides = overrides,
            actPool = Pools.effectiveAct(overrides),
            qPool = Pools.effectivePool(overrides),
            cycleStats = st,
            phaseToday = CycleMath.phaseOf(Dates.fmt(Dates.today()), st),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppUiState())

    private fun refreshWidgets() {
        WidgetUpdater.updateAll(getApplication())
        SyncScheduler.request(getApplication()) // 同步端：数据变更防抖自动上传
    }

    fun nameOf(author: String): String =
        if (author == "her") ui.value.profile.herName.ifBlank { "她" }
        else ui.value.profile.himName.ifBlank { "他" }

    suspend fun daoMemo(id: Long): MemoEntity? = dao.memo(id)
    fun memoFlow(id: Long): kotlinx.coroutines.flow.Flow<MemoEntity?> = dao.memoOnce(id)
    suspend fun daoQuarrel(id: Long): QuarrelEntity? = dao.quarrel(id)
    suspend fun daoLetter(id: Long): LetterEntity? = dao.letter(id)

    /* ── 备忘 ── */
    fun addQuickMemo(text: String, onDone: () -> Unit = {}) = viewModelScope.launch {
        val id = settings.profileOnce().identity
        if (text.isBlank()) return@launch
        dao.insertMemo(MemoEntity(text = text.trim(), tag = "生活", author = id, createdAt = now(), updatedAt = now()))
        refreshWidgets(); onDone()
    }

    /** 从首页「要一起做的」添加备忘待办 */
    fun addTodoMemo(text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        val id = settings.profileOnce().identity
        dao.insertMemo(MemoEntity(text = text.trim(), tag = "待办", author = id, isTodo = true, createdAt = now(), updatedAt = now()))
        refreshWidgets()
    }

    /** 点滴页「＋ 新建事件」：标题 + 可选发生日期（可补录）+ 可选备注
     *  privateOnly（仅我看）：只留在本机，不进同步快照（见 Backup.exportSyncSnapshot） */
    fun addEventMemo(title: String, date: String, note: String, privateOnly: Boolean = false, onDone: () -> Unit = {}) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        val id = settings.profileOnce().identity
        dao.insertMemo(
            MemoEntity(
                text = title.trim(), tag = "生活", author = id, kind = "event",
                eventDate = date, note = note.trim(), privateOnly = privateOnly,
                owner = if (privateOnly) "mine" else "shared",
                createdAt = now(), updatedAt = now(),
            ),
        )
        refreshWidgets(); onDone()
    }

    /* 泛化新建：任意标签的备忘都支持 名称(text) + 详细内容(note) 分层存储 */
    fun addTaggedMemo(text: String, tag: String, note: String, privateOnly: Boolean = false, onDone: () -> Unit = {}) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        val id = settings.profileOnce().identity
        dao.insertMemo(
            MemoEntity(
                text = text.trim(), tag = tag, author = id, kind = "memo",
                note = note.trim(), privateOnly = privateOnly,
                owner = if (privateOnly) "mine" else "shared",
                createdAt = now(), updatedAt = now(),
            ),
        )
        refreshWidgets(); onDone()
    }

    fun saveMemo(m: MemoEntity) = viewModelScope.launch {
        if (m.id == 0L) dao.insertMemo(m.copy(createdAt = now(), updatedAt = now()))
        else dao.updateMemo(m.copy(updatedAt = now()))
        refreshWidgets()
    }

    fun toggleMemoDone(m: MemoEntity) = viewModelScope.launch {
        dao.updateMemo(m.copy(done = !m.done, doneAt = if (!m.done) now() else 0, updatedAt = now()))
        refreshWidgets()
    }

    fun togglePin(m: MemoEntity) = viewModelScope.launch {
        dao.updateMemo(m.copy(pinned = !m.pinned, updatedAt = now()))
    }

    fun deleteMemo(id: Long) = viewModelScope.launch {
        dao.softDeleteMemo(id, now()); refreshWidgets()
    }

    /* ── 清单 ── */
    fun addChecklist(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) dao.insertChecklist(ChecklistEntity(name = name.trim(), createdAt = now(), updatedAt = now()))
        refreshWidgets()
    }

    fun addChecklistItem(listId: Long, text: String) = viewModelScope.launch {
        if (text.isNotBlank()) dao.insertChecklistItems(
            listOf(ChecklistItemEntity(listId = listId, text = text.trim(), author = settings.profileOnce().identity, updatedAt = now()))
        )
        refreshWidgets()
    }

    fun toggleChecklistItem(item: ChecklistItemEntity) = viewModelScope.launch {
        dao.setItemChecked(item.id, !item.checked, if (!item.checked) now() else 0, now())
        refreshWidgets()
    }

    fun deleteChecklistItem(id: Long) = viewModelScope.launch { dao.softDeleteChecklistItem(id, now()) }
    fun deleteChecklist(id: Long) = viewModelScope.launch { dao.softDeleteChecklist(id, now()); refreshWidgets() }

    fun renameChecklistItem(id: Long, text: String) = viewModelScope.launch {
        if (text.isNotBlank()) { dao.renameChecklistItem(id, text.trim(), now()); refreshWidgets() }
    }

    fun renameChecklist(id: Long, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) { dao.renameChecklist(id, name.trim(), now()); refreshWidgets() }
    }

    /* ── 约定 ── */
    fun addPlan(p: PlanEntity) = viewModelScope.launch {
        dao.insertPlan(p.copy(updatedAt = now())); refreshWidgets()
    }

    fun updatePlan(p: PlanEntity) = viewModelScope.launch {
        dao.updatePlan(p.copy(updatedAt = now())); refreshWidgets()
    }

    fun deletePlan(id: Long) = viewModelScope.launch { dao.softDeletePlan(id, now()); refreshWidgets() }

    /* ── 雨过 ── */
    fun saveQuarrel(q: QuarrelEntity, onDone: (Long) -> Unit) = viewModelScope.launch {
        val id = dao.upsertQuarrel(q.copy(updatedAt = now()))
        refreshWidgets(); onDone(id)
    }

    fun resolveQuarrel(id: Long, resolved: Boolean) = viewModelScope.launch {
        dao.setQuarrelResolved(id, resolved, if (resolved) now() else 0, now())
        refreshWidgets()
    }

    fun deleteQuarrel(id: Long) = viewModelScope.launch { dao.softDeleteQuarrel(id, now()); refreshWidgets() }

    /* ── 分寸 ── */
    fun saveRule(r: RuleEntity) = viewModelScope.launch {
        /* ⚠️ insertRule 是普通 @Insert（不是 REPLACE）：编辑已有记录必须走 update，
           否则主键冲突直接抛异常。这里按 id 分流。 */
        if (r.id == 0L) dao.insertRule(r.copy(createdAt = if (r.createdAt > 0) r.createdAt else now(), updatedAt = now()))
        else dao.updateRule(r.copy(updatedAt = now()))
        refreshWidgets()
    }
    fun deleteRule(id: Long) = viewModelScope.launch { dao.softDeleteRule(id, now()); refreshWidgets() }

    /* ── 周期（含每日记录；修改保留 uuid 走 LWW）── */
    fun saveCycle(c: CycleEntity) = viewModelScope.launch {
        if (c.id == 0L) dao.upsertCycle(c.copy(updatedAt = now()))
        else dao.updateCycle(c.copy(updatedAt = now()))
        refreshWidgets()
    }

    suspend fun daoCycle(id: Long): CycleEntity? = dao.cycle(id)

    fun deleteCycle(id: Long) = viewModelScope.launch { dao.softDeleteCycle(id, now()); refreshWidgets() }

    /* ── 情书 ── */
    fun saveLetter(l: LetterEntity, onDone: () -> Unit = {}) = viewModelScope.launch {
        dao.upsertLetter(l.copy(updatedAt = now())); refreshWidgets(); onDone()
    }

    fun deleteLetter(id: Long) = viewModelScope.launch { dao.softDeleteLetter(id, now()); refreshWidgets() }

    /* ── 相册 ── */
    fun addPhotos(ps: List<PhotoEntity>) = viewModelScope.launch { dao.insertPhotos(ps); refreshWidgets() }
    fun setPhotoAuthor(id: Long, author: String) = viewModelScope.launch { dao.setPhotoAuthor(id, author) }
    fun setPhotoNote(id: Long, note: String) = viewModelScope.launch { dao.setPhotoNote(id, note) }
    fun deletePhoto(id: Long) = viewModelScope.launch { dao.deletePhoto(id); refreshWidgets() }

    /* ── 互动标记 ── */
    fun toggleActFav(text: String) = viewModelScope.launch { toggleAct(text) { it.copy(fav = !it.fav) } }
    fun toggleActPlayed(text: String) = viewModelScope.launch { toggleAct(text) { it.copy(played = !it.played) } }

    /* ── 自定义标签 ── */
    fun addCustomTag(name: String, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val n = name.trim()
        if (n.isEmpty() || n.length > 8) { onDone(false); return@launch }
        if (dao.tagByName(n) != null) { onDone(true); return@launch } // 已存在视为成功（幂等）
        val r = dao.insertCustomTag(CustomTagEntity(name = n, createdAt = now(), updatedAt = now()))
        onDone(r != -1L)
    }

    fun deleteCustomTag(name: String) = viewModelScope.launch { dao.deleteCustomTagByName(name, now()) }

    /* ── 互动/翻篇池自定义（增删改，内置条目走覆盖表）── */
    fun addPoolItem(kind: String, text: String, cat: String, meet: Boolean = false, lv: Int = 0) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        dao.insertPoolOverride(
            PoolOverrideEntity(kind = kind, op = "add", text = text.trim(), cat = cat, meet = meet, lv = lv, createdAt = now(), updatedAt = now()),
        )
    }

    fun editPoolItem(kind: String, origin: String, newText: String, cat: String, meet: Boolean = false, lv: Int = 0) = viewModelScope.launch {
        if (newText.isBlank()) return@launch
        val added = dao.findAddedOverride(kind, origin)
        if (added != null) {
            // 改的是自定义新增条目：就地更新该行（保留 uuid，同步按 LWW 传播）
            dao.updatePoolOverride(added.copy(text = newText.trim(), cat = cat, meet = meet, lv = lv, updatedAt = now()))
            return@launch
        }
        // origin 可能是「上一次修改后的文本」：先回溯到最初的内置原文
        val priorEdit = dao.findEditedOverride(kind, origin)
        val trueOrigin = priorEdit?.origin ?: origin
        if (priorEdit != null) {
            dao.updatePoolOverride(priorEdit.copy(text = newText.trim(), cat = cat, meet = meet, lv = lv, updatedAt = now()))
        } else {
            dao.insertPoolOverride(PoolOverrideEntity(kind = kind, op = "edit", origin = trueOrigin, text = newText.trim(), cat = cat, meet = meet, lv = lv, createdAt = now(), updatedAt = now()))
        }
    }

    fun deletePoolItem(kind: String, origin: String) = viewModelScope.launch {
        val added = dao.findAddedOverride(kind, origin)
        if (added != null) {
            dao.softDeletePoolOverride(added.id, now()) // 墓碑：删除会传播给对方
            return@launch
        }
        // 同上：先回溯到最初的内置原文，再落一条删除
        val priorEdit = dao.findEditedOverride(kind, origin)
        val trueOrigin = priorEdit?.origin ?: origin
        dao.deletePoolOverridesFor(kind, trueOrigin, now())
        dao.insertPoolOverride(PoolOverrideEntity(kind = kind, op = "del", origin = trueOrigin, createdAt = now(), updatedAt = now()))
    }

    private suspend fun toggleAct(text: String, edit: (ActMarkEntity) -> ActMarkEntity) {
        val cur = ui.value.actMarks.find { it.text == text } ?: ActMarkEntity(text = text, updatedAt = now())
        dao.upsertActMark(edit(cur).copy(updatedAt = now()))
    }

    /* ── 设置 ── */
    fun setIdentity(v: String) = viewModelScope.launch { settings.setIdentity(v); refreshWidgets() }
    fun setNames(her: String, him: String) = viewModelScope.launch { settings.setNames(her, him) }
    fun setAnniversary(v: String) = viewModelScope.launch { settings.setAnniversary(v); refreshWidgets() }
    fun setTheme(v: String) = viewModelScope.launch { settings.setTheme(v) }

    /* ── 备份 ── */
    suspend fun exportJson(): String = Backup.exportFromDb(db, settings.profileOnce())

    /** 追加式导入，返回导入条目数；抛异常由 UI 捕获提示 */
    suspend fun importJson(json: String): Backup.ImportResult {
        val r = Backup.import(db, json, settings)
        refreshWidgets()
        return r
    }

    /** 清空全部数据：物理清除所有表 + 情书/相册图片文件；不重灌样例 */
    fun clearAllData(onDone: () -> Unit = {}) = viewModelScope.launch {
        dao.clearEverything()
        com.xiaoman.memo.util.ImageStore.dir(getApplication(), "photos").listFiles()?.forEach { it.delete() }
        com.xiaoman.memo.util.ImageStore.dir(getApplication(), "letters").listFiles()?.forEach { it.delete() }
        settings.markSeeded()
        refreshWidgets()
        onDone()
    }

    /* ── 同步端（BuildConfig.SYNC_ENABLED 时 UI 才暴露）── */
    val syncConfig: StateFlow<SyncConfig> = settings.pair
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), SyncConfig())

    fun setSyncConfig(cfg: SyncConfig) = viewModelScope.launch { settings.setSyncConfig(cfg) }

    fun syncNow(onStep: (String) -> Unit = {}, onResult: (SyncResult) -> Unit) = viewModelScope.launch {
        onResult(SyncEngine.syncNow(getApplication(), db, settings, onStep))
    }

    /* 连接体检：用弹窗里当前填的配置试连一次，不动已保存的配置 */
    fun testConnection(cfg: SyncConfig, onStep: (String) -> Unit = {}, onResult: (String) -> Unit) =
        viewModelScope.launch { onResult(SyncEngine.testConnection(cfg, onStep)) }

    fun generatePairCode(): String = (100000..999999).random().toString()

    private fun now(): Long = System.currentTimeMillis()
}
