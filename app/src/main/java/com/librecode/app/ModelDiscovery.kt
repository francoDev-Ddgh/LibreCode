package com.librecode.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class ModelInfo(val id: String, val name: String, val detail: String = "")

object ModelDiscovery {
    fun friendlyName(id: String): String {
        val value = id.removePrefix("models/").replace('-', ' ').replace('_', ' ').trim()
        return value.split(' ').joinToString(" ") { word ->
            when (word.lowercase(Locale.ROOT)) {
                "gpt" -> "GPT"; "llama" -> "Llama"; "qwen" -> "Qwen"; "ai" -> "AI"
                else -> word.replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
        }
    }
    fun isAllowedEndpoint(value: String, type: Int): Boolean = try {
        val url = URL(value)
        if (url.userInfo != null || url.host.isBlank()) false
        else if (url.protocol.equals("https", true)) true
        else if (type != 6 || !url.protocol.equals("http", true)) false
        else {
            val host = url.host.lowercase(Locale.ROOT).removePrefix("[").removeSuffix("]")
            val octets = host.split('.').mapNotNull { it.toIntOrNull() }
            host == "localhost" || host.endsWith(".local") || host == "::1" || host.startsWith("fc") && host.contains(':') || host.startsWith("fd") && host.contains(':') || host.startsWith("fe80:") ||
                (octets.size == 4 && octets.all { it in 0..255 } && (octets[0] == 10 || octets[0] == 127 || octets[0] == 192 && octets[1] == 168 || octets[0] == 172 && octets[1] in 16..31 || octets[0] == 169 && octets[1] == 254))
        }
    } catch (_: Exception) { false }

    fun endpointForModel(endpoint: String, type: Int, model: String): String {
        if (type != 0) return endpoint
        val parsed = URL(endpoint)
        val path = parsed.path
        val marker = "/models/"
        require(path.contains(marker) && path.contains(":generateContent")) { "La URL de Gemini debe incluir /models/MODELO:generateContent." }
        require(model.matches(Regex("[A-Za-z0-9._-]+"))) { "El ID de modelo Gemini no es válido." }
        val nextPath = path.substringBefore(marker) + marker + model + ":generateContent"
        return URL(parsed.protocol, parsed.host, parsed.port, nextPath + (parsed.query?.let { "?$it" } ?: "")).toString()
    }
    private fun modelsUrl(endpoint: String, type: Int): String {
        val source = URL(endpoint)
        val path = source.path.trimEnd('/')
        val modelsPath = when (type) {
            0 -> if (path.contains("/models/")) path.substringBefore("/models/") + "/models" else error("La URL Gemini debe incluir /models/MODELO:generateContent.")
            1 -> if (path.endsWith("/messages")) path.removeSuffix("/messages") + "/models" else error("La URL Anthropic debe terminar en /messages.")
            6 -> if (path.endsWith("/api/chat")) path.removeSuffix("/api/chat") + "/api/tags" else error("La URL Ollama debe terminar en /api/chat.")
            else -> when {
                path.endsWith("/chat/completions") -> path.removeSuffix("/chat/completions") + "/models"
                path.endsWith("/responses") -> path.removeSuffix("/responses") + "/models"
                else -> error("La URL debe terminar en /chat/completions o /responses.")
            }
        }
        return URL(source.protocol, source.host, source.port, modelsPath).toString()
    }
    fun fetch(endpoint: String, type: Int, key: String): List<ModelInfo> {
        require(isAllowedEndpoint(endpoint, type)) { "URL no permitida. Usa HTTPS o una dirección local HTTP para Ollama." }
        val base = modelsUrl(endpoint, type)
        val result = linkedMapOf<String, ModelInfo>()
        var token: String? = null
        for (page in 0 until 10) {
            val url = base + if (token != null) "?" + (if (type == 0) "pageToken=" else "after_id=") + URLEncoder.encode(token, "UTF-8") else ""
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"; conn.connectTimeout = 12000; conn.readTimeout = 20000; conn.instanceFollowRedirects = false
                conn.setRequestProperty("Accept", "application/json")
                when (type) {
                    0 -> conn.setRequestProperty("x-goog-api-key", key)
                    1 -> { conn.setRequestProperty("x-api-key", key); conn.setRequestProperty("anthropic-version", "2023-06-01") }
                    else -> if (key.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $key")
                }
                val status = conn.responseCode
                if (status !in 200..299) throw IllegalStateException("HTTP $status al consultar modelos. Revisa URL, clave y permisos.")
                val content = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(content)
                val list = json.optJSONArray(if (type == 0 || type == 6) "models" else "data") ?: throw IllegalStateException("El servidor no devolvió una lista de modelos compatible.")
                for (i in 0 until list.length()) {
                    val item = list.optJSONObject(i) ?: continue
                    if (type == 0) {
                        val methods = item.optJSONArray("supportedGenerationMethods")
                        if (methods != null && (0 until methods.length()).none { methods.optString(it) == "generateContent" }) continue
                    }
                    val id = (if (type == 0) item.optString("name").removePrefix("models/") else if (type == 6) item.optString("name", item.optString("model")) else item.optString("id")).trim()
                    if (id.isBlank()) continue
                    val display = item.optString("display_name", item.optString("displayName", item.optString("name"))).takeIf { it.isNotBlank() && it != "models/$id" && it != id } ?: friendlyName(id)
                    val details = when (type) {
                        0 -> listOfNotNull(item.optInt("inputTokenLimit", 0).takeIf { it > 0 }?.let { "Entrada: $it tokens" }, item.optInt("outputTokenLimit", 0).takeIf { it > 0 }?.let { "Salida: $it tokens" }).joinToString(" · ")
                        6 -> item.optJSONObject("details")?.let { listOf(it.optString("parameter_size"), it.optString("quantization_level")).filter(String::isNotBlank).joinToString(" · ") }.orEmpty()
                        else -> item.optString("description").take(150)
                    }
                    result.putIfAbsent(id, ModelInfo(id, display, details))
                    if (result.size >= 1000) break
                }
                token = when (type) { 0 -> json.optString("nextPageToken").takeIf { it.isNotBlank() }; 1 -> if (json.optBoolean("has_more")) json.optString("last_id").takeIf { it.isNotBlank() } else null; else -> null }
                if (token == null || result.size >= 1000) break
            } finally { conn.disconnect() }
        }
        return result.values.toList().sortedBy { it.name.lowercase(Locale.ROOT) }
    }
}
