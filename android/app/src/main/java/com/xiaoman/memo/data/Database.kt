package com.xiaoman.memo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MemoEntity::class, ChecklistEntity::class, ChecklistItemEntity::class,
        PlanEntity::class, QuarrelEntity::class, RuleEntity::class,
        CycleEntity::class, LetterEntity::class, PhotoEntity::class, ActMarkEntity::class,
        CustomTagEntity::class, PoolOverrideEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class XiaomanDb : RoomDatabase() {
    abstract fun dao(): XiaomanDao

    companion object {
        @Volatile private var inst: XiaomanDb? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `custom_tags` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_tags_name` ON `custom_tags` (`name`)")
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `pool_overrides` (`id` INTEGER NOT NULL, `kind` TEXT NOT NULL, `op` TEXT NOT NULL, `origin` TEXT NOT NULL, `text` TEXT NOT NULL, `cat` TEXT NOT NULL, `meet` INTEGER NOT NULL, `lv` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pool_overrides_kind_origin` ON `pool_overrides` (`kind`, `origin`)")
            }
        }

        /* v3→v4：全部同步表补 uuid（跨设备稳定键）+ updatedAt（LWW）+ deleted（墓碑）。
           新列全部带 defaultValue（与实体 @ColumnInfo 声明一致，Room 校验才通过），
           uuid 用 randomblob 逐行回填；updatedAt 用表内已有时间戳尽量还原。
           cycles 去掉 start 唯一索引（每日记录可同日），custom_tags 去掉 name 唯一索引（墓碑不占名字）。 */
        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // memos：uuid（跨设备稳定键）+ B1 完成时间 + B3 事件字段
                db.execSQL("ALTER TABLE `memos` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `memos` ADD COLUMN `doneAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `memos` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'memo'")
                db.execSQL("ALTER TABLE `memos` ADD COLUMN `eventDate` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `memos` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''")

                // 清单
                db.execSQL("ALTER TABLE `checklists` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `checklists` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `checklists` SET `updatedAt` = `createdAt`")
                db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `checklist_items` SET `updatedAt` = `checkedAt`")

                // 约定 / 雨过 / 分寸
                db.execSQL("ALTER TABLE `plans` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `plans` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `quarrels` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `quarrels` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `quarrels` SET `updatedAt` = `createdAt`")
                db.execSQL("ALTER TABLE `rules` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `rules` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `rules` SET `updatedAt` = `createdAt`")

                // 周期：B4 字段 + 去唯一索引
                db.execSQL("DROP INDEX IF EXISTS `index_cycles_start`")
                db.execSQL("ALTER TABLE `cycles` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `cycles` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `cycles` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `cycles` ADD COLUMN `recordDate` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `cycles` ADD COLUMN `daily` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `cycles` SET `recordDate` = `start`")

                // 情书 / 标签 / 池覆盖
                db.execSQL("ALTER TABLE `letters` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `letters` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DROP INDEX IF EXISTS `index_custom_tags_name`")
                db.execSQL("ALTER TABLE `custom_tags` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `custom_tags` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `custom_tags` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `custom_tags` SET `updatedAt` = `createdAt`")
                db.execSQL("ALTER TABLE `pool_overrides` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `pool_overrides` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `pool_overrides` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `pool_overrides` SET `updatedAt` = `createdAt`")

                // 回填跨设备稳定键（uuid 为空的行统一生成）
                listOf(
                    "memos", "checklists", "checklist_items", "plans", "quarrels",
                    "rules", "cycles", "letters", "custom_tags", "pool_overrides",
                ).forEach { t ->
                    db.execSQL("UPDATE `$t` SET `uuid` = lower(hex(randomblob(16))) WHERE `uuid` = ''")
                }
            }
        }

        /* v4→v5：雨过加「详细内容」列（与点滴的 note 一样，可写可不写）。
           纯新增列 + defaultValue，不做任何破坏性操作；老记录的 note 为空串，语义等价于"没写详细内容"。 */
        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `quarrels` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): XiaomanDb =
            inst ?: synchronized(this) {
                inst ?: Room.databaseBuilder(context.applicationContext, XiaomanDb::class.java, "xiaoman.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    // ⚠️ 不加 fallbackToDestructiveMigration*：任何情况下都不允许 Room 清库重建，
                    // 升降级遇到无法迁移的库宁可崩溃（崩溃日志可查），也不能静默抹掉用户数据
                    .build()
                    .also { inst = it }
            }
    }
}
