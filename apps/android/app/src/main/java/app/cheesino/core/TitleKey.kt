package app.cheesino.core

private val QUALITY_TOKENS = Regex(
    "\\b(4K|UHD|FHD|FULL ?HD|HD|SD|HEVC|H\\.?265|H\\.?264|X265|X264|2160P|1080P|720P|480P|HDR|DOLBY|DUAL|MULTI|TR|EN)\\b",
    RegexOption.IGNORE_CASE
)

/**
 * İçerik başlığını kalite/codec/dil etiketlerinden arındırıp normalize eder. Aynı filmin farklı
 * kategori/varyantlarını (ör. "Film 4K", "Film [TR]") tek anahtarda toplar → global tekilleştirme.
 */
fun baseTitle(name: String): String =
    name.replace(QUALITY_TOKENS, " ")
        .replace(Regex("[\\[\\](){}|]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()

private val TRAILING_YEAR = Regex("\\b(19|20)\\d{2}\\b")

// Aynı filmin dil/versiyon varyantları — ray tekilleştirmede "Film Dublaj" ↔ "Film Altyazılı" birleşir.
private val VARIANT_TOKENS = Regex(
    "\\b(DUBLAJ(LI)?|ALT ?YAZILI|ALTYAZI|T[ÜU]RK[ÇC]E|ORI?[İI]?J?[İI]?NAL|DUB|SUB|SUBBED|DUBBED|VOSTFR|YERL[İI])\\b",
    RegexOption.IGNORE_CASE
)

/**
 * Vitrin/ray tekilleştirme anahtarı — [baseTitle]'a ek olarak yıl ve dil/versiyon etiketini de atar
 * ("Film 2023 Dublaj" ↔ "Film"). YALNIZ görüntü rayları için; global katalog dedup'unda kullanma
 * (yoksa "Blade Runner" ile "Blade Runner 2049" birleşip biri gizlenir).
 */
fun railKey(name: String): String =
    baseTitle(name).replace(TRAILING_YEAR, " ").replace(VARIANT_TOKENS, " ")
        .replace(Regex("\\s+"), " ").trim()
