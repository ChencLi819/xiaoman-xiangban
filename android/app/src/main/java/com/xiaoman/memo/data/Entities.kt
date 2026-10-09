package com.xiaoman.memo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey


/* 归属约定：author = her|him；owner = shared|mine|partner；
   同步协议（v4 起，所有同步表统一）：
   - uuid：跨设备稳定内容键（本地自增 id 两端不一致，不能做键）；
   - updatedAt：修改时间戳，LWW（新者胜；时间戳打平时 host 侧胜出）；
   - deleted：墓碑位，删除传播，同步成功后 30 天物理清理。
   uuid 默认值 = 每次新建行随机生成；copy()/编辑路径自动保留。 */

@Entity(tableName = "memos")
data class MemoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val tag: String,                       // 生活 想法 约定 待办 想买
    val author: String,                    // her | him
    val isTodo: Boolean = false,
    val done: Boolean = false,
    val privateOnly: Boolean = false,
    val owner: String = "shared",
    val pinned: Boolean = false,
    val reminder: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "") val uuid: String = java.util.UUID.randomUUID().toString(),
    /* B1：完成标记时间（LWW 用） */
    @ColumnInfo(defaultValue = "0") val doneAt: Long = 0,
    /* B3：kind = memo|event；事件用 eventDate（可补录过去日期）+ note（备注） */
    @ColumnInfo(defaultValue = "memo") val kind: String = "memo",
    @ColumnInfo(defaultValue = "") val eventDate: String = "",
    @ColumnInfo(defaultValue = "") val note: String = "",
)

@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "") val uuid: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
)

@Entity(
    tableName = "checklist_items",
    indices = [Index("listId")],
)
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val text: String,
    val author: String,
    val checked: Boolean = false,
    val checkedAt: Long = 0,
    @ColumnInfo(defaultValue = "") val uuid: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
)

@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                      // yyyy-MM-dd；空串 = 不限日期（B2）
    val time: String,                      // HH:mm，可为空串
    val title: String,
    val note: String = "",
    val remind: Boolean = true,
    val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "") val uuid: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
)

@Entity(tableName = "quarrels")
data class QuarrelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                      // yyyy-MM-dd
    val text: String,                      // 标题：一句话说清这次因为什么
    /* v5 起：可选的「详细内容」，与点滴一致 —— 想写多详细都可以，不写也不影响统计 */
    @ColumnInfo(defaultValue = "") val note: String = "",
    val fault: String,                     // me | ta | both | none
    val emotions: String,                  // 逗号分隔
    val heat: Int,                         // 1..5
    val cardText: String = "",
    val cardCat: String = "",
    val cardLv: Int = 0,
    val cardMeet: Boolean = false,
    val pact: String = "",
    val resolved: Boolean = false,
    val resolvedAt: Long = 0,
    val createdAt: Long,
    val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "") val uuid: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
)

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cat: String,                       // like | hate | never | rule
    val who: String,                       // her | him | both
    val text: String,
    val line: Boolean = false,             // 底线
    val agreed: Boolean = true,            // 需要对方确认
    val createdAt: Long,
    val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "") val uuid: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
)

/* B4：daily=true 为「当日状态」记录（症状/情绪/备注），不参与周期计算；
   recordDate = 记录当日（列表展示与排序用它，而非 start）。
   v4 起去掉 start 的唯一索引（每日记录与经期区间可能同日）。 */
@Entity(tableName = "cycles")
data class CycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val start: String,                     // yyyy-MM-dd；daily 记录=记录当日
    val end: String,                       // 空串=进行中
    val flow: Int = 1,                     // 0少 1中 2多
    val pain: Int = 0,                     // 0没有..3很痛
    val mood: String = "",
    val symptoms: String = "",             // 逗号分隔
    val note: String = "",
    @ColumnInfo(defaultValue = "") val uuid: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "") val recordDate: String = "",
    @ColumnInfo(defaultValue = "0") val daily: Boolean = false,
)

@Entity(tableName = "letters")
data class LetterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val author: String,                    // her | him
    val date: String,
    val images: String = "",               // 文件绝对路径，逗号分隔
    val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "") val uuid: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
)

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,                      // 应用私有目录内文件
    val ts: Long,
    val author: String = "both",           // her | him | both
    val note: String = "",
    val deleted: Boolean = false,
)

@Entity(tableName = "act_marks")
data class ActMarkEntity(
    @PrimaryKey val text: String,
    val fav: Boolean = false,
    val played: Boolean = false,
    val updatedAt: Long,
)

/* 用户自定义标签（备忘的 tag 字段存标签名，内置标签除外）。
   v4 起去掉 name 唯一索引（墓碑行不能占名字），去重由应用层完成。 */
@Entity(tableName = "custom_tags")
data class CustomTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "") val uuid: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
)

/* 互动/翻篇池的内容覆盖：内置 520 条为静态种子，用户的增删改记在此表。
   kind = act|pool；op = add|edit|del；origin = 被改/删的内置原文（add 行为空）。
   直接删除本表行也走墓碑（v4 起），防止被对端同步回来。 */
@Entity(tableName = "pool_overrides", indices = [Index(value = ["kind", "origin"])])
data class PoolOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val op: String,
    val origin: String = "",
    val text: String = "",
    val cat: String = "",
    val meet: Boolean = false,
    val lv: Int = 0,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "") val uuid: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
)
