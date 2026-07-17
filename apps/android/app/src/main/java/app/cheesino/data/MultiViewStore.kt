package app.cheesino.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Çoklu ekran düzeni + slotlara atanmış kanal id'leri — sonraki oturumda geri yüklenir. */
@Serializable
data class MultiViewConfig(
    val rows: Int = 2,
    val cols: Int = 1,
    val slotChannelIds: List<String?> = emptyList()
)

class MultiViewStore(context: Context) {
    private val prefs = context.getSharedPreferences("cheesino_multiview", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): MultiViewConfig = try {
        prefs.getString("cfg", null)?.let { json.decodeFromString<MultiViewConfig>(it) } ?: MultiViewConfig()
    } catch (e: Exception) { MultiViewConfig() }

    fun save(c: MultiViewConfig) { prefs.edit().putString("cfg", json.encodeToString(c)).apply() }
}
