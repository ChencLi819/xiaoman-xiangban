package com.xiaoman.memo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface XiaomanDao {

    /* ── 备忘 ── */
    @Query("SELECT * FROM memos WHERE deleted=0 ORDER BY pinned DESC, createdAt DESC")
    fun memos(): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memos WHERE deleted=0 ORDER BY pinned DESC, createdAt DESC")
    suspend fun memosOnce(): List<MemoEntity>

    @Query("SELECT * FROM memos")
    suspend fun memosAllOnce(): List<MemoEntity>

    @Query("SELECT * FROM memos WHERE id=:id")
    suspend fun memo(id: Long): MemoEntity?

    @Query("SELECT * FROM memos WHERE id=:id")
    fun memoOnce(id: Long): Flow<MemoEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemo(m: MemoEntity): Long

    @Update
    suspend fun updateMemo(m: MemoEntity)

    @Query("UPDATE memos SET deleted=1, updatedAt=:now WHERE id=:id")
    suspend fun softDeleteMemo(id: Long, now: Long)

    @Query("SELECT COUNT(*) FROM memos WHERE deleted=0 AND isTodo=1 AND done=0")
    fun pendingTodoCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM memos WHERE deleted=0 AND isTodo=1 AND done=1")
    fun doneTodoCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM memos WHERE deleted=0")
    fun memoCount(): Flow<Int>

    /* ── 清单 ── */
    @Query("SELECT * FROM checklists WHERE deleted=0 ORDER BY createdAt DESC")
    fun checklists(): Flow<List<ChecklistEntity>>

    @Query("SELECT * FROM checklist_items WHERE deleted=0 AND listId IN (SELECT id FROM checklists WHERE deleted=0) ORDER BY id")
    fun checklistItems(): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklists")
    suspend fun checklistsAllOnce(): List<ChecklistEntity>

    @Query("SELECT * FROM checklist_items")
    suspend fun checklistItemsAllOnce(): List<ChecklistItemEntity>

    @Insert
    suspend fun insertChecklist(c: ChecklistEntity): Long

    @Insert
    suspend fun insertChecklistItems(items: List<ChecklistItemEntity>)

    @Update
    suspend fun updateChecklist(c: ChecklistEntity)

    @Update
    suspend fun updateChecklistItem(i: ChecklistItemEntity)

    @Query("UPDATE checklist_items SET checked=:checked, checkedAt=:at, updatedAt=:now WHERE id=:id")
    suspend fun setItemChecked(id: Long, checked: Boolean, at: Long, now: Long)

    @Query("UPDATE checklist_items SET text=:text, updatedAt=:now WHERE id=:id")
    suspend fun renameChecklistItem(id: Long, text: String, now: Long)

    @Query("UPDATE checklists SET name=:name, updatedAt=:now WHERE id=:id")
    suspend fun renameChecklist(id: Long, name: String, now: Long)

    @Query("UPDATE checklist_items SET deleted=1, updatedAt=:now WHERE id=:id")
    suspend fun softDeleteChecklistItem(id: Long, now: Long)

    @Query("UPDATE checklists SET deleted=1, updatedAt=:now WHERE id=:id")
    suspend fun softDeleteChecklist(id: Long, now: Long)

    /* ── 约定 ── */
    @Query("SELECT * FROM plans WHERE deleted=0 ORDER BY date, time")
    fun plans(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans")
    suspend fun plansAllOnce(): List<PlanEntity>

    @Insert
    suspend fun insertPlan(p: PlanEntity): Long

    @Update
    suspend fun updatePlan(p: PlanEntity)

    @Query("UPDATE plans SET deleted=1, updatedAt=:now WHERE id=:id")
    suspend fun softDeletePlan(id: Long, now: Long)

    /* ── 雨过 ── */
    @Query("SELECT * FROM quarrels WHERE deleted=0 ORDER BY createdAt DESC")
    fun quarrels(): Flow<List<QuarrelEntity>>

    @Query("SELECT * FROM quarrels WHERE id=:id")
    suspend fun quarrel(id: Long): QuarrelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuarrel(q: QuarrelEntity): Long

    @Query("UPDATE quarrels SET resolved=:resolved, resolvedAt=:at, updatedAt=:now WHERE id=:id")
    suspend fun setQuarrelResolved(id: Long, resolved: Boolean, at: Long, now: Long)

    @Query("UPDATE quarrels SET deleted=1, updatedAt=:now WHERE id=:id")
    suspend fun softDeleteQuarrel(id: Long, now: Long)

    /* ── 分寸 ── */
    @Query("SELECT * FROM rules WHERE deleted=0 ORDER BY line DESC, createdAt DESC")
    fun rules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules")
    suspend fun rulesAllOnce(): List<RuleEntity>

    @Insert
    suspend fun insertRule(r: RuleEntity): Long

    @Update
    suspend fun updateRule(r: RuleEntity)

    @Query("UPDATE rules SET deleted=1, updatedAt=:now WHERE id=:id")
    suspend fun softDeleteRule(id: Long, now: Long)

    /* ── 周期（含每日记录）── */
    @Query("SELECT * FROM cycles WHERE deleted=0 ORDER BY start")
    fun cycles(): Flow<List<CycleEntity>>

    @Query("SELECT * FROM cycles")
    suspend fun cyclesAllOnce(): List<CycleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCycle(c: CycleEntity): Long

    @Update
    suspend fun updateCycle(c: CycleEntity)

    @Query("SELECT * FROM cycles WHERE id=:id")
    suspend fun cycle(id: Long): CycleEntity?

    @Query("UPDATE cycles SET deleted=1, updatedAt=:now WHERE id=:id")
    suspend fun softDeleteCycle(id: Long, now: Long)

    @Query("SELECT * FROM quarrels WHERE deleted=0")
    suspend fun quarrelsOnce(): List<QuarrelEntity>

    @Query("SELECT * FROM quarrels")
    suspend fun quarrelsAllOnce(): List<QuarrelEntity>

    @Query("SELECT * FROM letters WHERE deleted=0")
    suspend fun lettersOnce(): List<LetterEntity>

    @Query("SELECT * FROM letters")
    suspend fun lettersAllOnce(): List<LetterEntity>

    @Query("SELECT * FROM act_marks")
    suspend fun actMarksOnce(): List<ActMarkEntity>

    @Query("SELECT * FROM custom_tags WHERE deleted=0")
    suspend fun customTagsOnce(): List<CustomTagEntity>

    @Query("SELECT * FROM custom_tags")
    suspend fun customTagsAllOnce(): List<CustomTagEntity>

    @Query("SELECT * FROM pool_overrides WHERE deleted=0")
    suspend fun poolOverridesOnce(): List<PoolOverrideEntity>

    @Query("SELECT * FROM pool_overrides")
    suspend fun poolOverridesAllOnce(): List<PoolOverrideEntity>

    /* ── 情书 ── */
    @Query("SELECT * FROM letters WHERE deleted=0 ORDER BY date DESC, id DESC")
    fun letters(): Flow<List<LetterEntity>>

    @Query("SELECT * FROM letters WHERE id=:id")
    suspend fun letter(id: Long): LetterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLetter(l: LetterEntity): Long

    @Query("UPDATE letters SET deleted=1, updatedAt=:now WHERE id=:id")
    suspend fun softDeleteLetter(id: Long, now: Long)

    /* ── 相册 ── */
    @Query("SELECT * FROM photos WHERE deleted=0 ORDER BY ts DESC")
    fun photos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id=:id")
    suspend fun photo(id: Long): PhotoEntity?

    @Insert
    suspend fun insertPhotos(ps: List<PhotoEntity>)

    @Query("UPDATE photos SET author=:author WHERE id=:id")
    suspend fun setPhotoAuthor(id: Long, author: String)

    @Query("UPDATE photos SET note=:note WHERE id=:id")
    suspend fun setPhotoNote(id: Long, note: String)

    @Query("DELETE FROM photos WHERE id=:id")
    suspend fun deletePhoto(id: Long)

    /* ── 互动标记 ── */
    @Query("SELECT * FROM act_marks")
    fun actMarks(): Flow<List<ActMarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActMark(m: ActMarkEntity)

    /* ── 自定义标签 ── */
    @Query("SELECT * FROM custom_tags WHERE deleted=0 ORDER BY createdAt")
    fun customTags(): Flow<List<CustomTagEntity>>

    @Query("SELECT * FROM custom_tags WHERE deleted=0 AND name=:name LIMIT 1")
    suspend fun tagByName(name: String): CustomTagEntity?

    @Insert
    suspend fun insertCustomTag(t: CustomTagEntity): Long

    @Update
    suspend fun updateCustomTag(t: CustomTagEntity)

    @Query("UPDATE custom_tags SET deleted=1, updatedAt=:now WHERE name=:name AND deleted=0")
    suspend fun deleteCustomTagByName(name: String, now: Long)

    /* ── 互动/翻篇池内容覆盖 ── */
    @Query("SELECT * FROM pool_overrides WHERE deleted=0")
    fun poolOverrides(): Flow<List<PoolOverrideEntity>>

    @Insert
    suspend fun insertPoolOverride(o: PoolOverrideEntity): Long

    @Update
    suspend fun updatePoolOverride(o: PoolOverrideEntity)

    @Query("UPDATE pool_overrides SET deleted=1, updatedAt=:now WHERE id=:id")
    suspend fun softDeletePoolOverride(id: Long, now: Long)

    @Query("UPDATE pool_overrides SET deleted=1, updatedAt=:now WHERE kind=:kind AND origin=:origin AND deleted=0")
    suspend fun deletePoolOverridesFor(kind: String, origin: String, now: Long)

    @Query("SELECT * FROM pool_overrides WHERE kind=:kind AND op='add' AND text=:text AND deleted=0 LIMIT 1")
    suspend fun findAddedOverride(kind: String, text: String): PoolOverrideEntity?

    @Query("SELECT * FROM pool_overrides WHERE kind=:kind AND op='edit' AND text=:text AND deleted=0 LIMIT 1")
    suspend fun findEditedOverride(kind: String, text: String): PoolOverrideEntity?

    /* ── 墓碑清理（同步成功后调用）：删除超过 30 天的墓碑行 ── */
    @Query("DELETE FROM memos WHERE deleted=1 AND updatedAt < :cutoff")
    suspend fun purgeMemos(cutoff: Long)
    @Query("DELETE FROM checklists WHERE deleted=1 AND updatedAt < :cutoff")
    suspend fun purgeChecklists(cutoff: Long)
    @Query("DELETE FROM checklist_items WHERE deleted=1 AND updatedAt < :cutoff")
    suspend fun purgeChecklistItems(cutoff: Long)
    @Query("DELETE FROM plans WHERE deleted=1 AND updatedAt < :cutoff")
    suspend fun purgePlans(cutoff: Long)
    @Query("DELETE FROM quarrels WHERE deleted=1 AND updatedAt < :cutoff")
    suspend fun purgeQuarrels(cutoff: Long)
    @Query("DELETE FROM rules WHERE deleted=1 AND updatedAt < :cutoff")
    suspend fun purgeRules(cutoff: Long)
    @Query("DELETE FROM cycles WHERE deleted=1 AND updatedAt < :cutoff")
    suspend fun purgeCycles(cutoff: Long)
    @Query("DELETE FROM letters WHERE deleted=1 AND updatedAt < :cutoff")
    suspend fun purgeLetters(cutoff: Long)
    @Query("DELETE FROM custom_tags WHERE deleted=1 AND updatedAt < :cutoff")
    suspend fun purgeCustomTags(cutoff: Long)
    @Query("DELETE FROM pool_overrides WHERE deleted=1 AND updatedAt < :cutoff")
    suspend fun purgePoolOverrides(cutoff: Long)

    @Transaction
    suspend fun purgeAllTombstones(cutoff: Long) {
        purgeMemos(cutoff); purgeChecklists(cutoff); purgeChecklistItems(cutoff)
        purgePlans(cutoff); purgeQuarrels(cutoff); purgeRules(cutoff)
        purgeCycles(cutoff); purgeLetters(cutoff); purgeCustomTags(cutoff)
        purgePoolOverrides(cutoff)
    }

    /* ── 统计 ── */
    @Query("SELECT COUNT(*) FROM checklists WHERE deleted=0")
    fun checklistCount(): Flow<Int>

    /* ── 清空全部数据（设置里的危险操作，软删内容一并物理清除）── */
    @Query("DELETE FROM memos") suspend fun clearMemos()
    @Query("DELETE FROM checklists") suspend fun clearChecklists()
    @Query("DELETE FROM checklist_items") suspend fun clearChecklistItems()
    @Query("DELETE FROM plans") suspend fun clearPlans()
    @Query("DELETE FROM quarrels") suspend fun clearQuarrels()
    @Query("DELETE FROM rules") suspend fun clearRules()
    @Query("DELETE FROM cycles") suspend fun clearCycles()
    @Query("DELETE FROM letters") suspend fun clearLetters()
    @Query("DELETE FROM photos") suspend fun clearPhotos()
    @Query("DELETE FROM act_marks") suspend fun clearActMarks()
    @Query("DELETE FROM custom_tags") suspend fun clearCustomTags()
    @Query("DELETE FROM pool_overrides") suspend fun clearPoolOverrides()

    @Transaction
    suspend fun clearEverything() {
        clearMemos(); clearChecklists(); clearChecklistItems(); clearPlans()
        clearQuarrels(); clearRules(); clearCycles(); clearLetters()
        clearPhotos(); clearActMarks(); clearCustomTags(); clearPoolOverrides()
    }
}
