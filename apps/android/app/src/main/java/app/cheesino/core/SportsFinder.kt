package app.cheesino.core

/**
 * Spor/maç merkezi MVP — EPG programlarından bugünün maçlarını çıkarır.
 * Çok sporlu: futbol, basketbol, tenis, voleybol, formula... anahtar kelime + "takım vs takım" sezgisi.
 */
object SportsFinder {

    private val sportWords = listOf(
        "spor", "sport", "futbol", "football", "soccer", "basketbol", "basketball", "nba",
        "maç", "match", "tenis", "tennis", "voleybol", "volley", "hokey", "hockey",
        "süper lig", "super lig", "premier", "la liga", "serie a", "bundesliga",
        "şampiyonlar", "champions", "uefa", "euro", "derbi", "formula", "moto", "rugby", "cricket"
    )

    /** Başlıkta "takım vs takım" izi mi var? */
    private fun looksLikeMatch(t: String): Boolean =
        t.contains(" vs ") || t.contains(" v ") || t.contains(" - ") || t.contains(" x ")

    data class Match(
        val channelId: String,
        val channelName: String,
        val channelLogo: String?,
        val title: String,
        val start: Long,
        val stop: Long
    ) {
        val isLiveNow: Boolean get() { val n = System.currentTimeMillis() / 1000; return start <= n && n < stop }
    }

    fun today(channels: List<Channel>, epg: Map<String, List<EpgEntry>>): List<Match> {
        val now = System.currentTimeMillis() / 1000
        val from = now - 2 * 3600       // biten yakın maçlar da görünsün
        val to = now + 22 * 3600        // önümüzdeki ~1 gün
        val out = ArrayList<Match>()
        for (ch in channels) {
            val list = ch.tvgId?.let { epg[it] } ?: continue
            val sportChannel = sportWords.any { ch.group.lowercase().contains(it) || ch.name.lowercase().contains(it) }
            for (e in list) {
                if (e.stop < from || e.start > to) continue
                val t = e.title.lowercase()
                val isSport = sportChannel || sportWords.any { t.contains(it) }
                if (isSport && (looksLikeMatch(t) || sportChannel)) {
                    out.add(Match(ch.id, ch.name, ch.logo, e.title, e.start, e.stop))
                }
            }
        }
        return out.sortedBy { it.start }.take(200)
    }
}
