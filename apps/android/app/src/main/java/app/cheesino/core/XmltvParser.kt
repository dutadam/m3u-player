package app.cheesino.core

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * XMLTV → kanal (tvg-id) başına program listesi.
 * Android XmlPullParser ile akış tabanlı; büyük feed'lerde belleği şişirmez.
 */
object XmltvParser {

    private val fmtUtc = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val fmtTz = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)

    /**
     * "20240711183000 +0300" → epoch saniye. TZ offset'i DİKKATE ALINIR — aksi halde
     * "şimdi oynuyor"/CANLI penceresi offset kadar (TR'de 3 saat) kayar.
     */
    private fun parseTime(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        val t = s.trim()
        val core = t.take(14)
        val rest = t.drop(14).trim()   // "+0300" / "-0500" olabilir
        return try {
            if (rest.length >= 5 && (rest[0] == '+' || rest[0] == '-'))
                (fmtTz.parse("$core ${rest.take(5)}")?.time ?: 0L) / 1000
            else
                (fmtUtc.parse(core)?.time ?: 0L) / 1000
        } catch (e: Exception) {
            try { (fmtUtc.parse(core)?.time ?: 0L) / 1000 } catch (e2: Exception) { 0L }
        }
    }

    fun parse(xml: String): Map<String, List<EpgEntry>> {
        val out = HashMap<String, MutableList<EpgEntry>>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var channel: String? = null
        var start = 0L
        var stop = 0L
        var inProgramme = false
        var title: StringBuilder? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        channel = parser.getAttributeValue(null, "channel")
                        start = parseTime(parser.getAttributeValue(null, "start"))
                        stop = parseTime(parser.getAttributeValue(null, "stop"))
                        inProgramme = true
                        title = null
                    }
                    "title" -> if (inProgramme && title == null) title = StringBuilder()
                }
                XmlPullParser.TEXT -> title?.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "programme") {
                    val ch = channel; val t = title?.toString()?.trim()
                    if (ch != null && !t.isNullOrBlank())
                        out.getOrPut(ch) { ArrayList() }.add(EpgEntry(ch, start, stop, t))
                    inProgramme = false; channel = null; title = null
                }
            }
            event = parser.next()
        }
        out.values.forEach { it.sortBy { e -> e.start } }
        return out
    }
}
