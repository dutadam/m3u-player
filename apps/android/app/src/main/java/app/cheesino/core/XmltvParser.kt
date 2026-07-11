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

    private val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    /** "20240711183000 +0000" → epoch saniye (tz offset MVP'de yok sayılır). */
    private fun parseTime(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        val core = s.trim().take(14)
        return try { (fmt.parse(core)?.time ?: 0L) / 1000 } catch (e: Exception) { 0L }
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
