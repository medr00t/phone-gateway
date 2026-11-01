package app.phonegateway

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var settings: Settings
    private lateinit var info: TextView
    private lateinit var log: TextView
    private val ui = Handler(Looper.getMainLooper())

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    private val pickJson = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importFile(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        JobStore.init(this)

        fun btn(label: String, onClick: () -> Unit) = Button(this).apply { text = label; setOnClickListener { onClick() } }

        val port = EditText(this).apply { setText(settings.port.toString()); inputType = InputType.TYPE_CLASS_NUMBER; hint = "Port" }
        val hook = EditText(this).apply { setText(settings.webhookUrl); hint = "Webhook SMS reçus (optionnel)"; inputType = InputType.TYPE_TEXT_VARIATION_URI }
        info = TextView(this).apply { setTextIsSelectable(true) }
        log = TextView(this).apply { typeface = android.graphics.Typeface.MONOSPACE; textSize = 12f }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(info)
            addView(port); addView(hook)
            addView(btn("1. Autoriser SMS / appels") { askPermissions() })
            addView(btn("2. Démarrer la passerelle") {
                settings.port = port.text.toString().toIntOrNull()?.coerceIn(1024, 65535) ?: 8088
                settings.webhookUrl = hook.text.toString()
                GatewayService.stop(this@MainActivity); GatewayService.start(this@MainActivity); refresh()
            })
            addView(btn("3. Désactiver l'optimisation batterie (obligatoire)") { askBatteryExemption() })
            addView(btn("Arrêter") { GatewayService.stop(this@MainActivity); refresh() })
            addView(btn("Importer un fichier JSON de messages") { pickJson.launch(arrayOf("application/json", "text/plain", "*/*")) })
            addView(btn("Nouveau jeton") { settings.regenerateToken(); refresh() })
            addView(log)
        }
        setContentView(ScrollView(this).apply { addView(col, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) })
    }

    override fun onResume() { super.onResume(); tick() }
    override fun onPause() { super.onPause(); ui.removeCallbacksAndMessages(null) }

    private fun tick() { refresh(); ui.postDelayed({ tick() }, 2000) }

    private fun askPermissions() {
        val list = mutableListOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS)
        if (android.os.Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        perms.launch(list.toTypedArray())
    }

    // Without this, Android's idle mode (Doze) and the maker's battery saver pause the app after a while.
    @android.annotation.SuppressLint("BatteryLife")
    private fun askBatteryExemption() {
        val pm = getSystemService(android.os.PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) { toast("Déjà désactivée"); return }
        startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")))
    }

    private fun importFile(uri: Uri) {
        val text = try { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "" }
        catch (e: Exception) { toast("Lecture impossible: ${e.message}"); return }
        val r = BatchParser.parse(text)
        if (r.items.isNotEmpty()) {
            Dispatcher.start(this)
            Dispatcher.enqueueSms(r.items, r.delayMs ?: 2000)
        }
        val msg = "${r.items.size} message(s) en file" + if (r.errors.isEmpty()) "" else ", ${r.errors.size} ignoré(s):\n" + r.errors.take(8).joinToString("\n")
        toast(msg)
        refresh()
    }

    private fun refresh() {
        val jobs = JobStore.all()
        info.text = "Jeton (à garder secret):\n${settings.token}\n\nAPI: http://<adresse du téléphone>:${settings.port}/api/status" +
            "\nEn file: ${Dispatcher.pending()}   Total: ${jobs.size}" +
            "\nBatterie: " + if (getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) "OK (non restreinte)" else "À DÉSACTIVER (bouton 3)"
        log.text = jobs.takeLast(25).reversed().joinToString("\n") {
            "${it.kind.name.take(1)} ${it.to} ${it.status}" +
                (if (it.kind == Kind.CALL && it.durationSec >= 0) " ${it.durationSec}s" else "") + (it.error?.let { e -> " ($e)" } ?: "")
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
