package app.cheesino.core

/** Yetişkin içerik tespiti (parental kilidi için). iOS isAdultGroup karşılığı. */
object AdultFilter {
    private val keys = listOf(
        "xxx", "adult", "+18", "18+", "erotik", "erotic", "porn", "yetişkin", "for adults"
    )
    fun isAdult(vararg raw: String?): Boolean {
        val h = raw.filterNotNull().joinToString(" ").lowercase()
        return keys.any { h.contains(it) }
    }
}
