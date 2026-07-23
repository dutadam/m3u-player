package app.cheesino.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Yakalanmayan çökmeleri dosyaya yazar → kullanıcı Ayarlar'dan kopyalayıp paylaşabilir (logcat
 * gerekmeden hata teşhisi). Varsayılan işleyiciyi zincirler; normal çökme davranışı korunur.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                File(app.filesDir, FILE).writeText(
                    buildString {
                        append("cheesino çökme kaydı\n")
                        append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                        append("\nThread: ${t.name}\n\n")
                        append(sw.toString())
                    }
                )
            }
            prev?.uncaughtException(t, e)
        }
    }

    fun last(context: Context): String? = runCatching {
        File(context.applicationContext.filesDir, FILE).takeIf { it.exists() }?.readText()
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE).delete() }
    }
}
