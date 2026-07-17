package app.cheesino.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Bir program başlamadan önce hatırlatma. */
@Serializable
data class Reminder(
    val channelId: String,
    val channelName: String,
    val url: String,
    val logo: String? = null,
    val title: String,
    val startSec: Long
) {
    val key: Int get() = "$channelId#$startSec".hashCode()
}

class ReminderStore(context: Context) {
    private val prefs = context.getSharedPreferences("cheesino_reminders", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): List<Reminder> = try {
        prefs.getString("list", null)?.let { json.decodeFromString<List<Reminder>>(it) } ?: emptyList()
    } catch (e: Exception) { emptyList() }

    fun save(list: List<Reminder>) {
        try { prefs.edit().putString("list", json.encodeToString(list)).apply() } catch (e: Exception) { }
    }
}

/** Program hatırlatıcıları — AlarmManager ile zamanla, tetiklendiğinde bildirim göster. */
object Reminders {
    private const val CHANNEL_ID = "reminders"
    const val EXTRA = "reminder_json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Program hatırlatıcıları", NotificationManager.IMPORTANCE_HIGH)
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    fun isSet(ctx: Context, channelId: String, startSec: Long): Boolean {
        val k = "$channelId#$startSec".hashCode()
        return ReminderStore(ctx).load().any { it.key == k }
    }

    fun add(ctx: Context, r: Reminder) {
        val store = ReminderStore(ctx)
        store.save(store.load().filter { it.key != r.key } + r)
        schedule(ctx, r)
    }

    fun remove(ctx: Context, r: Reminder) {
        val store = ReminderStore(ctx)
        store.save(store.load().filter { it.key != r.key })
        alarm(ctx).cancel(pending(ctx, r))
    }

    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis() / 1000
        val valid = ReminderStore(ctx).load().filter { it.startSec > now }
        ReminderStore(ctx).save(valid)
        valid.forEach { schedule(ctx, it) }
    }

    fun fire(ctx: Context, r: Reminder) {
        ensureChannel(ctx)
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
        val pi = open?.let {
            PendingIntent.getActivity(ctx, r.key, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("${r.channelName} · şimdi başlıyor")
            .setContentText(r.title)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply { pi?.let { setContentIntent(it) } }
            .build()
        try { NotificationManagerCompat.from(ctx).notify(r.key, n) } catch (e: SecurityException) { }
        // Tek seferlik — ateşledikten sonra listeden düş.
        val store = ReminderStore(ctx)
        store.save(store.load().filter { it.key != r.key })
    }

    private fun alarm(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pending(ctx: Context, r: Reminder): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).putExtra(EXTRA, json.encodeToString(r))
        return PendingIntent.getBroadcast(ctx, r.key, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun schedule(ctx: Context, r: Reminder) {
        val triggerAt = (r.startSec - 120) * 1000L   // 2 dk önce
        if (triggerAt < System.currentTimeMillis()) return
        val am = alarm(ctx)
        try {
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending(ctx, r))
            else am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 60_000L, pending(ctx, r))
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pending(ctx, r))
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val js = intent.getStringExtra(Reminders.EXTRA) ?: return
        val r = try { Json { ignoreUnknownKeys = true }.decodeFromString<Reminder>(js) } catch (e: Exception) { return }
        Reminders.fire(context, r)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.rescheduleAll(context)
    }
}
