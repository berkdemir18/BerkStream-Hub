package com.berkstream

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.lagradost.cloudstream3.APIHolder.apis

internal const val WATCH_HISTORY_KEY = "berkstream_watch_history"

/**
 * Eklentinin kendi ayarlari ve kalici deposu.
 *
 * Kendi arayuz kaynagimiz yok; pakete resource dosyasi eklemek yerine sistemin
 * hazir dialoglari kullaniliyor. Menu kategorilere ayrildi: kaynaklar, ana
 * sayfa, oynatma ve bakim.
 */
object BerkStreamSettings {
    @Volatile
    var store: SharedPreferences? = null
        private set

    private const val PREFS = "berkstream"
    private const val KEY_TIMEOUT = "scan_timeout_seconds"
    private const val KEY_SUBTITLES = "subtitles_enabled"
    private const val KEY_SUBTITLE_LANG = "subtitle_language"
    private const val KEY_FRESH = "fresh_shelf_enabled"
    private const val KEY_PERSONAL = "personal_shelf_enabled"
    private const val KEY_PLATFORMS = "platform_shelves_enabled"
    private const val KEY_GENRES = "genre_shelves_enabled"
    private const val KEY_DISCOVERY = "discovery_shelves_enabled"
    private const val KEY_LINK_TARGET = "link_target"
    private const val KEY_PREFER_DUB = "prefer_turkish_dub"

    private val timeoutOptions = listOf(5L, 9L, 15L, 25L)
    private val linkTargets = listOf(4, 6, 10, 20)
    private val subtitleLanguages = listOf("Türkçe + İngilizce", "Sadece Türkçe", "Sadece İngilizce")

    fun attach(context: Context) {
        store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun prefs(context: Context): SharedPreferences =
        store ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { store = it }

    val scanTimeoutSeconds: Long get() = store?.getLong(KEY_TIMEOUT, 9L) ?: 9L
    val subtitlesEnabled: Boolean get() = store?.getBoolean(KEY_SUBTITLES, true) ?: true
    val subtitleLanguage: Int get() = store?.getInt(KEY_SUBTITLE_LANG, 0) ?: 0
    val freshShelfEnabled: Boolean get() = store?.getBoolean(KEY_FRESH, true) ?: true
    val personalShelfEnabled: Boolean get() = store?.getBoolean(KEY_PERSONAL, true) ?: true
    val platformShelvesEnabled: Boolean get() = store?.getBoolean(KEY_PLATFORMS, true) ?: true
    val genreShelvesEnabled: Boolean get() = store?.getBoolean(KEY_GENRES, true) ?: true
    val discoveryShelvesEnabled: Boolean get() = store?.getBoolean(KEY_DISCOVERY, true) ?: true

    /** Kac link toplanınca tarama durdurulsun. */
    val linkTarget: Int get() = store?.getInt(KEY_LINK_TARGET, 6) ?: 6

    /** Turkce dublaj kaynagi listenin en ustune tasinsin mi. */
    val preferTurkishDub: Boolean get() = store?.getBoolean(KEY_PREFER_DUB, true) ?: true

    fun watchCount(): Int = store?.getString(WATCH_HISTORY_KEY, "").orEmpty()
        .split('\n').count { it.isNotBlank() }

    fun open(context: Context) {
        val sections = arrayOf(
            "📡  Kaynaklar",
            "🏠  Ana sayfa rafları",
            "▶️  Oynatma ve altyazı",
            "🧹  Bakım",
            "ℹ️  Hakkında",
        )
        AlertDialog.Builder(context)
            .setTitle("BerkStream")
            .setItems(sections) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> sourcesMenu(context)
                    1 -> shelvesMenu(context)
                    2 -> playbackMenu(context)
                    3 -> maintenanceMenu(context)
                    4 -> aboutDialog(context)
                }
            }
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun sourcesMenu(context: Context) {
        val turkish = runCatching {
            apis.filter { it.lang == "tr" && it.name != "BerkStream" }
        }.getOrElse { emptyList() }
        val live = turkish.count { api ->
            api.supportedTypes.any { it.name.equals("Live", ignoreCase = true) }
        }
        val lines = buildList {
            add("Yüklü Türkçe kaynak: ${turkish.size}")
            add("Canlı yayın kaynağı: $live")
            add("Tarama süresi: ${scanTimeoutSeconds} sn  (değiştirmek için dokun)")
            addAll(turkish.take(40).map { "• ${it.name}" })
        }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Kaynaklar")
            .setItems(lines) { dialog, which ->
                if (which == 2) {
                    val next = timeoutOptions[
                        (timeoutOptions.indexOf(scanTimeoutSeconds).coerceAtLeast(0) + 1) %
                            timeoutOptions.size,
                    ]
                    prefs(context).edit().putLong(KEY_TIMEOUT, next).apply()
                    toast(context, "Tarama süresi: ${next} sn")
                }
                dialog.dismiss()
            }
            .setNegativeButton("Geri", null)
            .show()
    }

    private fun shelvesMenu(context: Context) {
        val labels = arrayOf(
            "Sana Özel",
            "Kaynaklarda Yeni",
            "Platform rafları (Netflix, Prime…)",
            "Tür rafları (Aksiyon, Komedi…)",
            "Keşif rafları (Zar, Gizli Cevherler…)",
        )
        val checked = booleanArrayOf(
            personalShelfEnabled,
            freshShelfEnabled,
            platformShelvesEnabled,
            genreShelvesEnabled,
            discoveryShelvesEnabled,
        )
        val keys = arrayOf(KEY_PERSONAL, KEY_FRESH, KEY_PLATFORMS, KEY_GENRES, KEY_DISCOVERY)

        AlertDialog.Builder(context)
            .setTitle("Ana sayfa rafları")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                prefs(context).edit().putBoolean(keys[which], isChecked).apply()
            }
            .setPositiveButton("Tamam") { _, _ ->
                toast(context, "Raf değişikliği uygulamayı yeniden açınca görünür")
            }
            .setNegativeButton("Geri", null)
            .show()
    }

    private fun playbackMenu(context: Context) {
        val lines = arrayOf(
            "Altyazı: ${if (subtitlesEnabled) "açık" else "kapalı"}",
            "Altyazı dili: ${subtitleLanguages[subtitleLanguage]}",
            "Yeterli link sayısı: ${linkTarget}  (bulunca taramayı durdur)",
            "Türkçe dublajı öne al: ${if (preferTurkishDub) "açık" else "kapalı"}",
        )
        AlertDialog.Builder(context)
            .setTitle("Oynatma ve altyazı")
            .setItems(lines) { dialog, which ->
                when (which) {
                    0 -> {
                        val next = !subtitlesEnabled
                        prefs(context).edit().putBoolean(KEY_SUBTITLES, next).apply()
                        toast(context, "Altyazı ${if (next) "açık" else "kapalı"}")
                    }
                    1 -> {
                        val next = (subtitleLanguage + 1) % subtitleLanguages.size
                        prefs(context).edit().putInt(KEY_SUBTITLE_LANG, next).apply()
                        toast(context, subtitleLanguages[next])
                    }
                    2 -> {
                        val next = linkTargets[
                            (linkTargets.indexOf(linkTarget).coerceAtLeast(0) + 1) % linkTargets.size,
                        ]
                        prefs(context).edit().putInt(KEY_LINK_TARGET, next).apply()
                        toast(context, "Yeterli link: $next")
                    }
                    3 -> {
                        val next = !preferTurkishDub
                        prefs(context).edit().putBoolean(KEY_PREFER_DUB, next).apply()
                        toast(context, "Türkçe dublaj önceliği ${if (next) "açık" else "kapalı"}")
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Geri", null)
            .show()
    }

    private fun maintenanceMenu(context: Context) {
        val lines = arrayOf(
            "Öneri geçmişini temizle (${watchCount()} kayıt)",
            "Raf önbelleğini temizle",
            "Kaynak adres listesini yenile",
        )
        AlertDialog.Builder(context)
            .setTitle("Bakım")
            .setItems(lines) { dialog, which ->
                when (which) {
                    0 -> {
                        prefs(context).edit().remove(WATCH_HISTORY_KEY).apply()
                        toast(context, "Öneri geçmişi temizlendi")
                    }
                    1 -> {
                        cacheCleaner?.invoke()
                        toast(context, "Önbellek temizlendi")
                    }
                    2 -> {
                        domainRefresher?.invoke()
                        toast(context, "Adres listesi yenilenecek")
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Geri", null)
            .show()
    }

    private fun aboutDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("BerkStream")
            .setMessage(
                "Türkçe kaynakların tamamını tek eklentide toplar.\n\n" +
                    "• Raflar TMDB üzerinden, Türkçe başlıklarla\n" +
                    "• Kaynak adresleri haftalık taranıp güncellenir\n" +
                    "• Altyazı: OpenSubtitles\n" +
                    "• Oynatıcı menüsündeki \"bunu beğendim\" önerileri besler\n\n" +
                    "github.com/berkdemir18/BerkStream-Hub",
            )
            .setPositiveButton("Kapat", null)
            .show()
    }

    /** Saglayici tarafindan doldurulur; bakim menusu bunlari cagiriyor. */
    var cacheCleaner: (() -> Unit)? = null
    var domainRefresher: (() -> Unit)? = null

    private fun toast(context: Context, message: String) {
        runCatching { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
}
