package app.cheesino.data

import app.cheesino.core.Channel
import app.cheesino.core.GenreTagger
import app.cheesino.core.SeriesRef
import kotlin.math.abs

/**
 * Dinamik keşif rayları — tür + senaryo/alt-tür + küratörlü raylar.
 * Performans: tür etiketleri ve skorlar öğe başına BİR kez hesaplanır (ana thread'i kilitlemez).
 * ViewModel arka planda çağırır; her gün jitter değişir, beğeni afinitesine göre ağırlıklı.
 */
object RailEngine {

    val daySeed: Long get() = System.currentTimeMillis() / 86_400_000L

    private fun jitter(key: String, salt: Long): Double {
        var h = 1125899906842597L
        for (c in key) h = 31 * h + c.code
        return (abs(h xor salt) % 1000) / 1000.0
    }

    private val scenarios = listOf(
        "Paranormal & Doğaüstü" to listOf("hayalet", "ghost", "ruh", "şeytan", "demon", "exorcist", "paranormal", "lanet", "cadı", "witch", "zombi", "vampir", "doğaüstü", "conjuring", "insidious"),
        "Uzay & Ötesi" to listOf("uzay", "space", "mars", "galaks", "alien", "uzaylı", "star wars", "yıldız savaş", "gezegen", "planet", "interstellar", "kozmik"),
        "Suç & Mafya" to listOf("mafya", "mafia", "gangster", "çete", "cartel", "kartel", "cinayet", "dedektif", "detective", "katil", "heist", "soygun", "godfather", "narcos"),
        "Süper Kahramanlar" to listOf("marvel", "batman", "superman", "spider", "avenger", "x-men", "mutant", "süper", "thor", "hulk", "joker", "aquaman", "venom"),
        "Savaş Cephesi" to listOf("savaş", "war", "asker", "soldier", "komando", "sniper", "dunkirk", "normandiya"),
        "Gerçek Hikâyeler" to listOf("gerçek", "true story", "biyografi", "biography", "based on", "hayatı"),
        "Korku Gecesi" to listOf("korku", "horror", "scream", "halloween", "annabelle", "dehşet")
    )

    private class MTag(val ch: Channel, val tags: Set<String>, val nameLower: String, val score: Double)
    private data class Scored(val title: String, val items: List<Channel>, val score: Double)

    /** Ana sayfa/Filmler için dinamik film rayları. Ağır iş öğe başına bir kez. */
    fun movieRails(movies: List<Channel>, allChannels: List<Channel>, user: UserData, limit: Int = 10): List<Pair<String, List<Channel>>> {
        val affinity = Recommender.genreAffinity(user, allChannels)
        val seed = daySeed
        val tagged = movies.mapNotNull { ch ->
            if (ch.logo == null) return@mapNotNull null
            val tags = GenreTagger.tags(ch.name, ch.group)
            val score = (ch.rating ?: 0.0) / 10.0 * 0.5 + tags.sumOf { affinity[it] ?: 0.0 } + jitter(ch.id, seed) * 0.8
            MTag(ch, tags, ch.name.lowercase(), score)
        }
        if (tagged.isEmpty()) return emptyList()

        val rails = ArrayList<Scored>()
        for (g in GenreTagger.canonical) {
            val items = tagged.filter { g in it.tags }
            if (items.size >= 6)
                rails.add(Scored(g, items.sortedByDescending { it.score }.take(30).map { it.ch }, (affinity[g] ?: 0.0) * 1.5 + jitter(g, seed)))
        }
        for ((title, keys) in scenarios) {
            val items = tagged.filter { m -> keys.any { m.nameLower.contains(it) } }
            if (items.size >= 5)
                rails.add(Scored(title, items.sortedByDescending { it.score }.take(30).map { it.ch }, 0.6 + jitter(title, seed)))
        }
        val kult = tagged.filter { (it.ch.rating ?: 0.0) >= 8.2 }
        if (kult.size >= 6)
            rails.add(Scored("Kült & Efsane", kult.sortedByDescending { it.score }.take(30).map { it.ch }, 0.9 + jitter("kult", seed)))

        return rails.sortedByDescending { it.score }.take(limit).map { it.title to it.items }
    }

    /** Diziler için dinamik raylar. */
    fun seriesRails(series: List<SeriesRef>, user: UserData, limit: Int = 10): List<Pair<String, List<SeriesRef>>> {
        val seed = daySeed
        data class STag(val s: SeriesRef, val tags: Set<String>, val nameLower: String, val jit: Double)
        val tagged = series.mapNotNull { s ->
            if (s.cover == null) return@mapNotNull null
            STag(s, GenreTagger.tags(s.name, s.genre, s.group), s.name.lowercase(), jitter(s.name, seed))
        }
        if (tagged.isEmpty()) return emptyList()

        val rails = ArrayList<Pair<Pair<String, List<SeriesRef>>, Double>>()
        for (g in GenreTagger.canonical) {
            val items = tagged.filter { g in it.tags }
            if (items.size >= 5)
                rails.add((g to items.sortedByDescending { it.jit }.take(30).map { it.s }) to jitter(g, seed))
        }
        for ((title, keys) in scenarios) {
            val items = tagged.filter { m -> keys.any { m.nameLower.contains(it) } }
            if (items.size >= 5)
                rails.add((title to items.sortedByDescending { it.jit }.take(30).map { it.s }) to (0.5 + jitter(title, seed)))
        }
        return rails.sortedByDescending { it.second }.take(limit).map { it.first }
    }
}
