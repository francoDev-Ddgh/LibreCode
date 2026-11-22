package com.librecode.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val bg = Color.rgb(250, 249, 245)
    private val ink = Color.rgb(44, 42, 37)
    private val muted = Color.rgb(115, 110, 100)
    private val accent = Color.rgb(195, 101, 72)
    private val panel = Color.rgb(238, 236, 228)
    private lateinit var root: LinearLayout
    private lateinit var transcript: LinearLayout
    private lateinit var scroller: ScrollView
    private lateinit var composer: EditText
    private lateinit var send: Button
    private lateinit var title: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private val messages = mutableListOf<Pair<String, String>>()
    private val prefs by lazy { getSharedPreferences("provider", MODE_PRIVATE) }
    private val names = arrayOf("Google Gemini", "Anthropic Claude", "DeepSeek", "Kimi", "OpenAI")
    private val urls = arrayOf(
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent",
        "https://api.anthropic.com/v1/messages", "https://api.deepseek.com/chat/completions",
        "https://api.moonshot.ai/v1/chat/completions", "https://api.openai.com/v1/responses"
    )
    private val models = arrayOf("gemini-3.5-flash", "claude-opus-5-5", "deepseek-flash", "kimi-k3", "gpt-6.2-astra")
    private var busy = false
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        else @Suppress("DEPRECATION") run { window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(root)
        buildScreen()
        showProviderDialog()
    }
    private fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()
    private fun shape(color: Int, radius: Int = 18) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun text(value: String, size: Float, color: Int = ink) = TextView(this).apply { text = value; textSize = size; setTextColor(color) }
    private fun button(label: String) = Button(this).apply { text = label; isAllCaps = false; setTextColor(ink); background = shape(panel, 14); textSize = 14f }
    private fun buildScreen() {
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(22), dp(12), dp(18), dp(12)) }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(text("✳  LibreCode", 22f).apply { typeface = Typeface.create("serif", Typeface.BOLD) })
        title = text("Tu espacio para pensar", 12f, muted)
        brand.addView(title)
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("Configurar").apply { setOnClickListener { if (!busy) showProviderDialog() } }, LinearLayout.LayoutParams(-2, dp(42)))
        root.addView(header)
        scroller = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        transcript = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(18)) }
        scroller.addView(transcript)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(12)) }
        val row = LinearLayout(this).apply { gravity = Gravity.BOTTOM; background = shape(Color.WHITE, 20); setPadding(dp(10), dp(5), dp(5), dp(5)) }
        composer = EditText(this).apply { hint = "Escribe un mensaje…"; textSize = 16f; setTextColor(ink); setHintTextColor(muted); background = null; minLines = 1; maxLines = 5; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; setPadding(dp(8), dp(12), dp(8), dp(12)) }
        row.addView(composer, LinearLayout.LayoutParams(0, -2, 1f))
        send = button("Enviar").apply { setTextColor(Color.WHITE); background = shape(accent, 14); setOnClickListener { sendMessage() } }
        row.addView(send, LinearLayout.LayoutParams(dp(76), dp(48)))
        bottom.addView(row)
        bottom.addView(text("Una conversación sencilla, sin historial guardado.", 11f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
        root.addView(bottom)
        showWelcome()
    }
    private fun showWelcome() {
        transcript.removeAllViews()
        transcript.addView(text("✳", 32f, accent).apply { gravity = Gravity.CENTER; setPadding(0, dp(55), 0, dp(12)) })
        transcript.addView(text("¿En qué puedo ayudarte?", 27f).apply { gravity = Gravity.CENTER; typeface = Typeface.create("serif", Typeface.NORMAL) })
        transcript.addView(text("Una idea, una pregunta o algo que quieras crear.", 14f, muted).apply { gravity = Gravity.CENTER; setPadding(dp(20), dp(10), dp(20), 0) })
    }
    private fun addMessage(role: String, content: String) {
        if (messages.size == 1 && role == "user") transcript.removeAllViews()
        val block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(13), dp(16), dp(13)); background = shape(if (role == "user") panel else Color.WHITE, 18) }
        block.addView(text(if (role == "user") "TÚ" else prefs.getString("name", "ASISTENTE")!!.uppercase(), 11f, if (role == "user") muted else accent).apply { typeface = Typeface.DEFAULT_BOLD })
        block.addView(text(content, 16f).apply { setTextIsSelectable(true); setPadding(0, dp(7), 0, 0); setLineSpacing(dp(3).toFloat(), 1f) })
        val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        transcript.addView(block, lp)
        scroller.post { scroller.fullScroll(View.FOCUS_DOWN) }
    }
    private fun showProviderDialog() {
        val initial = prefs.getInt("type", 0).coerceIn(0, names.lastIndex)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(5), dp(22), dp(8)) }
        val selector = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, names); setSelection(initial) }
        layout.addView(text("Tipo de proveedor", 13f, muted)); layout.addView(selector)
        fun field(label: String, value: String, secret: Boolean = false): EditText {
            layout.addView(text(label, 13f, muted).apply { setPadding(0, dp(12), 0, 0) })
            return EditText(this).apply { setSingleLine(true); setText(value); textSize = 15f; if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; layout.addView(this) }
        }
        val name = field("Nombre del proveedor", prefs.getString("name", names[initial]) ?: names[initial])
        val url = field("URL HTTPS completa del endpoint", prefs.getString("url", urls[initial]) ?: urls[initial])
        val model = field("Modelo (Gemini usa el de la URL)", prefs.getString("model", models[initial]) ?: models[initial])
        val key = field("API key (guardada en texto plano)", prefs.getString("key", "") ?: "", true)
        layout.addView(text("Solo para pruebas. La clave se guarda sin cifrar y se envía a la URL indicada.", 12f, muted).apply { setPadding(0, dp(12), 0, 0) })
        var last = initial
        selector.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != last) { name.setText(names[position]); url.setText(urls[position]); model.setText(models[position]); key.setText(""); last = position }
            }
        }
        val scroll = ScrollView(this).apply { addView(layout) }
        val dialog = AlertDialog.Builder(this).setTitle("Conectar proveedor").setView(scroll).setPositiveButton("Guardar", null).setNegativeButton("Cancelar") { _, _ -> }.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val endpoint = url.text.toString().trim()
                val host = try { URL(endpoint).let { it.protocol == "https" && it.host.isNotBlank() && it.userInfo == null } } catch (_: Exception) { false }
                if (!host || name.text.isBlank() || key.text.isBlank() || (selector.selectedItemPosition != 0 && model.text.isBlank())) {
                    Toast.makeText(this, "Completa nombre, URL HTTPS, modelo y clave.", Toast.LENGTH_LONG).show()
                } else {
                    val oldType = prefs.getInt("type", -1); val oldUrl = prefs.getString("url", ""); val oldModel = prefs.getString("model", "")
                    prefs.edit().putInt("type", selector.selectedItemPosition).putString("name", name.text.toString().trim()).putString("url", endpoint).putString("model", model.text.toString().trim()).putString("key", key.text.toString().trim()).apply()
                    if (oldType != selector.selectedItemPosition || oldUrl != endpoint || oldModel != model.text.toString().trim()) { messages.clear(); showWelcome() }
                    title.text = name.text.toString().trim()
                    dialog.dismiss()
                }
            }
        }
        title.text = prefs.getString("name", "Tu espacio para pensar")
        dialog.show()
    }
    private fun sendMessage() {
        val content = composer.text.toString().trim()
        if (content.isEmpty() || busy) return
        if (prefs.getString("key", "").isNullOrBlank()) { showProviderDialog(); return }
        val type = prefs.getInt("type", 0); val endpoint = prefs.getString("url", "")!!; val key = prefs.getString("key", "")!!; val model = prefs.getString("model", "")!!
        messages.add("user" to content); addMessage("user", content); composer.setText("")
        busy = true; send.isEnabled = false; send.text = "…"
        val snapshot = messages.toList(); val requestGeneration = ++generation
        executor.execute {
            val result = try { Result.success(request(type, endpoint, key, model, snapshot)) } catch (e: Exception) { Result.failure(e) }
            runOnUiThread {
                if (isFinishing || isDestroyed || requestGeneration != generation) return@runOnUiThread
                busy = false; send.isEnabled = true; send.text = "Enviar"
                result.fold(onSuccess = { answer -> messages.add("assistant" to answer); addMessage("assistant", answer) }, onFailure = { error ->
                    if (messages.lastOrNull()?.first == "user") messages.removeAt(messages.lastIndex)
                    composer.setText(content)
                    addMessage("error", "No se pudo obtener una respuesta. ${error.message ?: "Error de conexión."}")
                })
            }
        }
    }
    private fun request(type: Int, endpoint: String, key: String, model: String, history: List<Pair<String, String>>): String {
        val body = JSONObject()
        when (type) {
            0 -> { val contents = JSONArray(); history.forEach { (role, value) -> contents.put(JSONObject().put("role", if (role == "assistant") "model" else "user").put("parts", JSONArray().put(JSONObject().put("text", value)))) }; body.put("contents", contents) }
            1 -> { val list = JSONArray(); history.forEach { (role, value) -> list.put(JSONObject().put("role", role).put("content", value)) }; body.put("model", model).put("max_tokens", 2048).put("messages", list) }
            2, 3 -> { val list = JSONArray(); history.forEach { (role, value) -> list.put(JSONObject().put("role", role).put("content", value)) }; body.put("model", model).put("messages", list).put("stream", false) }
            4 -> { val list = JSONArray(); history.forEach { (role, value) -> list.put(JSONObject().put("role", role).put("content", value)) }; body.put("model", model).put("input", list).put("store", false) }
            else -> error("Proveedor desconocido")
        }
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.connectTimeout = 30000; connection.readTimeout = 180000; connection.doOutput = true
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Content-Type", "application/json")
            when (type) {
                0 -> connection.setRequestProperty("x-goog-api-key", key)
                1 -> { connection.setRequestProperty("x-api-key", key); connection.setRequestProperty("anthropic-version", "2026-10-21") }
                else -> connection.setRequestProperty("Authorization", "Bearer $key")
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("HTTP $status. Revisa la URL, el modelo, la clave y el saldo.")
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(response)
            val answer = when (type) {
                0 -> { val parts = json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts"); (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optString("text")?.takeIf(String::isNotBlank) }.joinToString("\n") }
                1 -> { val blocks = json.getJSONArray("content"); (0 until blocks.length()).mapNotNull { blocks.optJSONObject(it)?.takeIf { b -> b.optString("type") == "text" }?.optString("text") }.joinToString("\n") }
                2, 3 -> json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
                else -> { val out = json.getJSONArray("output"); val pieces = mutableListOf<String>(); for (i in 0 until out.length()) { val block = out.optJSONObject(i) ?: continue; val parts = block.optJSONArray("content") ?: continue; for (j in 0 until parts.length()) { val part = parts.optJSONObject(j) ?: continue; if (part.optString("type") == "output_text") pieces.add(part.optString("text")) } }; pieces.joinToString("\n") }
            }
            if (answer.isBlank()) throw IllegalStateException("El proveedor no devolvió texto.")
            return answer
        } finally { connection.disconnect() }
    }
    override fun onDestroy() { generation++; executor.shutdownNow(); super.onDestroy() }
}
