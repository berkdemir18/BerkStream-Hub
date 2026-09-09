package com.berkstream

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast

internal const val WATCH_HISTORY_KEY = "berkstream_watch_history"

/**
 * Eklentinin kendi ayarlari ve kalici deposu.
 *
 * CloudStream'in kendi izleme gecmisine yalnizca yansimayla erisilebiliyor ve
 * bu her surumde tutmuyor; bu yuzden kendi kaydimizi da burada tutuyoruz.
 * Depo, eklenti yuklenirken [BerkStreamPlugin] tarafindan veriliyor.
 */
object BerkStreamSettings {
    @Volatile
    var store: SharedPreferences? = null
        private set

    private const val KEY_TIMEOUT = "scan_timeout_seconds"
    private const val KEY_SUBTITLES = "subtitles_enabled"
    private const val KEY_FRESH = "fresh_shelf_enabled"

    private val timeoutOptions = listOf(5L, 9L, 15L)

    fun attach(context: Context) {
        store = context.getSharedPreferences("berkstream", Context.MODE_PRIVATE)
    }

    /** Tek bir kaynagin taranmasi icin ayrilan sure. */
    val scanTimeoutSeconds: Long
        get() = store?.getLong(KEY_TIMEOUT, 9L) ?: 9L

    val subtitlesEnabled: Boolean
        get() = store?.getBoolean(KEY_SUBTITLES, true) ?: true

    val freshShelfEnabled: Boolean
        get() = store?.getBoolean(KEY_FRESH, true) ?: true

    private fun watchCount(): Int =
        store?.getString(WATCH_HISTORY_KEY, "").orEmpty()
            .split('\n').count { it.isNotBlank() }

    /**
     * Ayar ekrani. Kendi arayuz kaynagimiz olmadigi icin sistemin hazir
     * dialoglari kullaniliyor; boylece paket kaynak dosyasi tasimak zorunda
     * kalmiyor.
     */
    fun open(context: Context) {
        val preferences = store ?: context.getSharedPreferences("berkstream", Context.MODE_PRIVATE)
            .also { store = it }

        val entries = arrayOf(
            "Kaynak tarama süresi: ${scanTimeoutSeconds}sn",
            "Altyazı (OpenSubtitles): ${if (subtitlesEnabled) "açık" else "kapalı"}",
            "\"Kaynaklarda Yeni\" rafı: ${if (freshShelfEnabled) "açık" else "kapalı"}",
            "İzleme geçmişini temizle (${watchCount()} kayıt)",
        )

        AlertDialog.Builder(context)
            .setTitle("BerkStream ayarları")
            .setItems(entries) { dialog, which ->
                when (which) {
                    0 -> {
                        val next = timeoutOptions[
                            (timeoutOptions.indexOf(scanTimeoutSeconds).coerceAtLeast(0) + 1) %
                                timeoutOptions.size,
                        ]
                        preferences.edit().putLong(KEY_TIMEOUT, next).apply()
                        toast(context, "Tarama süresi: ${next}sn")
                    }
                    1 -> {
                        val next = !subtitlesEnabled
                        preferences.edit().putBoolean(KEY_SUBTITLES, next).apply()
                        toast(context, "Altyazı ${if (next) "açık" else "kapalı"}")
                    }
                    2 -> {
                        val next = !freshShelfEnabled
                        preferences.edit().putBoolean(KEY_FRESH, next).apply()
                        toast(context, "Kaynaklarda Yeni ${if (next) "açık" else "kapalı"}")
                    }
                    3 -> {
                        preferences.edit().remove(WATCH_HISTORY_KEY).apply()
                        toast(context, "İzleme geçmişi temizlendi")
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun toast(context: Context, message: String) {
        runCatching { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
}
