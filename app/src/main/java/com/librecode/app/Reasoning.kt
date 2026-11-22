package com.librecode.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class EffortOption(val key: String, val label: String)
data class EffortProfile(val mode: String, val options: List<EffortOption>, val note: String)

object Reasoning {
    private fun levels(vararg pairs: Pair<String, String>) = pairs.map { EffortOption(it.first, it.second) }
    private val auto = "auto" to "Automático · predeterminado"
    private val low = "low" to "Bajo"
    private val medium = "medium" to "Medio"
    private val high = "high" to "Alto"
    private val xhigh = "xhigh" to "Extra alto"
    private val off = "none" to "Desactivado"
    fun known(type: Int, endpoint: String, id: String): EffortProfile? {
        val host = try { URL(endpoint).host.lowercase(Locale.ROOT) } catch (_: Exception) { return null }
        val m = id.lowercase(Locale.ROOT)
        if (type == 0 && host == "generativelanguage.googleapis.com") {
            if (m in setOf("gemma-4-31b-it", "gemma-4-26b-a4b-it")) return EffortProfile("gemini_level", levels(auto, "minimal" to "Desactivado", "high" to "Activado"), "Gemma 4 permite activar o desactivar el razonamiento.")
            if (m in setOf("gemini-3.5-flash", "gemini-3.6-flash", "gemini-3-flash-preview", "gemini-3.5-flash-lite"))
                return EffortProfile("gemini_level", levels(auto, low, medium, high), "Gemini usa niveles de pensamiento; mínimo no garantiza desactivarlo.")
            if (m == "gemini-3.1-pro-preview") return EffortProfile("gemini_level", levels(auto, low, medium, high), "Este modelo no admite desactivar el razonamiento.")
            if (m == "gemini-2.5-flash" || m == "gemini-2.5-flash-lite")
                return EffortProfile("gemini_budget", levels(auto, "0" to "Desactivado", "1024" to "Bajo", "8192" to "Medio", "24576" to "Alto"), "Presupuesto de pensamiento de 0 a 24576 tokens.")
        }
        if (type == 1 && host == "api.anthropic.com") {
            val all = setOf("claude-opus-5-5", "claude-opus-5", "claude-opus-4-8", "claude-opus-4-7", "claude-sonnet-5-5", "claude-sonnet-5", "claude-haiku-5-5", "claude-fable-5", "claude-fable-5-1")
            val limited = setOf("claude-opus-4-6", "claude-sonnet-4-6")
            if (m in all || m in limited) {
                val disabled = m in setOf("claude-opus-4-6", "claude-sonnet-4-6", "claude-sonnet-5")
                val opts = mutableListOf(EffortOption(auto.first, auto.second)); if (disabled) opts.add(EffortOption("off", "Desactivado"))
                opts.add(EffortOption("on", "Activado")); opts.addAll(levels(low, medium, high))
                return EffortProfile("anthropic_effort", opts, "El esfuerzo orienta al modelo; el pensamiento puede ser obligatorio según el modelo.")
            }
        }
        if (type == 2 && host == "api.deepseek.com" && m in setOf("deepseek-flash", "deepseek-v4-pro"))
            return EffortProfile("deepseek", levels(auto, off, "on" to "Activado", low, high), "DeepSeek admite desactivado, bajo, alto y máximo.")
        if (type == 3 && host == "api.moonshot.ai" && m in setOf("kimi-k3.1-pro", "kimi-k3.1-pro-highspeed"))
            return EffortProfile("kimi", levels(auto, low, high), "Kimi admite bajo, alto y máximo en este modelo.")
        if (type == 5 && host == "api.z.ai") {
            if (m in setOf("glm-4.5", "glm-4.7", "glm-5-turbo")) return EffortProfile("zai", levels(auto, off, "on" to "Activado"), "Este modelo permite activar o desactivar el pensamiento.")
            if (m == "glm-5.3") return EffortProfile("zai", levels(auto, "on" to "Activado", low, high), "GLM-5.3 requiere razonamiento activo; admite bajo y alto.")
            if (m == "glm-5.2") return EffortProfile("zai", levels(auto, off, "on" to "Activado", low, high), "Las opciones dependen del modelo de Z.ai.")
        }
        if ((type == 4 || type == 5) && host == "api.openai.com") {
            val opts = when (m) {
                "gpt-5" -> levels(auto, low, medium, high)
                "gpt-5.1" -> levels(auto, off, low, medium, high)
                "gpt-5.2" -> levels(auto, off, low, medium, high)
                "gpt-6-sol", "gpt-6-luna" -> levels(auto, off, low, medium, high)
                else -> null
            }
            if (opts != null) return EffortProfile("openai", opts, "Las opciones dependen del modelo y protocolo de OpenAI.")
        }
        return null
    }
    fun ollama(endpoint: String, model: String, key: String): EffortProfile? {
        require(ModelDiscovery.isAllowedEndpoint(endpoint, 6)) { "URL Ollama no permitida." }
        val source = URL(endpoint); require(source.path.endsWith("/api/chat")) { "La URL debe terminar en /api/chat." }
        val target = URL(source.protocol, source.host, source.port, source.path.removeSuffix("/api/chat") + "/api/show")
        val conn = target.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"; conn.connectTimeout = 8000; conn.readTimeout = 12000; conn.doOutput = true; conn.instanceFollowRedirects = false
            conn.setRequestProperty("Content-Type", "application/json"); if (key.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $key")
            conn.outputStream.use { it.write(JSONObject().put("model", model).toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode} al consultar capacidades.")
            val obj = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val values = obj.optJSONObject("thinking")?.optJSONArray("values") ?: return null
            val options = mutableListOf(EffortOption("auto", "Automático · predeterminado"))
            for (i in 0 until values.length()) {
                val v = values.opt(i)
                if (v is String && v !in setOf("low", "medium", "high")) continue
                val k = when (v) { true -> "bool:true"; false -> "bool:false"; is String -> "str:$v"; else -> continue }
                val label = when (k) { "bool:true" -> "Activado"; "bool:false" -> "Desactivado"; "str:low" -> "Bajo"; "str:medium" -> "Medio"; "str:high" -> "Alto"; "str:xhigh" -> "Extra alto"; "str:max" -> "Máximo"; else -> v.toString() }
                options.add(EffortOption(k, label))
            }
            if (options.size == 2 && options[1].key == "bool:false") return null
            return EffortProfile("ollama", options, "Opciones consultadas directamente en /api/show.")
        } finally { conn.disconnect() }
    }
    fun apply(body: JSONObject, type: Int, mode: String, value: String) {
        if (value == "auto" || value.isBlank()) return
        when (mode) {
            "gemini_level" -> body.put("generationConfig", JSONObject().put("thinkingConfig", JSONObject().put("thinkingLevel", value)))
            "gemini_budget" -> body.put("generationConfig", JSONObject().put("thinkingConfig", JSONObject().put("thinkingBudget", value.toInt())))
            "anthropic_effort" -> if (value == "off") body.put("thinking", JSONObject().put("type", "disabled")) else { body.put("thinking", JSONObject().put("type", "adaptive")).put("max_tokens", 8192); if (value != "on") body.put("output_config", JSONObject().put("effort", value)) }
            "deepseek" -> if (value == "on") body.put("thinking", JSONObject().put("type", "enabled")) else body.put("reasoning_effort", value)
            "zai" -> if (value == "none" || value == "on") body.put("thinking", JSONObject().put("type", if (value == "none") "disabled" else "enabled")) else body.put("thinking", JSONObject().put("type", "enabled")).put("reasoning_effort", value)
            "kimi" -> body.put("reasoning_effort", value)
            "openai" -> if (type == 4) body.put("reasoning", JSONObject().put("effort", value)) else body.put("reasoning_effort", value)
            "ollama" -> body.put("think", when { value == "bool:true" -> true; value == "bool:false" -> false; value.startsWith("str:") -> value.removePrefix("str:"); else -> value })

        }
    }
}
