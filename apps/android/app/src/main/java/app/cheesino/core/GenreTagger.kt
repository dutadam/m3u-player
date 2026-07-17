package app.cheesino.core

/**
 * Kanal/film adından veya kategori adından kanonik tür çıkarımı.
 * iOS GenreTagger ile aynı sözlük (TR/EN). Öneri motorunun tür sinyali buradan gelir.
 */
object GenreTagger {

    /** Kanonik tür etiketleri (kullanıcıya gösterilebilir). */
    val canonical = listOf(
        "Aksiyon", "Macera", "Komedi", "Dram", "Korku", "Gerilim",
        "Bilim Kurgu", "Fantastik", "Romantik", "Animasyon", "Belgesel",
        "Suç", "Aile", "Savaş", "Western", "Müzikal", "Spor"
    )

    // Her kanonik türe eşlenen anahtar kelimeler (küçük harf, TR+EN).
    private val map: Map<String, List<String>> = mapOf(
        "Aksiyon" to listOf("aksiyon", "action"),
        "Macera" to listOf("macera", "adventure"),
        "Komedi" to listOf("komedi", "comedy"),
        "Dram" to listOf("dram", "drama"),
        "Korku" to listOf("korku", "horror"),
        "Gerilim" to listOf("gerilim", "thriller", "suspense"),
        "Bilim Kurgu" to listOf("bilim kurgu", "bilimkurgu", "sci-fi", "scifi", "science fiction"),
        "Fantastik" to listOf("fantastik", "fantasy", "fantezi"),
        "Romantik" to listOf("romantik", "romance", "romantic", "aşk"),
        "Animasyon" to listOf("animasyon", "animation", "anime", "çizgi"),
        "Belgesel" to listOf("belgesel", "documentary", "docu"),
        "Suç" to listOf("suç", "crime", "mafya", "gangster"),
        "Aile" to listOf("aile", "family", "çocuk", "kids", "children"),
        "Savaş" to listOf("savaş", "war"),
        "Western" to listOf("western", "kovboy", "cowboy"),
        "Müzikal" to listOf("müzikal", "musical", "müzik", "music"),
        "Spor" to listOf("spor", "sport", "futbol", "football", "soccer", "nba", "maç")
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
