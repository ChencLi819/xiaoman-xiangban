package com.xiaoman.memo.domain

/* 内置随手记标签：新建对话框 / 编辑页 / 详情页换标签三处共用同一份，
   避免各处硬编码导致漂移（此前点滴新建对话框漏了「想买」）。
   自定义标签另行拼接（custom_tags 表）。 */
val BUILTIN_TAGS: List<String> = listOf("生活", "想法", "约定", "待办", "想买")

/** 内置标签 + 用户自定义标签名，供选择器统一使用 */
fun allTags(custom: List<String>): List<String> = BUILTIN_TAGS + custom
