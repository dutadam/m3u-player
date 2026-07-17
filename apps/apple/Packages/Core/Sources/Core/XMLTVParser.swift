import Foundation

/// XMLTV (EPG) ayrıştırıcı. Spec: docs/spec/xtream-m3u-epg.md §4.
/// PWA referansı: `parseXMLTV()` (index.html:851), `parseXMLTVDate()` (:843).
/// Foundation `XMLParser` (SAX) — büyük dosyalarda bellek-dostu akış ayrıştırma.
public final class XMLTVParser: NSObject, XMLParserDelegate {

    private var entries: [EpgEntry] = []
    private var curChannel: String?
    private var curStart: Date?
    private var curStop: Date?
    private var curTitle: String = ""
    private var curDesc: String = ""
    private var capturing: String?   // "title" | "desc" | nil

    public override init() { super.init() }

    /// XMLTV metnini ayrıştırıp EPG kayıtlarını döndürür.
    public static func parse(_ data: Data) -> [EpgEntry] {
        let p = XMLTVParser()
        let parser = XMLParser(data: data)
        parser.delegate = p
        parser.parse()
        return p.entries
    }

    /// XMLTV tarih formatı: `YYYYMMDDHHMMSS ±ZZZZ` (saat dilimi opsiyonel).
    public static func parseDate(_ raw: String) -> Date? {
        let s = raw.trimmingCharacters(in: .whitespaces)
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "en_US_POSIX")
        // Saat dilimli
        if s.contains(" ") {
            fmt.dateFormat = "yyyyMMddHHmmss Z"
            if let d = fmt.date(from: s) { return d }
        }
        // Saat dilimsiz → yerel kabul et
        let core = String(s.prefix(14))
        fmt.dateFormat = "yyyyMMddHHmmss"
        fmt.timeZone = TimeZone.current
        return fmt.date(from: core)
    }

    // MARK: XMLParserDelegate

    public func parser(_ parser: XMLParser, didStartElement el: String, namespaceURI: String?,
                       qualifiedName: String?, attributes attr: [String: String]) {
        switch el {
        case "programme":
            curChannel = attr["channel"]
            curStart = attr["start"].flatMap { Self.parseDate($0) }
            curStop = attr["stop"].flatMap { Self.parseDate($0) }
            curTitle = ""; curDesc = ""
        case "title": capturing = "title"
        case "desc": capturing = "desc"
        default: break
        }
    }

    public func parser(_ parser: XMLParser, foundCharacters string: String) {
        switch capturing {
        case "title": curTitle += string
        case "desc": curDesc += string
        default: break
        }
    }

    public func parser(_ parser: XMLParser, didEndElement el: String, namespaceURI: String?,
                       qualifiedName: String?) {
        switch el {
        case "title", "desc": capturing = nil
        case "programme":
            if let ch = curChannel, let start = curStart, let stop = curStop {
                let title = curTitle.trimmingCharacters(in: .whitespacesAndNewlines)
                let desc = curDesc.trimmingCharacters(in: .whitespacesAndNewlines)
                entries.append(EpgEntry(channelId: ch, start: start, stop: stop,
                                        title: title.isEmpty ? "—" : title,
                                        desc: desc.isEmpty ? nil : desc))
            }
            curChannel = nil; curStart = nil; curStop = nil
        default: break
        }
    }
}

/// EPG kayıtlarını kanallarla eşleyen indeks. Spec §4: tvg-id birebir → normalize-isim fuzzy fallback.
public struct EPGIndex: Sendable {
    private var byChannelId: [String: [EpgEntry]]

    public init(entries: [EpgEntry]) {
        var dict: [String: [EpgEntry]] = [:]
        for e in entries { dict[e.channelId, default: []].append(e) }
        for k in dict.keys { dict[k]?.sort { $0.start < $1.start } }
        self.byChannelId = dict
    }

    /// Kanalın şu an oynayan programı.
    public func nowPlaying(for channel: Channel) -> EpgEntry? {
        entries(for: channel).first { $0.isLiveNow }
    }

    /// Kanalın sıradaki programı.
    public func upNext(for channel: Channel) -> EpgEntry? {
        let now = Date()
        return entries(for: channel).first { $0.start > now }
    }

    public func entries(for channel: Channel) -> [EpgEntry] {
        if let id = channel.tvgId, let hit = byChannelId[id] { return hit }
        // Fuzzy fallback: normalize edilmiş isim eşleşmesi
        let key = Self.normalizeName(channel.name)
        for (cid, list) in byChannelId where Self.normalizeName(cid) == key { return list }
        return []
    }

    /// İsim normalizasyonu: küçük harf, boşluk/işaret ve HD/FHD/4K ekleri atılır.
    static func normalizeName(_ s: String) -> String {
        var t = s.lowercased()
        for token in ["fhd", "uhd", "4k", "hd", "sd"] { t = t.replacingOccurrences(of: token, with: "") }
        return t.components(separatedBy: CharacterSet.alphanumerics.inverted).joined()
    }
}
