package com.librecode.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** GitHub OAuth device flow. Credentials are encrypted with an Android Keystore key. */
internal class GitHubAccount(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("github_auth", Context.MODE_PRIVATE)
    private val alias = "librecode_github_token_v1"
    private val clientId = "Ov23lic02Q91knRitDW6"
    data class DeviceCode(val deviceCode: String, val userCode: String, val interval: Int, val expires: Int)
    private fun form(vararg pairs: Pair<String, String>) = pairs.joinToString("&") { (k,v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }
    private fun post(path: String, body: String): JSONObject {
        val c = URL("https://github.com$path").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 30000; c.doOutput = true; c.instanceFollowRedirects = false
            c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (c.responseCode !in 200..299) throw IllegalStateException("GitHub OAuth: HTTP ${c.responseCode}")
            return JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally { c.disconnect() }
    }
    fun begin(): DeviceCode {
        // OAuth app scopes: repo includes private repository contents; workflow permits changing workflow files.
        val j = post("/login/device/code", form("client_id" to clientId, "scope" to "repo workflow"))
        if (j.has("error")) throw IllegalStateException("GitHub OAuth: ${j.optString("error")}")
        return DeviceCode(j.getString("device_code"), j.getString("user_code"), j.optInt("interval", 5).coerceAtLeast(1), j.optInt("expires_in", 900))
    }
    fun poll(deviceCode: String): JSONObject = post("/login/oauth/access_token", form("client_id" to clientId, "device_code" to deviceCode, "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"))
    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
    @Synchronized fun save(response: JSONObject) {
        require(response.optString("access_token").isNotBlank()) { "Respuesta de autorización inválida" }
        response.put("expires_at", if (response.has("expires_in")) System.currentTimeMillis() + response.optLong("expires_in") * 1000L else Long.MAX_VALUE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(response.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("encrypted", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()) { "No se pudo guardar la sesión" }
    }
    @Synchronized private fun load(): JSONObject? {
        val encoded = prefs.getString("encrypted", null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP); require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }
    fun validatePat(value: String) {
        require(value.isNotBlank() && value.length <= 1024 && value.none { it.isWhitespace() }) { "PAT inválido" }
        val c = URL("https://api.github.com/user").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "GET"; c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = false
            c.setRequestProperty("Accept", "application/vnd.github+json"); c.setRequestProperty("Authorization", "Bearer $value")
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (c.responseCode !in 200..299) throw IllegalStateException("PAT no aceptado por GitHub")
            val user = JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            require(user.optString("login").isNotBlank()) { "Respuesta GitHub inválida" }
        } finally { c.disconnect() }
    }
    fun hasSession() = prefs.contains("encrypted")
    @Synchronized fun clear() { prefs.edit().remove("encrypted").commit() }
    @Synchronized private fun token(): String {
        var j = load() ?: throw IllegalStateException("Conecta GitHub en el panel de Work.")
        if (System.currentTimeMillis() + 60_000 >= j.optLong("expires_at", 0)) {
            val refresh = j.optString("refresh_token")
            require(refresh.isNotBlank()) { "La sesión expiró; vuelve a conectar GitHub." }
            j = post("/login/oauth/access_token", form("client_id" to clientId, "grant_type" to "refresh_token", "refresh_token" to refresh))
            if (!j.has("access_token")) throw IllegalStateException("No se pudo renovar la sesión; vuelve a conectar GitHub.")
            save(j)
        }
        return j.getString("access_token")
    }
    private fun segment(value: String): String {
        require(value.isNotBlank() && value != "." && value != ".." && '/' !in value && '\u0000' !in value) { "Ruta GitHub inválida" }
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
    fun api(method: String, path: String, body: JSONObject? = null): JSONObject {
        require(path.startsWith("/repos/") || path == "/user/repos?per_page=100")
        val c = URL("https://api.github.com$path").openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = false
            c.setRequestProperty("Accept", "application/vnd.github+json"); c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            c.setRequestProperty("Authorization", "Bearer ${token()}")
            if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) } }
            val code = c.responseCode
            if (code !in 200..299) throw IllegalStateException("GitHub HTTP $code; revisa acceso, ruta y rama.")
            val raw = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            require(raw.length <= 2_000_000) { "Respuesta GitHub demasiado grande" }
            return if (raw.trimStart().startsWith("[")) JSONObject().put("items", org.json.JSONArray(raw)) else JSONObject(raw)
        } finally { c.disconnect() }
    }
    fun file(owner: String, repo: String, path: String, branch: String): String {
        val p = path.split('/').joinToString("/") { segment(it) }
        return "/repos/${segment(owner)}/${segment(repo)}/contents/$p?ref=${segment(branch)}"
    }
    fun writeFile(owner: String, repo: String, path: String, branch: String, content: String, message: String): JSONObject {
        require(content.toByteArray(Charsets.UTF_8).size <= 500_000) { "Archivo demasiado grande" }
        val readPath = file(owner, repo, path, branch)
        val existing = try { api("GET", readPath).optString("sha").takeIf { it.isNotBlank() } } catch (e: IllegalStateException) {
            if (e.message?.startsWith("GitHub HTTP 404") == true) null else throw e
        }
        val payload = JSONObject().put("message", message).put("branch", branch).put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
        if (existing != null) payload.put("sha", existing)
        return api("PUT", readPath.substringBefore('?'), payload)
    }
}
