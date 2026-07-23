package app.cheesino.core

/**
 * Kanal/film adından veya kategori adından kanonik tür çıkarımı.
 * iOS GenreTagger ile aynı sözlük (TR/EN). Öneri motorunun tür sinyali buradan gelir.
 */
object GenreTagger {

    /** Kanonik tür etiketleri (kullanıcıya gösterilebilir). */
    val canonical = listOf(
        "Action", "Adventure", "Comedy", "Drama", "Horror", "Thriller",
        "Sci-Fi", "Fantasy", "Romance", "Animation", "Documentary",
        "Crime", "Family", "War", "Western", "Musical", "Sports"
    )

    // Her kanonik türe eşlenen anahtar kelimeler (küçük harf, TR+EN).
    private val map: Map<String, List<String>> = mapOf(
        "Action" to listOf("aksiyon", "action"),
        "Adventure" to listOf("macera", "adventure"),
        "Comedy" to listOf("komedi", "comedy"),
        "Drama" to listOf("dram", "drama"),
        "Horror" to listOf("korku", "horror"),
        "Thriller" to listOf("gerilim", "thriller", "suspense"),
        "Sci-Fi" to listOf("bilim kurgu", "bilimkurgu", "sci-fi", "scifi", "science fiction"),
        "Fantasy" to listOf("fantastik", "fantasy", "fantezi"),
        "Romance" to listOf("romantik", "romance", "romantic", "aşk"),
        "Animation" to listOf("animasyon", "animation", "anime", "çizgi"),
        "Documentary" to listOf("belgesel", "documentary", "docu"),
        "Crime" to listOf("suç", "crime", "mafya", "gangster"),
        "Family" to listOf("aile", "family", "çocuk", "kids", "children"),
        "War" to listOf("savaş", "war"),
        "Western" to listOf("western", "kovboy", "cowboy"),
        "Musical" to listOf("müzikal", "musical", "müzik", "music"),
        "Sports" to listOf("spor", "sport", "futbol", "football", "soccer", "nba", "maç")
    )

    /** Ham metinden (ad + kategori) kanonik tür seti. Eşleşme yoksa boş. */
    fun tags(vararg raw: String?): Set<String> {
        val hay = raw.filterNotNull().joinToString(" ").lowercase()
        if (hay.isBlank()) return emptySet()
        val out = LinkedHashSet<String>()
        for ((genre, keys) in map) {
            if (keys.any { hay.contains(it) }) out.add(genre)
        }
        return out
    }

    /** Tek baskın tür (ilk eşleşme) — kategori üretimi için. */
    fun primary(vararg raw: String?): String? = tags(*raw).firstOrNull()
}
