package com.xiaoman.memo.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "xiaoman_settings")

data class Profile(
    val identity: String = "her",       // 当前身份：her | him
    val herName: String = "",           // 空白状态：由用户在「我的身份」里自行填写
    val himName: String = "",
    val anniversary: String = "",       // 空 = 未设置，「在一起的天数」卡不计算
    val theme: String = "system",       // system | light | dark
)

/* 同步端（BuildConfig.SYNC_ENABLED=true 的 flavor）的 WebDAV 同步配置。
   方案：任意支持 WebDAV 的网盘（坚果云 / Nextcloud / 群晖等 NAS / 自建）；
   同一文件夹内 host 写 host.json，guest 写 guest.json；
   文件内容用配对码派生的 AES-GCM 密钥端到端加密。 */
data class SyncConfig(
    val paired: Boolean = false,
    val server: String = "https://dav.jianguoyun.com/dav/",
    val user: String = "",
    val pass: String = "",              // 应用密码 / WebDAV 密码
    val folder: String = "",            // 如 /xiaoman-123456（双方须指向同一共享目录）
    val role: String = "host",          // host | guest
    val code: String = "",              // 6 位配对码：文件夹约定 + 加密密钥来源
    val trustAll: Boolean = false,      // 忽略自签名证书（家用 NAS 用）
    val lastSyncAt: Long = 0,
    val lastError: String = "",
)

class SettingsStore(private val context: Context) {
    private val K_ID = stringPreferencesKey("identity")
    private val K_HER = stringPreferencesKey("her_name")
    private val K_HIM = stringPreferencesKey("him_name")
    private val K_ANNI = stringPreferencesKey("anniversary")
    private val K_THEME = stringPreferencesKey("theme")
    private val K_SEEDED = booleanPreferencesKey("seeded")
    private val K_PAIR_STATE = stringPreferencesKey("pair_state")
    private val K_PAIR_ROLE = stringPreferencesKey("pair_role")
    private val K_PAIR_CODE = stringPreferencesKey("pair_code")
    private val K_SYNC_SERVER = stringPreferencesKey("sync_server")
    private val K_SYNC_USER = stringPreferencesKey("sync_user")
    private val K_SYNC_PASS = stringPreferencesKey("sync_pass")
    private val K_SYNC_FOLDER = stringPreferencesKey("sync_folder")
    private val K_SYNC_TRUST = booleanPreferencesKey("sync_trust_all")
    private val K_SYNC_LAST = longPreferencesKey("sync_last")
    private val K_SYNC_ERR = stringPreferencesKey("sync_err")

    val profile: Flow<Profile> = context.dataStore.data.map { p ->
        Profile(
            identity = p[K_ID] ?: "her",
            herName = p[K_HER] ?: "",
            himName = p[K_HIM] ?: "",
            anniversary = p[K_ANNI] ?: "",
            theme = p[K_THEME] ?: "system",
        )
    }

    suspend fun profileOnce(): Profile = profile.first()

    suspend fun setIdentity(v: String) = context.dataStore.edit { it[K_ID] = v }
    suspend fun setNames(her: String, him: String) = context.dataStore.edit {
        if (her.isNotBlank()) it[K_HER] = her
        if (him.isNotBlank()) it[K_HIM] = him
    }
    suspend fun setAnniversary(v: String) = context.dataStore.edit { it[K_ANNI] = v }
    suspend fun setTheme(v: String) = context.dataStore.edit { it[K_THEME] = v }
    suspend fun isSeeded(): Boolean = context.dataStore.data.first()[K_SEEDED] ?: false
    suspend fun markSeeded() = context.dataStore.edit { it[K_SEEDED] = true }

    val pair: Flow<SyncConfig> = context.dataStore.data.map { p ->
        SyncConfig(
            paired = (p[K_PAIR_STATE] ?: "none") == "paired",
            role = p[K_PAIR_ROLE] ?: "host",
            code = p[K_PAIR_CODE] ?: "",
            server = p[K_SYNC_SERVER] ?: "https://dav.jianguoyun.com/dav/",
            user = p[K_SYNC_USER] ?: "",
            pass = p[K_SYNC_PASS] ?: "",
            folder = p[K_SYNC_FOLDER] ?: "",
            trustAll = p[K_SYNC_TRUST] ?: false,
            lastSyncAt = p[K_SYNC_LAST] ?: 0,
            lastError = p[K_SYNC_ERR] ?: "",
        )
    }

    suspend fun setSyncConfig(cfg: SyncConfig) = context.dataStore.edit {
        it[K_PAIR_STATE] = if (cfg.paired) "paired" else "none"
        it[K_PAIR_ROLE] = cfg.role
        it[K_PAIR_CODE] = cfg.code
        it[K_SYNC_SERVER] = cfg.server
        it[K_SYNC_USER] = cfg.user
        it[K_SYNC_PASS] = cfg.pass
        it[K_SYNC_FOLDER] = cfg.folder
        it[K_SYNC_TRUST] = cfg.trustAll
        it[K_SYNC_LAST] = cfg.lastSyncAt
        it[K_SYNC_ERR] = cfg.lastError
    }

    suspend fun syncOnce(): SyncConfig = pair.first()
}
