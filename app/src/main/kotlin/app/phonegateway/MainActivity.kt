package app.phonegateway

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var settings: Settings
    private val ui = Handler(Looper.getMainLooper())

    private val ink = Color.parseColor("#0F172A")
    private val muted = Color.parseColor("#64748B")
    private val line = Color.parseColor("#E2E8F0")
    private val primary = Color.parseColor("#0F766E")
    private val ok = Color.parseColor("#15803D")
    private val bad = Color.parseColor("#B91C1C")
    private val warn = Color.parseColor("#B45309")
    private val violet = Color.parseColor("#6D28D9")

    private lateinit var content: FrameLayout
    private lateinit var tabViews: List<TextView>
    private var tab = 0
    private var filter = "ALL"
    private var shownSignature = ""

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { shownSignature = ""; refresh() }
    private val pickJson = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importFile(it) } }

    private val allPermissions: List<String> by lazy {
        val l = mutableListOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS)
        if (android.os.Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        l
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        JobStore.init(this)
        title = "Phone Gateway"
        window.statusBarColor = primary

        content = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE); elevation = dp(8).toFloat()
        }
        val labels = listOf("⌂\nAccueil", "✉\nMessages", "⚙\nRéglages")
        tabViews = labels.mapIndexed { i, t ->
            TextView(this).apply {
                text = t; textSize = 12f; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(8))
                setOnClickListener { select(i) }
                bar.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bar)
        })
        select(0)
    }

    override fun onResume() { super.onResume(); Thread { Retention.purge(this) }.start(); tick() }
    override fun onPause() { super.onPause(); ui.removeCallbacksAndMessages(null) }
    private fun tick() { refresh(); ui.postDelayed({ tick() }, 2000) }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Importer un fichier JSON").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) { pickJson.launch(arrayOf("application/json", "text/plain", "*/*")); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun select(i: Int) {
        tab = i; shownSignature = ""
        tabViews.forEachIndexed { k, v -> v.setTextColor(if (k == i) primary else muted); v.typeface = if (k == i) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
        refresh()
    }

    private fun refresh() {
        val sig = when (tab) {
            0 -> homeSignature()
            1 -> messagesSignature()
            else -> "settings"
        }
        if (sig == shownSignature) return
        shownSignature = sig
        content.removeAllViews()
        content.addView(when (tab) { 0 -> homeView(); 1 -> messagesView(); else -> settingsView() })
    }

    private fun missingPermissions() = allPermissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    private fun batteryOk() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    private fun homeSignature() = "${GatewayService.running}|${localAddresses()}|${settings.token}|${settings.port}|${missingPermissions().size}|${batteryOk()}"

    private fun homeView(): View {
        val running = GatewayService.running
        val col = column()

        val sw = Switch(this).apply {
            isChecked = running
            setOnCheckedChangeListener { _, on ->
                if (on) GatewayService.start(this@MainActivity) else GatewayService.stop(this@MainActivity)
                ui.postDelayed({ shownSignature = ""; refresh() }, 700)
            }
        }
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(dot(if (running) ok else muted))
            addView(TextView(context).apply { text = if (running) "Serveur actif" else "Serveur arrêté"; textSize = 20f; setTextColor(ink); typeface = Typeface.DEFAULT_BOLD },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(sw)
        })

        if (missingPermissions().isNotEmpty()) col.addView(notice("Autorisations manquantes", "Autoriser") { perms.launch(allPermissions.toTypedArray()) })
        if (!batteryOk()) col.addView(notice("Batterie restreinte", "Corriger") { askBatteryExemption() })

        val addrs = localAddresses()
        if (addrs.isEmpty()) col.addView(label("Aucun réseau"))
        for ((name, ip) in addrs) col.addView(copyRow(name, "http://$ip:${settings.port}"))
        col.addView(copyRow("Utilisateur", "sms"))
        col.addView(copyRow("Mot de passe", settings.token))
        return scroll(col)
    }

    private class Entry(val at: Long, val title: String, val detail: String, val badge: String, val color: Int, val full: String)

    private fun messagesSignature(): String {
        val jobs = JobStore.all()
        return "$filter|${jobs.size}|${jobs.sumOf { it.updatedAt % 1000003 }}|${Inbox.all().size}"
    }

    private fun entries(): List<Entry> {
        val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
        val list = ArrayList<Entry>()
        for (j in JobStore.all()) {
            val running = j.status == Status.QUEUED || j.status == Status.SENDING || j.status == Status.CALLING
            val keep = when (filter) {
                "ALL" -> true
                "RUNNING" -> running
                "SENT" -> j.kind == Kind.SMS && (j.status == Status.SENT || j.status == Status.DELIVERED)
                "FAILED" -> j.status == Status.FAILED
                "CALL" -> j.kind == Kind.CALL
                else -> false
            }
            if (!keep) continue
            val color = when (j.status) { Status.DELIVERED, Status.DONE -> ok; Status.SENT -> primary; Status.FAILED -> bad; else -> warn }
            val detail = if (j.kind == Kind.SMS) j.text.take(70) else when (j.verdict) {
                "VOICEMAIL" -> "boîte vocale"; "ALIVE" -> "répond par SMS"; "OFFLINE" -> "hors ligne"
                else -> if (j.durationSec >= 0) "pas de réponse" else "appel…"
            }
            val full = "${if (j.kind == Kind.SMS) j.text else "Appel"}\n\n${j.to}\n${j.verdict ?: j.status.name}${j.error?.let { "\n$it" } ?: ""}\n${fmt.format(Date(j.createdAt))}\nid ${j.id}"
            list.add(Entry(j.updatedAt, (if (j.kind == Kind.SMS) "✉  " else "☎  ") + j.to, detail + (j.error?.let { "  ⚠" } ?: ""), j.verdict ?: j.status.name, color, full))
        }
        if (filter == "ALL" || filter == "RECEIVED") for (m in Inbox.all()) {
            list.add(Entry(m.at, "⬇  ${m.from}", m.text.take(70), "REÇU", violet, "${m.text}\n\n${m.from}\n${fmt.format(Date(m.at))}"))
        }
        return list.sortedByDescending { it.at }.take(300)
    }

    private fun messagesView(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        listOf("ALL" to "Tous", "RUNNING" to "En cours", "SENT" to "Envoyés", "FAILED" to "Échecs", "RECEIVED" to "Reçus", "CALL" to "Appels").forEach { (key, name) ->
            val on = key == filter
            chips.addView(TextView(this).apply {
                text = name; textSize = 13f; setPadding(dp(14), dp(7), dp(14), dp(7))
                setTextColor(if (on) Color.WHITE else ink)
                background = rounded(if (on) primary else Color.parseColor("#F1F5F9"), 20)
                setOnClickListener { filter = key; shownSignature = ""; refresh() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        }
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) })

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(8)) }
        val items = entries()
        if (items.isEmpty()) list.addView(label("Aucun message").apply { gravity = Gravity.CENTER; setPadding(0, dp(48), 0, 0) })
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        items.forEach { e ->
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), dp(12), dp(16), dp(12))
                val left = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply { text = e.title; textSize = 15f; setTextColor(ink); typeface = Typeface.DEFAULT_BOLD })
                    addView(TextView(context).apply { text = e.detail; textSize = 13f; setTextColor(muted); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                }
                val right = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.END
                    addView(chip(e.badge, e.color))
                    addView(TextView(context).apply { text = timeFmt.format(Date(e.at)); textSize = 11f; setTextColor(muted); gravity = Gravity.END })
                }
                addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(right)
                setOnClickListener { AlertDialog.Builder(this@MainActivity).setMessage(e.full).setPositiveButton("OK", null).show() }
            })
            list.addView(View(this).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun settingsView(): View {
        val col = column()
        val port = input("Port", InputType.TYPE_CLASS_NUMBER).apply { setText(settings.port.toString()) }
        val jobHook = input("Webhook statuts", InputType.TYPE_TEXT_VARIATION_URI).apply { setText(settings.jobWebhookUrl) }
        val smsHook = input("Webhook SMS reçus", InputType.TYPE_TEXT_VARIATION_URI).apply { setText(settings.webhookUrl) }
        val auto = Switch(this).apply { isChecked = settings.autostart }
        col.addView(port); col.addView(jobHook); col.addView(smsHook)
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(label("Démarrage automatique"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(auto)
        })
        col.addView(button("Enregistrer", primary) {
            settings.port = port.text.toString().toIntOrNull()?.coerceIn(1024, 65535) ?: 8088
            settings.jobWebhookUrl = jobHook.text.toString()
            settings.webhookUrl = smsHook.text.toString()
            settings.autostart = auto.isChecked
            if (GatewayService.running) { GatewayService.stop(this); ui.postDelayed({ GatewayService.start(this) }, 700) }
            toast("Enregistré")
        })
        col.addView(button("Nouveau mot de passe", bad, outline = true) {
            AlertDialog.Builder(this).setMessage("Le serveur devra utiliser le nouveau mot de passe.")
                .setPositiveButton("Changer") { _, _ -> settings.regenerateToken(); toast("Nouveau mot de passe") }
                .setNegativeButton("Annuler", null).show()
        })
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "" }
        col.addView(label("v$version  •  messages gardés ${Retention.DAYS} jours").apply { gravity = Gravity.CENTER; setPadding(0, dp(16), 0, 0) })
        return scroll(col)
    }

    private fun importFile(uri: Uri) {
        val text = try { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "" }
        catch (e: Exception) { return toast("Lecture impossible") }
        val r = BatchParser.parse(text)
        if (r.items.isNotEmpty()) {
            if (!GatewayService.running) GatewayService.start(this)
            Dispatcher.start(this); Dispatcher.enqueueSms(r.items, r.delayMs ?: 2000)
        }
        toast("${r.items.size} message(s) en file" + if (r.errors.isEmpty()) "" else ", ${r.errors.size} ignoré(s):\n" + r.errors.take(6).joinToString("\n"))
        select(1)
    }

    @android.annotation.SuppressLint("BatteryLife")
    private fun askBatteryExemption() {
        startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }

    private fun localAddresses(): List<Pair<String, String>> = try {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.flatMap { ni ->
            ni.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { !it.isLoopbackAddress }.map { a ->
                val ip = a.hostAddress ?: ""
                val n = ni.name.lowercase()
                val name = when {
                    ip.startsWith("100.") && (ip.split(".").getOrNull(1)?.toIntOrNull() ?: 0) in 64..127 -> "Tailscale"
                    n.startsWith("tun") || n.startsWith("tailscale") || n.startsWith("wg") -> "VPN"
                    n.startsWith("wlan") || n.startsWith("wifi") -> "Wi-Fi"
                    n.startsWith("eth") -> "Ethernet"
                    else -> "Mobile"
                }
                name to ip
            }
        }
    } catch (_: Exception) { emptyList() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    private fun column() = object : LinearLayout(this) {
        override fun addView(child: View?) {
            if (child != null && childCount > 0) {
                val p = (child.layoutParams as? LayoutParams) ?: LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                p.topMargin = dp(12); child.layoutParams = p
            }
            super.addView(child)
        }
    }.apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }

    private fun scroll(v: View) = ScrollView(this).apply { addView(v) }

    private fun label(t: String) = TextView(this).apply { text = t; textSize = 14f; setTextColor(muted) }

    private fun dot(color: Int) = View(this).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(10) }
    }

    private fun copyRow(name: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10))
        background = rounded(Color.parseColor("#F1F5F9"), 12)
        addView(TextView(context).apply { text = name; textSize = 12f; setTextColor(muted) })
        addView(TextView(context).apply { text = value; textSize = 16f; setTextColor(ink); typeface = Typeface.MONOSPACE })
        setOnClickListener {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(name, value)); toast("Copié")
        }
    }

    private fun notice(text: String, action: String, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(8), dp(8), dp(8))
        background = rounded(Color.parseColor("#FEF3C7"), 12)
        addView(TextView(context).apply { this.text = text; textSize = 14f; setTextColor(warn) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Button(context).apply { this.text = action; isAllCaps = false; setTextColor(warn); setBackgroundColor(Color.TRANSPARENT); setOnClickListener { onClick() } })
    }

    private fun chip(t: String, color: Int) = TextView(this).apply {
        text = t; textSize = 11f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        background = rounded(color, 20); setPadding(dp(8), dp(2), dp(8), dp(2))
    }

    private fun input(hint: String, type: Int) = EditText(this).apply {
        this.hint = hint; inputType = type; textSize = 15f; setTextColor(ink)
        background = rounded(Color.parseColor("#F1F5F9"), 10); setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun button(t: String, color: Int, outline: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 15f
        if (outline) { setTextColor(color); background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(10).toFloat(); setStroke(dp(1), color) } }
        else { setTextColor(Color.WHITE); background = rounded(color, 10) }
        setOnClickListener { onClick() }
    }
}
