package com.xiaoman.memo.data

import android.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import java.security.MessageDigest
import java.security.SecureRandom
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/* 通用 WebDAV 客户端（PUT/GET/MKCOL + Basic Auth），适配坚果云 / Nextcloud / 群晖等 NAS / 自建服务。
   ⚠️ 必须用 OkHttp：Android 自带 HttpURLConnection 的方法白名单（GET/POST/PUT…）里没有 MKCOL，
   setRequestMethod("MKCOL") 会直接抛 ProtocolException。
   健壮性：
   - 建目录逐级 MKCOL（多数服务器不支持一次创建多级），已存在（405/301/302）视为成功；
   - PUT 遇 409（父目录缺失）自动补建后重试一次；
   - 可选「忽略自签名证书」（家用 NAS 常见），仅对该配置生效。 */
object WebDav {
    class DavException(val code: Int, msg: String) : Exception("HTTP $code: $msg")

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val trustAllCtx: SSLContext by lazy {
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        }
        SSLContext.getInstance("TLS").apply { init(null, arrayOf<javax.net.ssl.TrustManager>(tm), SecureRandom()) }
    }
    private val trustAllTm = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    }

    /* 按是否忽略证书缓存客户端，避免每次请求新建连接池 */
    private val clients = mutableMapOf<Boolean, OkHttpClient>()
    private fun client(cfg: SyncConfig): OkHttpClient = clients.getOrPut(cfg.trustAll) {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .apply {
                if (cfg.trustAll) {
                    sslSocketFactory(trustAllCtx.socketFactory, trustAllTm)
                    hostnameVerifier { _, _ -> true }
                }
            }
            .build()
    }

    private fun url(cfg: SyncConfig, path: String): String =
        cfg.server.trimEnd('/') + "/" + path.trimStart('/')

    private fun request(cfg: SyncConfig, path: String, method: String, body: ByteArray? = null): Request {
        val auth = okhttp3.Credentials.basic(cfg.user, cfg.pass, Charsets.UTF_8)
        val b = Request.Builder()
            .url(url(cfg, path))
            .header("Authorization", auth)
            .header("User-Agent", "xiaoman-sync")
        if (body != null && method == "PUT") {
            b.put(body.toRequestBody(JSON))
        } else {
            b.method(method, null) // MKCOL 等无请求体方法
        }
        return b.build()
    }

    private fun mkcolOnce(cfg: SyncConfig, path: String): Int =
        client(cfg).newCall(request(cfg, path, "MKCOL")).execute().use { it.code }

    /** 逐级创建同步文件夹；已存在的层视为成功。
        网络层异常（超时/DNS/SSL…）原样上抛，让上层 friendlyError 翻译，绝不吞掉——
        否则用户只能看到「卡在哪一步」而看不到「为什么失败」。 */
    fun ensureFolder(cfg: SyncConfig): Boolean {
        val segs = cfg.folder.trim('/').split('/').filter { it.isNotBlank() }
        if (segs.isEmpty()) return true
        var path = ""
        segs.forEach { seg ->
            path += "/$seg"
            val code = mkcolOnce(cfg, path)
            if (!(code in 200..299 || code == 405 || code == 301 || code == 302)) {
                throw DavException(code, "无法创建目录 $path（服务器返回 $code）")
            }
        }
        return true
    }

    fun put(cfg: SyncConfig, name: String, content: String): Boolean {
        return try {
            putOnce(cfg, name, content)
        } catch (e: DavException) {
            if (e.code == 409) {
                ensureFolder(cfg)
                putOnce(cfg, name, content)
            } else {
                throw e
            }
        }
    }

    private fun putOnce(cfg: SyncConfig, name: String, content: String): Boolean {
        val bytes = content.toByteArray(Charsets.UTF_8)
        client(cfg).newCall(request(cfg, "${cfg.folder}/$name", "PUT", bytes)).execute().use { resp ->
            if (!resp.isSuccessful) throw DavException(resp.code, resp.message.ifBlank { "上传失败" })
        }
        return true
    }

    /** 404 返回 null（对端还没上传过）；其他非 2xx 抛异常 */
    fun get(cfg: SyncConfig, name: String): String? {
        client(cfg).newCall(request(cfg, "${cfg.folder}/$name", "GET")).execute().use { resp ->
            when {
                resp.code == 404 -> return null
                resp.isSuccessful -> return resp.body?.string()
                else -> throw DavException(resp.code, resp.message.ifBlank { "下载失败" })
            }
        }
    }
}

/* 端到端加密：密钥 = SHA-256("xiaoman-sync|" + 配对码)，AES-GCM，文件 = Base64(iv).Base64(密文) */
object SyncCrypto {
    private fun key(code: String): SecretKeySpec =
        SecretKeySpec(MessageDigest.getInstance("SHA-256").digest("xiaoman-sync|$code".toByteArray()), "AES")

    fun encrypt(plain: String, code: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(code), javax.crypto.spec.GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + "." + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    fun decrypt(payload: String, code: String): String {
        val parts = payload.trim().split(".")
        require(parts.size == 2) { "同步文件格式不正确" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ct = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(code), javax.crypto.spec.GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}
