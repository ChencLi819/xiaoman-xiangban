package com.xiaoman.memo.domain

import com.xiaoman.memo.data.MemoEntity

/* 内置随手记标签：新建对话框 / 编辑页 / 详情页换标签三处共用同一份，
   避免各处硬编码导致漂移（此前点滴新建对话框漏了「想买」）。
   自定义标签另行拼接（custom_tags 表）。 */
val BUILTIN_TAGS: List<String> = listOf("生活", "想法", "约定", "待办", "想买")

/** 内置标签 + 用户自定义标签名，供选择器统一使用 */
fun allTags(custom: List<String>): List<String> = BUILTIN_TAGS + custom

/* ── 多标签（v6）──
   memos.tags 存逗号分隔的标签名；memos.tag 始终是第一项。
   这样旧版本 App 读到的仍是主标签，新版本能拿到全部标签。 */
const val TAG_SEP = ","
const val MAX_TAGS = 6

fun splitTags(raw: String): List<String> =
    raw.split(TAG_SEP).map { it.trim() }.filter { it.isNotBlank() }.distinct()

/** 一条备忘的全部标签；tags 为空（v5 及更早的数据、旧同步快照）时回落到 tag */
fun MemoEntity.tagList(): List<String> = splitTags(tags).ifEmpty { splitTags(tag) }

/** 规范化：去空白、去重、限制数量，并顺手把全角逗号换成半角（标签名里不让用逗号） */
fun normalizeTagList(list: List<String>): List<String> =
    list.map { it.replace('，', ' ').trim() }.filter { it.isNotBlank() }.distinct().take(MAX_TAGS)

/** 写进 tags 列的字符串 */
fun joinTags(list: List<String>): String = normalizeTagList(list).joinToString(TAG_SEP)

/** 写进 tag 列的主标签（第一项）；空列表回落到内置默认 */
fun primaryTag(list: List<String>): String = normalizeTagList(list).firstOrNull() ?: BUILTIN_TAGS.first()

/** 标签是否合法：非空、不超过 8 字、不含分隔符 */
fun isValidTagName(name: String): Boolean =
    name.trim().let { it.isNotEmpty() && it.length <= 8 && !it.contains(',') && !it.contains('，') }
