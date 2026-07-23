package app.cheesino.data

import app.cheesino.core.Channel
import app.cheesino.core.GenreTagger
import app.cheesino.core.MediaKind
import kotlin.math.abs
import kotlin.math.pow

/**
 * Öneri motoru — iOS LibraryStore mantığının Kotlin portu:
 * tür afinitesi (zaman-decay + beğeni) → ağırlıklı skor → çeşitlilik → günlük jitter.
 * Saf fonksiyonlar; UI state + user verisi girer, sıralı liste çıkar.
 */
object Recommender {

    /** Tür → afinite. İzleme geçmişi zaman-decay'li, beğeniler bonuslu. */
    fun genreAffinity(user: UserData, channels: List<Channel>): Map<String, Double> {
        val now = System.currentTimeMillis()
        val halfLife = 14.0 * 24 * 3600_000     // 14 gün yarı ömür
        val aff = HashMap<String, Double>()
        for (ev in user.history) {
            val age = (now - ev.at).coerceAtLeast(0).toDouble()
            val decay = 0.5.pow(age / halfLife)
            for (g in ev.genres) aff[g] = (aff[g] ?: 0.0) + decay
        }
        val byId = channels.associateBy { it.id }
        for (id in user.likes) byId[id]?.let { ch ->
            for (g in GenreTagger.tags(ch.name, ch.group)) aff[g] = (aff[g] ?: 0.0) + 2.0
        }
        for (id in user.dislikes) byId[id]?.let { ch ->
            for (g in GenreTagger.tags(ch.name, ch.group)) aff[g] = (aff[g] ?: 0.0) - 1.5
        }
        return aff
    }

    /** "For You" — ağırlıklı skor + tür çeşitliliği + günlük stabil jitter. */
    fun recommended(channels: List<Channel>, user: UserData, limit: Int = 30): List<Channel> {
        val movies = channels.filter { it.kind == MediaKind.VOD && it.logo != null }
        if (movies.isEmpty()) return emptyList()
        val aff = genreAffinity(user, channels)
        val seen = user.history.mapTo(HashSet()) { it.id }
        val daySeed = System.currentTimeMillis() / 86_400_000L
        fun jitter(id: String): Double {
            var h = 1125899906842597L
            for (c in id) h = 31 * h + c.code
            return (abs(h xor daySeed) % 1000) / 1000.0
        }

        val scored = movies.asSequence()
            .filter { it.id !in user.dislikes }
            .map { ch ->
                val genres = GenreTagger.tags(ch.name, ch.group)
                val gScore = genres.sumOf { aff[it] ?: 0.0 }
                val rating = (ch.rating ?: 0.0) / 10.0
                val watched = if (ch.id in seen) -1.0 else 0.0
                ch to (gScore * 1.5 + rating * 0.6 + jitter(ch.id) * 0.5 + watched)
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .toList()

        // Çeşitlilik: aynı baskın türden en fazla 6 art arda birikmesin.
        val out = ArrayList<Channel>()
        val perGenre = HashMap<String, Int>()
        for (ch in scored) {
            val g = GenreTagger.primary(ch.name, ch.group) ?: "?"
            val c = perGenre[g] ?: 0
            if (c >= 6) continue
            out.add(ch); perGenre[g] = c + 1
            if (out.size >= limit) break
        }
        return out
    }

    /**
     * "Devam Et" — bitmemiş işaretler, en son izlenene göre.
     * Bir dizinin bölümleri TEK karta indirgenir (en son bölüm), böylece her bölüm ayrı görünmez.
     * Anahtar: seriesId → seriesName → başlık öneki ("Dizi — Bölüm" formatı) → id.
     */
    fun continueWatching(user: UserData): List<ResumeMark> {
        val marks = user.resume.values.filter { !it.finished }.sortedByDescending { it.updatedAt }
        val seen = HashSet<String>()
        return marks.filter { m ->
            val key = when {
                m.isSeries && m.seriesId != null -> "s${m.seriesId}"
                m.isSeries && !m.seriesName.isNullOrBlank() -> "sn:${m.seriesName.lowercase()}"
                m.title.contains(" — ") -> "t:${m.title.substringBefore(" — ").trim().lowercase()}"
                else -> "id:${m.id}"
            }
            seen.add(key)
        }
    }

    fun favorites(channels: List<Channel>, user: UserData): List<Channel> =
        channels.filter { it.id in user.favorites }
}
