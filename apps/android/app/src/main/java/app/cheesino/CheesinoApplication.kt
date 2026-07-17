package app.cheesino

import android.app.Application
import app.cheesino.data.Reminders

class CheesinoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Hatırlatıcı bildirim kanalını hazırla.
        Reminders.ensureChannel(this)
    }
}
