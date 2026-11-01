package app.phonegateway

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
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

    // palette
    private val bg = Color.parseColor("#F1F5F9")
    private val ink = Color.parseColor("#0F172A")
    private val muted = Color.parseColor("#64748B")
    private val primary = Color.parseColor("#0F766E")
    private val ok = Color.parseColor("#15803D")
    private val bad = Color.parseColor("#B91C1C")
    private val warn = Color.parseColor("#B45309")

    private lateinit var statusChip: TextView
    private lateinit var toggleBtn: Button
    private lateinit var urlsBox: LinearLayout
    private lateinit var tokenView: TextView
    private lateinit var checksBox: LinearLayout
    private lateinit var journalBox: LinearLayout
    private lateinit var summary: TextView
    private lateinit var portInput: EditText
    private lateinit var hookInput: EditText
    private lateinit var toInput: EditText
    private lateinit var textInput: EditText

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    private val pickJson = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importFile(it) } }

    private val allPermissions: List<Pair<String, String>> by lazy {
        val l = mutableListOf(
            Manifest.permission.SEND_SMS to "Envoyer des SMS",
            Manifest.permission.RECEIVE_SMS to "Recevoir des SMS",
            Manifest.permission.CALL_PHONE to "Passer des appels",
            Manifest.permission.READ_CALL_LOG to "Lire le journal d'appels",
            Manifest.permission.READ_PHONE_STATE to "État du téléphone",
            Manifest.permission.ANSWER_PHONE_CALLS to "Raccrocher les appels",
        )
        if (android.os.Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS to "Notifications")
        l
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        JobStore.init(this)
        title = "Phone Gateway"
        window.statusBarColor = primary

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(28)) }

        // ── Header: state + start/stop ──
        statusChip = chip("", muted)
        toggleBtn = button("", primary) { toggleService() }
        root.addView(card(
            row(title("Phone Gateway"), statusChip),
            toggleBtn,
        ))

        // ── Connection: URLs + token ──
        urlsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tokenView = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 15f; setTextColor(ink); setTextIsSelectable(true)
            background = rounded(Color.parseColor("#E2E8F0"), 10); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        root.addView(card(
            heading("Connexion"),
            urlsBox,
            tokenView,
            row(
                button("Copier le jeton", primary, outline = true) { copy("Jeton", settings.token) },
                button("Nouveau jeton", bad, outline = true) { settings.regenerateToken(); refresh() },
            ),
        ))

        // ── Checklist ──
        checksBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(card(heading("Vérifications"), checksBox))

        // ── Send / call ──
        toInput = input("Numéro", InputType.TYPE_CLASS_PHONE)
        textInput = input("Message", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE).apply { minLines = 2 }
        root.addView(card(
            heading("Envoyer maintenant"),
            toInput, textInput,
            row(
                button("Envoyer le SMS", primary) { sendSms() },
                button("Appeler", ok) { placeCall() },
            ),
            button("Importer un fichier JSON", primary, outline = true) { pickJson.launch(arrayOf("application/json", "text/plain", "*/*")) },
        ))

        // ── Settings ──
        portInput = input("Port (8088)", InputType.TYPE_CLASS_NUMBER).apply { setText(settings.port.toString()) }
        hookInput = input("Webhook SMS reçus (optionnel)", InputType.TYPE_TEXT_VARIATION_URI).apply { setText(settings.webhookUrl) }
        root.addView(card(
            heading("Réglages"),
            portInput, hookInput,
            button("Enregistrer", primary, outline = true) { saveSettings() },
        ))

        // ── Journal ──
        summary = small("")
        journalBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(card(heading("Journal"), summary, journalBox))

        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) })
    }

    override fun onResume() { super.onResume(); tick() }
    override fun onPause() { super.onPause(); ui.removeCallbacksAndMessages(null) }
    private fun tick() { refresh(); ui.postDelayed({ tick() }, 2000) }

    // ───────────────────────── actions ─────────────────────────

    private fun toggleService() {
        if (GatewayService.running) GatewayService.stop(this) else { saveSettings(); GatewayService.start(this) }
        ui.postDelayed({ refresh() }, 600)
    }

    private fun saveSettings() {
        settings.port = portInput.text.toString().toIntOrNull()?.coerceIn(1024, 65535) ?: 8088
        settings.webhookUrl = hookInput.text.toString()
        if (GatewayService.running) { GatewayService.stop(this); ui.postDelayed({ GatewayService.start(this) }, 700) }
        toast("Réglages enregistrés")
    }

    private fun ensureRunning() { if (!GatewayService.running) GatewayService.start(this); Dispatcher.start(this) }

    private fun sendSms() {
        val to = BatchParser.normalizeNumber(toInput.text.toString()) ?: return toast("Numéro invalide")
        val text = textInput.text.toString()
        if (text.isBlank()) return toast("Message vide")
        ensureRunning()
        Dispatcher.enqueueSms(listOf(BatchItem(to, text, null)), 2000)
        textInput.setText(""); refresh()
    }

    private fun placeCall() {
        val to = BatchParser.normalizeNumber(toInput.text.toString()) ?: return toast("Numéro invalide")
        ensureRunning()
        Dispatcher.enqueueCall(to, null, 25)
        refresh()
    }

    private fun importFile(uri: Uri) {
        val text = try { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "" }
        catch (e: Exception) { return toast("Lecture impossible: ${e.message}") }
        val r = BatchParser.parse(text)
        if (r.items.isNotEmpty()) { ensureRunning(); Dispatcher.enqueueSms(r.items, r.delayMs ?: 2000) }
        toast("${r.items.size} message(s) en file" + if (r.errors.isEmpty()) "" else ", ${r.errors.size} ignoré(s):\n" + r.errors.take(6).joinToString("\n"))
        refresh()
    }

    private fun askPermissions() = perms.launch(allPermissions.map { it.first }.toTypedArray())

    @android.annotation.SuppressLint("BatteryLife")
    private fun askBatteryExemption() {
        startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }

    // ───────────────────────── refresh ─────────────────────────

    private fun refresh() {
        val running = GatewayService.running
        statusChip.text = if (running) "● ACTIVE" else "● ARRÊTÉE"
        (statusChip.background as GradientDrawable).setColor(if (running) ok else muted)
        toggleBtn.text = if (running) "Arrêter la passerelle" else "Démarrer la passerelle"
        styleButton(toggleBtn, if (running) bad else primary, false)

        // URLs
        urlsBox.removeAllViews()
        val addrs = localAddresses()
        if (addrs.isEmpty()) urlsBox.addView(small("Aucun réseau"))
        for ((label, ip) in addrs) {
            val url = "http://$ip:${settings.port}"
            urlsBox.addView(TextView(this).apply {
                text = "$label\n$url"; textSize = 15f; setTextColor(ink); typeface = Typeface.MONOSPACE
                background = rounded(Color.parseColor("#E0F2F1"), 10); setPadding(dp(12), dp(8), dp(12), dp(8))
                layoutParams = lp(top = 6); setOnClickListener { copy("URL", url) }
            })
        }
        tokenView.text = settings.token

        // checklist
        checksBox.removeAllViews()
        val missing = allPermissions.filter { checkSelfPermission(it.first) != PackageManager.PERMISSION_GRANTED }
        checksBox.addView(checkRow(missing.isEmpty(), "Autorisations" + if (missing.isEmpty()) "" else " (${missing.size} manquantes)", "Autoriser") { askPermissions() })
        val battOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        checksBox.addView(checkRow(battOk, if (battOk) "Batterie non restreinte" else "Batterie restreinte", "Corriger") { askBatteryExemption() })
        checksBox.addView(checkRow(running, if (running) "Passerelle active" else "Passerelle arrêtée", if (running) "" else "Démarrer") { toggleService() })

        // journal
        val jobs = JobStore.all()
        val inbox = Inbox.since(0, null)
        summary.text = "${Dispatcher.pending()} en file  •  ${jobs.size} envoyés/appels  •  ${inbox.size} reçus"
        journalBox.removeAllViews()
        if (jobs.isEmpty() && inbox.isEmpty()) journalBox.addView(small("Vide"))
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val lines = jobs.takeLast(25).map { Triple(it.updatedAt, it, null as InboxMessage?) } + inbox.takeLast(10).map { Triple(it.at, null as Job?, it) }
        for ((at, job, msg) in lines.sortedByDescending { it.first }.take(30)) {
            if (job != null) {
                val color = when (job.status) {
                    Status.DELIVERED, Status.DONE -> ok; Status.SENT -> primary; Status.FAILED -> bad; else -> warn
                }
                val detail = if (job.kind == Kind.SMS) job.text.take(60)
                    else if (job.durationSec >= 0) (if (job.connected == true) "décroché" else "pas de réponse") + " • ${job.durationSec}s" else "appel…"
                journalBox.addView(logRow((if (job.kind == Kind.SMS) "✉  " else "☎  ") + job.to, detail + (job.error?.let { "\n⚠ $it" } ?: ""), fmt.format(Date(at)), job.status.name, color))
            } else if (msg != null) {
                journalBox.addView(logRow("⬇  ${msg.from}", msg.text.take(80), fmt.format(Date(at)), "REÇU", Color.parseColor("#6D28D9")))
            }
        }
    }

    private fun localAddresses(): List<Pair<String, String>> = try {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.flatMap { ni ->
            ni.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { !it.isLoopbackAddress }.map { a ->
                val ip = a.hostAddress ?: ""
                val n = ni.name.lowercase()
                val label = when {
                    ip.startsWith("100.") && (ip.split(".").getOrNull(1)?.toIntOrNull() ?: 0) in 64..127 -> "VPN Tailscale"
                    n.startsWith("tun") || n.startsWith("tailscale") || n.startsWith("wg") -> "VPN"
                    n.startsWith("wlan") || n.startsWith("wifi") -> "Wi-Fi"
                    n.startsWith("eth") -> "Ethernet"
                    else -> "Données mobiles"
                }
                label to ip
            }
        }
    } catch (_: Exception) { emptyList() }

    // ───────────────────────── small UI kit ─────────────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }
    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun card(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.WHITE, 16); setPadding(dp(16), dp(14), dp(16), dp(16)); elevation = dp(2).toFloat()
        layoutParams = lp(top = 12)
        views.forEachIndexed { i, v -> addView(v, if (v.layoutParams is LinearLayout.LayoutParams) v.layoutParams else lp()); if (i > 0) (v.layoutParams as LinearLayout.LayoutParams).topMargin = dp(8) }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        views.forEach { addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }) }
    }

    private fun title(t: String) = TextView(this).apply { text = t; textSize = 22f; setTextColor(ink); typeface = Typeface.DEFAULT_BOLD }
    private fun heading(t: String) = TextView(this).apply { text = t; textSize = 17f; setTextColor(ink); typeface = Typeface.DEFAULT_BOLD }
    private fun small(t: String) = TextView(this).apply { text = t; textSize = 13f; setTextColor(muted) }

    private fun chip(t: String, color: Int) = TextView(this).apply {
        text = t; textSize = 12f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        background = rounded(color, 20); setPadding(dp(10), dp(4), dp(10), dp(4))
    }

    private fun input(hint: String, type: Int) = EditText(this).apply {
        this.hint = hint; inputType = type; textSize = 15f; setTextColor(ink)
        background = rounded(Color.parseColor("#F1F5F9"), 10); setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun styleButton(b: Button, color: Int, outline: Boolean) {
        b.isAllCaps = false; b.textSize = 15f
        if (outline) { b.setTextColor(color); b.background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(10).toFloat(); setStroke(dp(1), color) } }
        else { b.setTextColor(Color.WHITE); b.background = rounded(color, 10) }
    }

    private fun button(t: String, color: Int, outline: Boolean = false, onClick: () -> Unit) =
        Button(this).apply { text = t; styleButton(this, color, outline); setOnClickListener { onClick() } }

    private fun checkRow(good: Boolean, label: String, action: String, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = lp(top = 4)
        addView(TextView(context).apply { text = if (good) "✓" else "✗"; textSize = 18f; setTextColor(if (good) ok else bad); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, dp(10), 0) })
        addView(TextView(context).apply { text = label; textSize = 14f; setTextColor(ink) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (!good && action.isNotEmpty()) addView(button(action, primary, true, onClick), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)))
    }

    private fun logRow(head: String, detail: String, time: String, badge: String, color: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; layoutParams = lp(top = 8)
        background = rounded(Color.parseColor("#F8FAFC"), 10); setPadding(dp(10), dp(8), dp(10), dp(8))
        val left = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = head; textSize = 14f; setTextColor(ink); typeface = Typeface.DEFAULT_BOLD })
            addView(TextView(context).apply { text = detail; textSize = 12f; setTextColor(muted) })
        }
        val right = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.END
            addView(chip(badge, color).apply { textSize = 11f })
            addView(TextView(context).apply { text = time; textSize = 11f; setTextColor(muted); gravity = Gravity.END })
        }
        addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(right)
    }

    private fun copy(label: String, value: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, value))
        toast("$label copié")
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
