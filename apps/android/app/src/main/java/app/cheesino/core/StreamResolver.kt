package app.cheesino.core

// Oynatma kaynağı çözümleme. Android'de Media3 HLS/MP4/TS oynatır; MKV bazıları için
// ileride libVLC eklenebilir. Şimdilik tek aday (orijinal URL) + .m3u8 için .ts fallback.
object StreamResolver {
    data class Candidate(val url: String)

    fun candidates(original: String): List<Candidate> {
        val out = ArrayList<Candidate>()
        out.add(Candidate(original))
        if (original.substringAfterLast('.', "").lowercase() == "m3u8") {
            out.add(Candidate(original.substringBeforeLast('.') + ".ts"))
        }
        return out
    }
}
