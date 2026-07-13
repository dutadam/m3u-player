package app.cheesino.data

import app.cheesino.core.Channel
import app.cheesino.core.GenreTagger
import app.cheesino.core.SeriesRef
import kotlin.math.abs

/**
 * Dinamik keşif rayları — tür + senaryo/alt-tür + küratörlü raylar.
 * Her açılış/gün farklı sıralanır (günlük jitter), beğeni afinitesine göre ağırlıklı.
 * Alfabetik değil; canlı ve kişisel bir görünüm.
 */
object RailEngine {

    val daySeed: Long get() = System.currentTimeMillis() / 86_400_000L

    private fun jitter(key: String, salt: Long): Double {
        var h = 1125899906842597L
        for (c in key) h = 31 * h + c.code
        return (abs(h xor salt) % 1000) / 1000.0
    }

    /** Rastgele ama rating + beğeni-ağırlıklı sıralama; alfabetik değil, günlük değişir. */
    fun dynamicSort(items: List<Channel>, affinity: Map<String, Double>, salt: Long = daySeed): List<Channel> =
        items.sortedByDescending { ch ->
            val a = GenreTagger.tags(ch.name, ch.group).sumOf { affinity[it] ?: 0.0 }
            (ch.rating ?: 0.0) / 10.0 * 0.5 + a * 1.0 + jitter(ch.id, salt) * 0.8
        }

    // Senaryo/alt-tür rayları — isimden anahtar kelime eşleşmesi.
    private val scenarios = listOf(
        "Paranormal & Doğaüstü" to listOf("hayalet", "ghost", "ruh", "şeytan", "demon", "exorcist", "paranormal", "lanet", "cadı", "witch", "zombi", "vampir", "doğaüstü", "conjuring", "insidious"),
        "Uzay & Ötesi" to listOf("uzay", "space", "mars", "galaks", "alien", "uzaylı", "star wars", "yıldız savaş", "gezegen", "planet", "interstellar", "kozmik"),
        "Suç & Mafya" to listOf("mafya", "mafia", "gangster", "çete", "cartel", "kartel", "cinayet", "dedektif", "detective", "katil", "heist", "soygun", "godfather", "narcos"),
        "Süper Kahramanlar" to listOf("marvel", "batman", "superman", "spider", "avenger", "x-men", "mutant", "süper", "thor", "hulk", "joker", "aquaman", "venom"),
        "Savaş Cephesi" to listOf("savaş", "war", "asker", "soldier", "komando", "sniper", "dunkirk", "normandiya"),
        "Gerçek Hikâyeler" to listOf("gerçek", "true story", "biyografi", "biography", "based on", "hayatı"),
        "Korku Gecesi" to listOf("korku", "horror", "scream", "halloween", "annabelle", "dehşet")
    )

    private data class Scored(val title: String, val items: List<Channel>, val score: Double)

    /** Ana sayfa/Filmler için dinamik film rayları (tür + senaryo + kült), beğeniye göre sıralı. */
    fun movieRails(movies: List<Channel>, allChannels: List<Channel>, user: UserData, limit: Int = 10): List<Pair<String, List<Channel>>> {
        val affinity = Recommender.genreAffinity(user, allChannels)
        val withLogo = movies.filter { it.logo != null }
        val rails = ArrayList<Scored>()

        for (g in GenreTagger.canonical) {
            val items = withLogo.filter { GenreTagger.tags(it.name, it.group).contains(g) }
            if (items.size >= 6)
                rails.add(Scored(g, dynamicSort(items, affinity).take(30), (affinity[g] ?: 0.0) * 1.5 + jitter(g, daySeed)))
        }
        for ((title, keys) in scenarios) {
            val items = withLogo.filter { m -> val n = m.name.lowercase(); keys.any { n.contains(it) } }
            if (items.size >= 5)
                rails.add(Scored(title, dynamicSort(items, affinity).take(30), 0.6 + jitter(title, daySeed)))
        }
        val kult = withLogo.filter { (it.rating ?: 0.0) >= 8.2 }
        if (kult.size >= 6)
            rails.add(Scored("Kült & Efsane", dynamicSort(kult, affinity).take(30), 0.9 + jitter("kult", daySeed)))

        return rails.sortedByDescending { it.score }.take(limit).map { it.title to it.items }
    }

    /** Diziler için dinamik raylar (tür + senaryo). */
    fun seriesRails(series: List<SeriesRef>, user: UserData, limit: Int = 10): List<Pair<String, List<SeriesRef>>> {
        val withCover = series.filter { it.cover != null }
        val out = ArrayList<Pair<Pair<String, List<SeriesRef>>, Double>>()

        for (g in GenreTagger.canonical) {
            val items = withCover.filter { GenreTagger.tags(it.name, it.genre, it.group).contains(g) }
            if (items.size >= 5)
                out.add((g to items.shuffledStable(jitter(g, daySeed)).take(30)) to jitter(g, daySeed))
        }
        for ((title, keys) in scenarios) {
            val items = withCover.filter { s -> val n = s.name.lowercase(); keys.any { n.contains(it) } }
            if (items.size >= 5)
                out.add((title to items.shuffledStable(jitter(title, daySeed)).take(30)) to (0.5 + jitter(title, daySeed)))
        }
        return out.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    /** Deterministik "rastgele" sıralama (günlük seed) — alfabetik kırar. */
    private fun <T> List<T>.shuffledStable(seed: Double): List<T> =
        sortedBy { ((it.hashCode() * 2654435761u.toLong()) xor (seed * 1e6).toLong()) }
}
