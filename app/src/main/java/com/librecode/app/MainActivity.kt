package com.librecode.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.AtomicFile
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.*
import io.noties.markwon.Markwon
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.tables.TablePlugin
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.UUID

class MainActivity : Activity() {
    private val bg = Color.rgb(250, 249, 245)
    private val ink = Color.rgb(44, 42, 37)
    private val muted = Color.rgb(115, 110, 100)
    private val accent = Color.rgb(195, 101, 72)
    private val panel = Color.rgb(238, 236, 228)
    private lateinit var root: LinearLayout
    private lateinit var screen: FrameLayout
    private var drawerOverlay: FrameLayout? = null
    private var settingsOverlay: FrameLayout? = null
    private var settingsPage = ""
    private var editingId: String? = null
    private var providersAvailable = true
    private data class Provider(val id: String, val type: Int, val name: String, val url: String, val model: String, val key: String, val custom: Boolean, val selected: List<ModelInfo> = emptyList())
    private val providers = mutableListOf<Provider>()
    private lateinit var transcript: LinearLayout
    private lateinit var scroller: ScrollView
    private lateinit var composer: EditText
    private lateinit var send: Button
    private lateinit var title: TextView
    private lateinit var modelButton: TextView
    private val scanHandler = Handler(Looper.getMainLooper())
    private var scanVersion = 0
    private val executor = Executors.newSingleThreadExecutor()
    private val messages = mutableListOf<Pair<String, String>>()
    private data class Chat(val id: String, val title: String, val time: Long, val entries: List<Pair<String, String>>)
    private val chats = mutableListOf<Chat>()
    private val historyFile by lazy { AtomicFile(java.io.File(filesDir, "chats.json")) }
    private var historyAvailable = true
    private var chatId = UUID.randomUUID().toString()
    private val markdown by lazy {
        Markwon.builder(this).usePlugin(TablePlugin.create(this)).usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder.codeBlockBackgroundColor(Color.rgb(238, 236, 230))
                    .codeBlockTextColor(ink).codeBlockTypeface(Typeface.MONOSPACE)
                    .codeBackgroundColor(Color.rgb(238, 236, 230)).codeTypeface(Typeface.MONOSPACE)
            }
        }).build()
    }
    private val prefs by lazy { getSharedPreferences("provider", MODE_PRIVATE) }
    private val names = arrayOf("Google Gemini", "Anthropic Claude", "DeepSeek", "Kimi", "OpenAI · Responses", "OpenAI · Chat Completions", "Ollama")
    private val urls = arrayOf(
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent",
        "https://api.anthropic.com/v1/messages", "https://api.deepseek.com/chat/completions",
        "https://api.moonshot.ai/v1/chat/completions", "https://api.openai.com/v1/responses",
        "https://api.openai.com/v1/chat/completions", "http://192.168.1.100:11434/api/chat"
    )
    private val models = arrayOf("gemini-3.5-flash", "claude-opus-5-5", "deepseek-flash", "kimi-k3", "gpt-6.2-astra", "gpt-6.2-astra", "")
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
        screen = FrameLayout(this).apply { addView(root) }
        setContentView(screen)
        loadHistory()
        loadProviders()
        buildScreen()
        val restoredId = savedInstanceState?.getString("chatId")
        chats.find { it.id == restoredId }?.let { openChat(it) }
        if (prefs.getString("active_provider_id", null) == null) showSettings("providers")
    }
    private fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()
    private fun shape(color: Int, radius: Int = 18) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun text(value: String, size: Float, color: Int = ink) = TextView(this).apply { text = value; textSize = size; setTextColor(color) }
    private fun button(label: String) = Button(this).apply { text = label; isAllCaps = false; setTextColor(ink); background = shape(panel, 14); textSize = 14f }
    private fun buildScreen() {
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10)) }
        val menu = text("☰", 23f, muted).apply {
            gravity = Gravity.CENTER; contentDescription = "Abrir menú e historial"
            background = shape(bg, 14); setOnClickListener { showDrawer() }
        }
        header.addView(menu, LinearLayout.LayoutParams(dp(44), dp(44)))
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, 0) }
        brand.addView(text("LibreCode", 22f).apply { typeface = Typeface.create("serif", Typeface.BOLD) })
        title = text(prefs.getString("name", "Tu espacio para pensar") ?: "Tu espacio para pensar", 12f, muted)
        brand.addView(title)
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        modelButton = text("Elegir modelo  ▾", 14f, ink).apply {
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(12), 0, dp(12), 0); background = shape(panel, 14); contentDescription = "Seleccionar modelo"
            setOnClickListener { if (busy) Toast.makeText(this@MainActivity, "Espera la respuesta actual.", Toast.LENGTH_SHORT).show() else showModelPicker() }
        }
        header.addView(modelButton, LinearLayout.LayoutParams(dp(140), dp(42)))
        refreshModelButton()
        root.addView(header)
        scroller = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        transcript = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(18), dp(22), dp(18)) }
        scroller.addView(transcript)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(12)) }
        val row = LinearLayout(this).apply { gravity = Gravity.BOTTOM; background = shape(Color.WHITE, 22); setPadding(dp(12), dp(7), dp(7), dp(7)); elevation = dp(2).toFloat() }
        composer = EditText(this).apply { hint = "¿En qué puedo ayudarte?"; textSize = 16f; setTextColor(ink); setHintTextColor(muted); background = null; minLines = 1; maxLines = 5; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; setPadding(dp(8), dp(12), dp(8), dp(12)) }
        row.addView(composer, LinearLayout.LayoutParams(0, -2, 1f))
        send = button("↑").apply { contentDescription = "Enviar mensaje"; textSize = 22f; setTextColor(Color.WHITE); background = shape(accent, 13); setOnClickListener { sendMessage() } }
        row.addView(send, LinearLayout.LayoutParams(dp(46), dp(46)))
        bottom.addView(row)
        bottom.addView(text("Las conversaciones se guardan en este dispositivo.", 11f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(9), 0, 0) })
        root.addView(bottom)
        showWelcome()
    }
    private fun showWelcome() {
        transcript.removeAllViews()
        transcript.addView(text("¿En qué puedo ayudarte?", 27f).apply { gravity = Gravity.CENTER; setPadding(0, dp(70), 0, 0); typeface = Typeface.create("serif", Typeface.NORMAL) })
        transcript.addView(text("Escribe lo que tengas en mente.", 14f, muted).apply { gravity = Gravity.CENTER; setPadding(dp(20), dp(10), dp(20), 0) })
    }
    private fun addMessage(role: String, content: String) {
        val isUser = role == "user"
        val isError = role == "error"
        val row = LinearLayout(this).apply { gravity = if (isUser) Gravity.END else Gravity.START; setPadding(0, dp(4), 0, dp(16)) }
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(if (isUser) 16 else 4), dp(12), dp(if (isUser) 16 else 4), dp(12))
            if (isUser) background = shape(panel, 18)
        }
        if (!isUser) block.addView(text(if (isError) "ERROR" else "Asistente", 12f, accent).apply { typeface = Typeface.DEFAULT_BOLD })
        val body = text("", 16f).apply { setTextIsSelectable(true); setPadding(0, if (isUser) 0 else dp(8), 0, 0); setLineSpacing(dp(4).toFloat(), 1f) }
        if (isUser || isError) body.text = content else markdown.setMarkdown(body, content)
        block.addView(body)
        val width = if (isUser) (resources.displayMetrics.widthPixels * .80f).toInt() else -1
        row.addView(block, LinearLayout.LayoutParams(width, -2))
        transcript.addView(row, LinearLayout.LayoutParams(-1, -2))
        scroller.post { scroller.fullScroll(View.FOCUS_DOWN) }
    }
    private fun renderConversation() {
        transcript.removeAllViews()
        if (messages.isEmpty()) showWelcome() else messages.forEach { (role, value) -> addMessage(role, value) }
    }
    private fun closeDrawer() {
        val overlay = drawerOverlay ?: return
        drawerOverlay = null
        screen.removeView(overlay)
    }
    private fun showDrawer() {
        if (drawerOverlay != null) return
        val overlay = FrameLayout(this)
        val shade = View(this).apply {
            setBackgroundColor(Color.argb(95, 20, 19, 17)); contentDescription = "Cerrar panel"
            setOnClickListener { closeDrawer() }
        }
        overlay.addView(shade, FrameLayout.LayoutParams(-1, -1))
        val drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(243, 241, 234))
            elevation = dp(12).toFloat(); isClickable = true
        }
        val insets = root.rootWindowInsets
        val topInset = if (insets != null) {
            if (Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()).top
            else @Suppress("DEPRECATION") insets.systemWindowInsetTop
        } else 0
        val bottomInset = if (insets != null) {
            if (Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.systemBars()).bottom
            else @Suppress("DEPRECATION") insets.systemWindowInsetBottom
        } else 0
        drawer.setPadding(dp(16), topInset + dp(12), dp(16), bottomInset + dp(12))
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("LibreCode", 22f).apply { typeface = Typeface.create("serif", Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(text("×", 27f, muted).apply { gravity = Gravity.CENTER; contentDescription = "Cerrar panel"; setOnClickListener { closeDrawer() } }, LinearLayout.LayoutParams(dp(44), dp(44)))
        drawer.addView(header)
        val new = text("＋   Nuevo chat", 16f, ink).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), 0, dp(10), 0); background = shape(Color.WHITE, 12)
            setOnClickListener { if (busy) Toast.makeText(this@MainActivity, "Espera la respuesta actual.", Toast.LENGTH_SHORT).show() else { newChat(); closeDrawer() } }
        }
        drawer.addView(new, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(18); bottomMargin = dp(22) })
        drawer.addView(text("RECIENTES", 11f, muted).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(dp(12), 0, 0, dp(10)) })
        val scroll = ScrollView(this).apply { isFillViewport = false }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val ordered = chats.sortedByDescending { it.time }
        if (ordered.isEmpty()) list.addView(text("Tus conversaciones aparecerán aquí.", 14f, muted).apply { setPadding(dp(12), dp(12), dp(8), 0) })
        ordered.forEach { chat ->
            val item = text(chat.title, 15f, ink).apply {
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(8), dp(10))
                if (chat.id == chatId) background = shape(Color.rgb(229, 226, 216), 10)
                contentDescription = "Abrir conversación: ${chat.title}. Mantén pulsado para eliminar."
                setOnClickListener {
                    if (busy) Toast.makeText(this@MainActivity, "Espera la respuesta actual.", Toast.LENGTH_SHORT).show()
                    else { openChat(chat); closeDrawer(); Toast.makeText(this@MainActivity, "Se usará el proveedor configurado actualmente.", Toast.LENGTH_SHORT).show() }
                }
                setOnLongClickListener {
                    if (busy) Toast.makeText(this@MainActivity, "Espera la respuesta actual.", Toast.LENGTH_SHORT).show()
                    else AlertDialog.Builder(this@MainActivity).setTitle("Eliminar conversación")
                        .setMessage("¿Eliminar «${chat.title}» del dispositivo?")
                        .setPositiveButton("Eliminar") { _, _ -> chats.removeAll { it.id == chat.id }; saveHistory(); if (chatId == chat.id) newChat(); closeDrawer(); showDrawer() }
                        .setNegativeButton("Cancelar", null).show()
                    true
                }
            }
            list.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(3) })
        }
        scroll.addView(list)
        drawer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        drawer.addView(text("Mantén pulsado un chat para eliminarlo", 11f, muted).apply { setPadding(dp(12), dp(10), 0, dp(10)) })
        drawer.addView(text("⚙   Configuraciones", 15f, ink).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), 0, dp(10))
            setOnClickListener { if (busy) Toast.makeText(this@MainActivity, "Espera la respuesta actual.", Toast.LENGTH_SHORT).show() else { closeDrawer(); showSettings("home") } }
        })
        val width = minOf(dp(315), (resources.displayMetrics.widthPixels * .86f).toInt())
        overlay.addView(drawer, FrameLayout.LayoutParams(width, -1, Gravity.START))
        screen.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        drawerOverlay = overlay
        drawer.translationX = -width.toFloat()
        drawer.animate().translationX(0f).setDuration(200).start()
    }
    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        when {
            settingsOverlay != null -> when (settingsPage) {
                "editor" -> { scanVersion++; scanHandler.removeCallbacksAndMessages(null); showSettings("providers") }
                "providers", "general", "changes" -> showSettings("home")
                else -> closeSettings()
            }
            drawerOverlay != null -> closeDrawer()
            else -> super.onBackPressed()
        }
    }
    private fun newChat() {
        chatId = UUID.randomUUID().toString(); messages.clear(); composer.setText(""); showWelcome()
    }
    private fun openChat(chat: Chat) {
        chatId = chat.id; messages.clear(); messages.addAll(chat.entries); composer.setText(""); renderConversation()
    }
    private fun loadHistory() {
        if (!historyFile.baseFile.exists()) return
        try {
            val list = JSONArray(historyFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i); val entries = item.getJSONArray("messages"); val content = mutableListOf<Pair<String, String>>()
                for (j in 0 until entries.length()) { val m = entries.getJSONObject(j); val role = m.getString("role"); if (role == "user" || role == "assistant") content.add(role to m.getString("text")) }
                if (content.isNotEmpty()) chats.add(Chat(item.getString("id"), item.getString("title"), item.optLong("time"), content))
            }
        } catch (_: Exception) { historyAvailable = false; Toast.makeText(this, "No se pudo leer el historial; el archivo original se conserva.", Toast.LENGTH_LONG).show() }
    }
    private fun saveHistory() {
        if (!historyAvailable) return
        val list = JSONArray()
        chats.forEach { chat ->
            val entries = JSONArray(); chat.entries.forEach { (role, value) -> entries.put(JSONObject().put("role", role).put("text", value)) }
            list.put(JSONObject().put("id", chat.id).put("title", chat.title).put("time", chat.time).put("messages", entries))
        }
        var output: java.io.FileOutputStream? = null
        try { output = historyFile.startWrite(); output.write(list.toString().toByteArray(Charsets.UTF_8)); historyFile.finishWrite(output) }
        catch (_: Exception) { if (output != null) historyFile.failWrite(output); Toast.makeText(this, "No se pudo guardar el historial local.", Toast.LENGTH_LONG).show() }
    }
    private fun saveCurrentChat() {
        if (messages.isEmpty()) return
        val title = messages.firstOrNull { it.first == "user" }?.second?.replace('\n', ' ')?.take(55) ?: "Conversación"
        chats.removeAll { it.id == chatId }
        chats.add(Chat(chatId, title, System.currentTimeMillis(), messages.toList()))
        saveHistory()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("chatId", chatId); super.onSaveInstanceState(outState) }
    private fun legacyModel(type: Int, url: String, model: String): String = if (type == 0) try { URL(url).path.substringAfterLast("/models/").substringBefore(":") } catch (_: Exception) { "" } else model
    private fun loadProviders() {
        val stored = prefs.getString("providers_v1", null)
        if (stored != null) {
            try {
                val array = JSONArray(stored)
                for (i in 0 until array.length()) {
                    val p = array.getJSONObject(i); val type = p.getInt("type"); val url = p.getString("url"); val model = p.getString("model"); val key = p.getString("key")
                    val selected = mutableListOf<ModelInfo>(); val saved = p.optJSONArray("selected")
                    if (saved != null) for (j in 0 until saved.length()) { val m = saved.getJSONObject(j); selected.add(ModelInfo(m.getString("id"), m.getString("name"), m.optString("detail"))) }
                    else if (key.isNotBlank()) { val id = legacyModel(type, url, model); if (id.isNotBlank()) selected.add(ModelInfo(id, ModelDiscovery.friendlyName(id))) }
                    providers.add(Provider(p.getString("id"), type, p.getString("name"), url, model, key, p.getBoolean("custom"), selected))
                }
                for (type in names.indices) if (providers.none { it.id == "preset_$type" }) providers.add(Provider("preset_$type", type, names[type], urls[type], models[type], "", false))
                saveProviders()
            } catch (_: Exception) {
                providers.clear(); providersAvailable = false
                Toast.makeText(this, "No se pudo leer la configuración de proveedores; se conserva el archivo original.", Toast.LENGTH_LONG).show()
            }
            return
        }
        for (type in names.indices) providers.add(Provider("preset_$type", type, names[type], urls[type], models[type], "", false))
        val oldKey = prefs.getString("key", "").orEmpty()
        if (oldKey.isNotBlank()) {
            val type = prefs.getInt("type", 0).coerceIn(0, names.lastIndex)
            val oldName = prefs.getString("name", names[type]).orEmpty(); val oldUrl = prefs.getString("url", urls[type]).orEmpty(); val oldModel = prefs.getString("model", models[type]).orEmpty()
            val isPreset = oldName == names[type] && oldUrl == urls[type]
            val id = if (isPreset) "preset_$type" else "custom_" + UUID.randomUUID()
            val model = legacyModel(type, oldUrl, oldModel)
            val migrated = Provider(id, type, oldName, oldUrl, oldModel, oldKey, !isPreset, if (model.isNotBlank()) listOf(ModelInfo(model, ModelDiscovery.friendlyName(model))) else emptyList())
            if (isPreset) providers[type] = migrated else providers.add(migrated)
            prefs.edit().putString("active_provider_id", id).apply()
        }
        saveProviders()
    }
    private fun saveProviders() {
        if (!providersAvailable) return
        val array = JSONArray()
        providers.forEach { p ->
            val selected = JSONArray(); p.selected.forEach { m -> selected.put(JSONObject().put("id", m.id).put("name", m.name).put("detail", m.detail)) }
            array.put(JSONObject().put("id", p.id).put("type", p.type).put("name", p.name).put("url", p.url).put("model", p.model).put("key", p.key).put("custom", p.custom).put("selected", selected))
        }
        prefs.edit().putString("providers_v1", array.toString()).apply()
    }
    private fun activateProvider(provider: Provider, model: ModelInfo) {
        val endpoint = try { ModelDiscovery.endpointForModel(provider.url, provider.type, model.id) } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "No se pudo seleccionar el modelo.", Toast.LENGTH_LONG).show(); return
        }
        prefs.edit().putString("active_provider_id", provider.id).putInt("type", provider.type).putString("name", provider.name)
            .putString("url", endpoint).putString("model", model.id).putString("model_name", model.name).putString("key", provider.key).apply()
        title.text = provider.name; refreshModelButton()
    }
    private fun refreshModelButton() {
        if (::modelButton.isInitialized) modelButton.text = (prefs.getString("model_name", null) ?: prefs.getString("model", "Elegir modelo")?.takeIf { it.isNotBlank() }?.let { ModelDiscovery.friendlyName(it) } ?: "Elegir modelo") + "  ▾"
    }
    private fun showModelPicker() {
        val choices = providers.flatMap { p -> p.selected.map { p to it } }
        if (choices.isEmpty()) { showSettings("providers"); return }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(14)) }
        val dialog = AlertDialog.Builder(this).setTitle("Selecciona un modelo").setView(ScrollView(this).apply { addView(container) }).setNegativeButton("Cerrar", null).create()
        choices.forEach { (provider, model) -> settingsRow(container, model.name, provider.name) { activateProvider(provider, model); dialog.dismiss() } }
        dialog.show()
    }
    private fun closeSettings() {
        scanVersion++; scanHandler.removeCallbacksAndMessages(null)
        settingsOverlay?.let { screen.removeView(it) }; settingsOverlay = null; settingsPage = ""; editingId = null
    }
    private fun showSettings(page: String, providerId: String? = null) {
        settingsOverlay?.let { screen.removeView(it) }
        settingsPage = page; editingId = providerId
        val frame = FrameLayout(this).apply { setBackgroundColor(bg); isClickable = true }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val insets = root.rootWindowInsets
        val top = if (insets == null) 0 else if (Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()).top else @Suppress("DEPRECATION") insets.systemWindowInsetTop
        val bottom = if (insets == null) 0 else if (Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.systemBars()).bottom else @Suppress("DEPRECATION") insets.systemWindowInsetBottom
        column.setPadding(0, top, 0, bottom)
        frame.setOnApplyWindowInsetsListener { _, current ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = current.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val keyboard = current.getInsets(WindowInsets.Type.ime())
                column.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            } else {
                @Suppress("DEPRECATION")
                column.setPadding(current.systemWindowInsetLeft, current.systemWindowInsetTop, current.systemWindowInsetRight, current.systemWindowInsetBottom)
            }
            current
        }
        frame.addView(column, FrameLayout.LayoutParams(-1, -1))
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(18), dp(8)) }
        bar.addView(text("‹", 34f, ink).apply { gravity = Gravity.CENTER; contentDescription = "Volver"; setOnClickListener { onBackPressed() } }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val heading = when (page) { "home" -> "Configuraciones"; "providers" -> "Proveedores"; "general" -> "Ajustes generales"; "changes" -> "Cambios generales"; else -> if (providerId == null) "Agregar proveedor" else "Configurar proveedor" }
        bar.addView(text(heading, 22f).apply { typeface = Typeface.create("serif", Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
        column.addView(bar)
        if (page == "general" || page == "changes") {
            column.setBackgroundColor(Color.WHITE)
            column.addView(text("Próximamente", 19f, muted).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            val scroll = ScrollView(this).apply { isFillViewport = true }
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(14), dp(22), dp(24)) }
            scroll.addView(body); column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            when (page) {
                "home" -> {
                    body.addView(text("Personaliza tu experiencia", 15f, muted).apply { setPadding(0, 0, 0, dp(18)) })
                    settingsRow(body, "Proveedores", "Configura tus modelos y conexiones") { showSettings("providers") }
                    settingsRow(body, "Ajustes generales", "Próximamente") { showSettings("general") }
                    settingsRow(body, "Cambios generales", "Próximamente") { showSettings("changes") }
                }
                "providers" -> {
                    if (!providersAvailable) body.addView(text("No se pueden modificar los proveedores porque no se pudo leer su configuración.", 14f, accent))
                    val active = prefs.getString("active_provider_id", null)
                    body.addView(text("Elige un proveedor para configurarlo y usarlo.", 14f, muted).apply { setPadding(0, 0, 0, dp(16)) })
                    providers.forEach { p ->
                        val description = when { p.id == active -> "En uso · ${protocolName(p.type)}"; (p.key.isNotBlank() || p.type == 6) && p.selected.isNotEmpty() -> "${p.selected.size} modelos · ${protocolName(p.type)}"; else -> "Sin configurar · ${protocolName(p.type)}" }
                        settingsRow(body, p.name, description) { if (providersAvailable) showSettings("editor", p.id) }
                    }
                    val add = button("＋  Agregar proveedor").apply { setTextColor(accent); setOnClickListener { if (providersAvailable) showSettings("editor") } }
                    body.addView(add, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(14) })
                }
                "editor" -> renderProviderEditor(body, providerId)
            }
        }
        screen.addView(frame, FrameLayout.LayoutParams(-1, -1)); settingsOverlay = frame
    }
    private fun settingsRow(parent: LinearLayout, label: String, detail: String, click: () -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = shape(Color.WHITE, 14); setPadding(dp(16), dp(13), dp(16), dp(13)); setOnClickListener { click() } }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(label, 16f).apply { typeface = Typeface.DEFAULT_BOLD })
        labels.addView(text(detail, 12f, muted).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(text("›", 24f, muted))
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
    }
    private fun protocolName(type: Int) = when (type) { 0 -> "Gemini nativo"; 1 -> "Anthropic Messages"; 4 -> "OpenAI Responses"; 6 -> "Ollama nativo"; else -> "Chat Completions" }
    private fun renderProviderEditor(body: LinearLayout, providerId: String?) {
        val existing = providers.find { it.id == providerId }
        if (providerId != null && existing == null) return
        var type = existing?.type ?: 5
        val chosen = linkedMapOf<String, ModelInfo>()
        existing?.selected?.forEach { chosen[it.id] = it }
        body.addView(text("Tu conexión", 25f).apply { typeface = Typeface.create("serif", Typeface.NORMAL) })
        body.addView(text("Configura el proveedor y elige solo los modelos que quieras usar.", 14f, muted).apply { setPadding(0, dp(6), 0, dp(18)) })
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)); background = shape(Color.WHITE, 18) }
        body.addView(card)
        var selector: Spinner? = null
        if (existing == null) {
            card.addView(text("COMPATIBILIDAD", 11f, muted).apply { typeface = Typeface.DEFAULT_BOLD })
            val options = arrayOf("Google Gemini", "OpenAI Chat Completions", "OpenAI Responses", "Anthropic Messages", "Ollama nativo")
            selector = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, options); setSelection(1); background = shape(bg, 10) }
            card.addView(selector, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(7); bottomMargin = dp(8) })
        } else card.addView(text(protocolName(type).uppercase(), 11f, accent).apply { typeface = Typeface.DEFAULT_BOLD })
        fun field(label: String, hintText: String, value: String, secret: Boolean = false): EditText {
            card.addView(text(label, 13f, ink).apply { setPadding(0, dp(14), 0, dp(7)); typeface = Typeface.DEFAULT_BOLD })
            val edit = EditText(this).apply {
                setSingleLine(true); textSize = 15f; setTextColor(ink); setHintTextColor(muted); hint = hintText
                background = shape(bg, 12); setPadding(dp(14), dp(12), dp(14), dp(12))
                if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setText(value)
            }
            card.addView(edit, LinearLayout.LayoutParams(-1, dp(50)))
            return edit
        }
        val name = field("Nombre", "Ej. Mi proveedor", existing?.name.orEmpty())
        val url = field("URL del endpoint", "https://…", existing?.url.orEmpty())
        val key = field("API key", "Clave del proveedor", existing?.key.orEmpty(), true)
        card.addView(text("Las claves se guardan en texto plano. Ollama local puede usar HTTP sin cifrar: úsalo solo en redes de confianza.", 12f, muted).apply { setPadding(0, dp(14), 0, 0) })
        val modelHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(24), 0, dp(6)) }
        modelHeader.addView(text("Modelos disponibles", 20f).apply { typeface = Typeface.create("serif", Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
        val refresh = text("Actualizar", 13f, accent).apply { setPadding(dp(12), dp(10), dp(12), dp(10)); background = shape(panel, 10) }
        modelHeader.addView(refresh); body.addView(modelHeader)
        val status = text("Completa nombre, URL y clave para buscar modelos.", 13f, muted)
        body.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list)
        var discovered = emptyList<ModelInfo>()
        fun drawModels() {
            list.removeAllViews()
            val all = linkedMapOf<String, ModelInfo>()
            chosen.values.forEach { all[it.id] = it }; discovered.forEach { if (!all.containsKey(it.id)) all[it.id] = it }
            all.values.forEach { model ->
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = shape(Color.WHITE, 14); setPadding(dp(14), dp(11), dp(10), dp(11)) }
                val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                labels.addView(text(model.name, 15f).apply { typeface = Typeface.DEFAULT_BOLD })
                labels.addView(text(model.id, 12f, muted).apply { setPadding(0, dp(3), 0, 0); maxLines = 2 })
                if (model.detail.isNotBlank()) labels.addView(text(model.detail, 11f, muted).apply { setPadding(0, dp(3), 0, 0); maxLines = 3 })
                row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                val selected = chosen.containsKey(model.id)
                val toggle = text(if (selected) "−" else "+", 24f, if (selected) Color.WHITE else accent).apply {
                    gravity = Gravity.CENTER; background = shape(if (selected) accent else panel, 12)
                    contentDescription = (if (selected) "Quitar " else "Agregar ") + model.name
                    setOnClickListener { if (chosen.containsKey(model.id)) chosen.remove(model.id) else chosen[model.id] = model; drawModels() }
                }
                row.addView(toggle, LinearLayout.LayoutParams(dp(42), dp(42)))
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
        drawModels()
        val manual = EditText(this).apply { hint = "ID de modelo manual (opcional)"; textSize = 14f; setSingleLine(true); background = shape(Color.WHITE, 12); setPadding(dp(14), dp(12), dp(14), dp(12)) }
        body.addView(manual, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(6) })
        body.addView(text("＋  Agregar ID manual", 14f, accent).apply { setPadding(dp(8), dp(12), 0, dp(12)); setOnClickListener { val id = manual.text.toString().trim(); if (id.isNotBlank()) { chosen[id] = ModelInfo(id, ModelDiscovery.friendlyName(id)); manual.setText(""); drawModels() } } })
        fun validInputs() = name.text.isNotBlank() && ModelDiscovery.isAllowedEndpoint(url.text.toString().trim(), type) && (type == 6 || key.text.isNotBlank())
        fun doScan() {
            val version = ++scanVersion
            scanHandler.removeCallbacksAndMessages(null)
            if (!validInputs()) { discovered = emptyList(); drawModels(); status.text = "Completa nombre, URL válida y clave para buscar modelos."; return }
            val endpoint = url.text.toString().trim(); val secret = key.text.toString().trim(); val capturedType = type
            status.text = "Buscando modelos…"
            executor.execute {
                val result = try { Result.success(ModelDiscovery.fetch(endpoint, capturedType, secret)) } catch (e: Exception) { Result.failure(e) }
                runOnUiThread {
                    if (isDestroyed || version != scanVersion || settingsPage != "editor" || !body.isAttachedToWindow) return@runOnUiThread
                    result.fold(onSuccess = { found -> discovered = found; found.forEach { if (chosen.containsKey(it.id)) chosen[it.id] = it }; drawModels(); status.text = if (found.isEmpty()) "No se encontraron modelos. Puedes agregar un ID manual." else "${found.size} modelos encontrados · toca + para agregar" },
                        onFailure = { discovered = emptyList(); drawModels(); status.text = "No se pudieron consultar los modelos. ${it.message ?: "Error de conexión."} Puedes agregar un ID manual." })
                }
            }
        }
        fun scheduleScan() {
            val version = ++scanVersion; scanHandler.removeCallbacksAndMessages(null)
            discovered = emptyList(); drawModels()
            if (!validInputs()) { status.text = "Completa nombre, URL válida y clave para buscar modelos."; return }
            status.text = "Preparando búsqueda…"
            scanHandler.postDelayed({ if (version == scanVersion) doScan() }, 1200)
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { scheduleScan() }
        }
        name.addTextChangedListener(watcher); url.addTextChangedListener(watcher); key.addTextChangedListener(watcher)
        if (selector != null) {
            val types = intArrayOf(0, 5, 4, 1, 6)
            selector.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { val newType = types[position]; if (newType != type) { type = newType; chosen.clear(); scheduleScan() } }
            }
        }
        refresh.setOnClickListener { doScan() }
        body.addView(button("Guardar proveedor").apply {
            setTextColor(Color.WHITE); background = shape(accent, 14)
            setOnClickListener {
                if (!validInputs()) { Toast.makeText(this@MainActivity, "Completa nombre, URL válida y clave (opcional para Ollama).", Toast.LENGTH_LONG).show(); return@setOnClickListener }
                val p = Provider(existing?.id ?: "custom_" + UUID.randomUUID(), type, name.text.toString().trim(), url.text.toString().trim(), chosen.values.firstOrNull()?.id.orEmpty(), key.text.toString().trim(), existing?.custom ?: true, chosen.values.toList())
                val index = providers.indexOfFirst { it.id == p.id }; if (index >= 0) providers[index] = p else providers.add(p)
                saveProviders()
                val active = prefs.getString("active_provider_id", null)
                if (active == p.id || active == null) {
                    val selected = p.selected.find { it.id == prefs.getString("model", "") } ?: p.selected.firstOrNull()
                    if (selected != null) activateProvider(p, selected)
                    else if (active == p.id) { prefs.edit().remove("active_provider_id").remove("model_name").remove("model").remove("key").apply(); title.text = "Sin modelo"; refreshModelButton() }
                }
                closeSettings()
            }
        }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(16) })
        if (existing?.custom == true) body.addView(button("Eliminar proveedor").apply {
            setTextColor(accent)
            setOnClickListener { AlertDialog.Builder(this@MainActivity).setTitle("Eliminar proveedor").setMessage("Se borrarán su configuración y su clave de este dispositivo.")
                .setPositiveButton("Eliminar") { _, _ ->
                    providers.removeAll { it.id == existing.id }; saveProviders()
                    if (prefs.getString("active_provider_id", null) == existing.id) { prefs.edit().remove("active_provider_id").remove("key").remove("url").remove("model").remove("model_name").remove("name").apply(); title.text = "Sin modelo"; refreshModelButton() }
                    showSettings("providers")
                }.setNegativeButton("Cancelar", null).show() }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        scheduleScan()
    }
    private fun sendMessage() {
        val content = composer.text.toString().trim()
        if (content.isEmpty() || busy) return
        if (prefs.getString("active_provider_id", null) == null || prefs.getString("model", "").isNullOrBlank()) { showModelPicker(); return }
        val type = prefs.getInt("type", 0); val endpoint = prefs.getString("url", "")!!; val key = prefs.getString("key", "")!!; val model = prefs.getString("model", "")!!
        messages.add("user" to content); if (messages.size == 1) transcript.removeAllViews(); addMessage("user", content); composer.setText("")
        busy = true; send.isEnabled = false; send.text = "…"
        val snapshot = messages.toList(); val requestGeneration = ++generation
        executor.execute {
            val result = try { Result.success(request(type, endpoint, key, model, snapshot)) } catch (e: Exception) { Result.failure(e) }
            runOnUiThread {
                if (isFinishing || isDestroyed || requestGeneration != generation) return@runOnUiThread
                busy = false; send.isEnabled = true; send.text = "Enviar"
                result.fold(onSuccess = { answer -> messages.add("assistant" to answer); addMessage("assistant", answer); saveCurrentChat() }, onFailure = { error ->
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
            2, 3, 5 -> { val list = JSONArray(); history.forEach { (role, value) -> list.put(JSONObject().put("role", role).put("content", value)) }; body.put("model", model).put("messages", list).put("stream", false) }
            6 -> { val list = JSONArray(); history.forEach { (role, value) -> list.put(JSONObject().put("role", role).put("content", value)) }; body.put("model", model).put("messages", list).put("stream", false) }
            4 -> { val list = JSONArray(); history.forEach { (role, value) -> list.put(JSONObject().put("role", role).put("content", value)) }; body.put("model", model).put("input", list).put("store", false) }
            else -> error("Proveedor desconocido")
        }
        require(ModelDiscovery.isAllowedEndpoint(endpoint, type)) { "URL no permitida. Usa HTTPS o una dirección local HTTP para Ollama." }
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.connectTimeout = 30000; connection.readTimeout = 180000; connection.doOutput = true
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Content-Type", "application/json")
            when (type) {
                0 -> connection.setRequestProperty("x-goog-api-key", key)
                1 -> { connection.setRequestProperty("x-api-key", key); connection.setRequestProperty("anthropic-version", "2026-10-21") }
                else -> if (key.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $key")
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val raw = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(8192) }.orEmpty()
                val serverMessage = try {
                    val parsed = JSONObject(raw)
                    val error = parsed.opt("error")
                    when (error) {
                        is JSONObject -> error.optString("message")
                        is String -> error
                        else -> parsed.optString("message")
                    }
                } catch (_: Exception) { "" }
                val safeMessage = (if (key.isNotBlank()) serverMessage.replace(key, "[clave oculta]") else serverMessage).take(400)
                throw IllegalStateException("HTTP $status." + if (safeMessage.isNotBlank()) " $safeMessage" else " Revisa la URL, el protocolo, el modelo y la clave.")
            }
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(response)
            val answer = when (type) {
                0 -> { val parts = json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts"); (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optString("text")?.takeIf(String::isNotBlank) }.joinToString("\n") }
                1 -> { val blocks = json.getJSONArray("content"); (0 until blocks.length()).mapNotNull { blocks.optJSONObject(it)?.takeIf { b -> b.optString("type") == "text" }?.optString("text") }.joinToString("\n") }
                6 -> json.getJSONObject("message").optString("content")
                2, 3, 5 -> json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
                else -> { val out = json.getJSONArray("output"); val pieces = mutableListOf<String>(); for (i in 0 until out.length()) { val block = out.optJSONObject(i) ?: continue; val parts = block.optJSONArray("content") ?: continue; for (j in 0 until parts.length()) { val part = parts.optJSONObject(j) ?: continue; if (part.optString("type") == "output_text") pieces.add(part.optString("text")) } }; pieces.joinToString("\n") }
            }
            if (answer.isBlank()) throw IllegalStateException("El proveedor no devolvió texto.")
            return answer
        } finally { connection.disconnect() }
    }
    override fun onDestroy() { generation++; scanVersion++; scanHandler.removeCallbacksAndMessages(null); executor.shutdownNow(); super.onDestroy() }
}
