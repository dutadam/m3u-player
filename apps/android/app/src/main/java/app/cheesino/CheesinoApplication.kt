package app.cheesino

import android.app.Application
import app.cheesino.data.CrashLog
import app.cheesino.data.Reminders

class CheesinoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Çökmeleri dosyaya yaz (Ayarlar'dan paylaşılabilir tanılama).
        CrashLog.install(this)
        // Hatırlatıcı bildirim kanalını hazırla.
        Reminders.ensureChannel(this)
    }
}
