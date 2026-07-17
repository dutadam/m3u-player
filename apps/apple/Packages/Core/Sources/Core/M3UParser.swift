import Foundation

/// M3U / M3U8 ayrıştırıcı. Spec: docs/spec/xtream-m3u-epg.md §2.
/// PWA referansı: `parseM3U()` (index.html:724).
public enum M3UParser {

    /// Ayrıştırma sonucu: kanallar + (varsa) playlist-seviyesi EPG kaynak URL'i (`url-tvg` vb.).
    public struct Result: Sendable {
        public var channels: [Channel]
        public var epgURL: URL?
    }

    public static func parse(_ text: String) -> Result {
        var channels: [Channel] = []
        var epgURL: URL?
        var pending: (name: String, attrs: [String: String])?

        text.enumerateLines { rawLine, _ in
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            guard !line.isEmpty else { return }

            if line.hasPrefix("#EXTM3U") {
                // Header: playlist EPG kaynağı (url-tvg / x-tvg-url / tvg-url)
                let a = parseAttributes(line)
                for key in ["url-tvg", "x-tvg-url", "tvg-url"] {
                    if let v = a[key], let u = URL(string: v) { epgURL = u; break }
                }
                return
            }

            if line.hasPrefix("#EXTINF") {
                let attrs = parseAttributes(line)
                // Başlık = tırnak-dışı ilk virgülden sonrası (öznitelik değerindeki virgüller korunur).
                let name: String
                if let idx = firstCommaOutsideQuotes(line) {
                    name = String(line[line.index(after: idx)...]).trimmingCharacters(in: .whitespaces)
                } else {
                    name = attrs["tvg-name"] ?? "Kanal"
                }
                pending = (name.isEmpty ? (attrs["tvg-name"] ?? "Kanal") : name, attrs)
                return
            }

            // Yorum/direktif satırlarını atla (ör. #EXTGRP zaten attrs'a yansımaz; #KODIPROP vb.)
            if line.hasPrefix("#") { return }

            // Non-yorum satır = stream URL
            guard let p = pending, let url = URL(string: line) else { pending = nil; return }
            let group = p.attrs["group-title"].flatMap { $0.isEmpty ? nil : $0 } ?? "Diğer"
            let logo = p.attrs["tvg-logo"].flatMap { URL(string: $0) }
            let tvgId = p.attrs["tvg-id"].flatMap { $0.isEmpty ? nil : $0 }
            let ch = Channel(
                id: line,
                name: p.name,
                logo: logo,
                group: group,
                url: url,
                tvgId: tvgId,
                kind: SeriesDetector.kind(forName: p.name, group: group)
            )
            channels.append(ch)
            pending = nil
        }

        return Result(channels: channels, epgURL: epgURL)
    }

    /// Tırnak (`"`) dışında kalan ilk virgülün indeksini bulur. Öznitelik değerlerinin (ör.
    /// `group-title="Spor, Canlı"`) ve virgüllü başlıkların doğru ayrışması için gerekir.
    static func firstCommaOutsideQuotes(_ line: String) -> String.Index? {
        var inQuotes = false
        var i = line.startIndex
        while i < line.endIndex {
            let c = line[i]
            if c == "\"" { inQuotes.toggle() }
            else if c == "," && !inQuotes { return i }
            i = line.index(after: i)
        }
        return nil
    }

    /// `key="value"` çiftlerini ayıklar (EXTINF ve EXTM3U header için).
    static func parseAttributes(_ line: String) -> [String: String] {
        var out: [String: String] = [:]
        let pattern = #"([A-Za-z0-9\-]+)="([^"]*)""#
        guard let re = try? NSRegularExpression(pattern: pattern) else { return out }
        let ns = line as NSString
        for m in re.matches(in: line, range: NSRange(location: 0, length: ns.length)) {
            let key = ns.substring(with: m.range(at: 1)).lowercased()
            let val = ns.substring(with: m.range(at: 2))
            out[key] = val
        }
        return out
    }
}

/// VOD/Dizi tespiti. PWA referansı: `detectSeries()` (index.html:739), `chType()` (:768).
public enum SeriesDetector {

    public struct Match: Sendable { public let season: Int; public let episode: Int; public let showName: String }

    private static let patterns = [
        #"^(.*?)[\s._-]+S(\d{1,2})[\s._-]*E(\d{1,3})\b"#,           // Show S01E02
        #"^(.*?)[\s._-]+(\d{1,2})x(\d{1,3})\b"#,                     // Show 1x02
        #"^(.*?)[\s._-]+Sezon[\s._-]*(\d{1,2}).*?Bölüm[\s._-]*(\d{1,3})"#  // Show Sezon 1 Bölüm 2
    ]

    public static func detect(_ name: String) -> Match? {
        for p in patterns {
            guard let re = try? NSRegularExpression(pattern: p, options: [.caseInsensitive]) else { continue }
            let ns = name as NSString
            if let m = re.firstMatch(in: name, range: NSRange(location: 0, length: ns.length)), m.numberOfRanges >= 4 {
                let show = ns.substring(with: m.range(at: 1)).trimmingCharacters(in: CharacterSet(charactersIn: " ._-"))
                let s = Int(ns.substring(with: m.range(at: 2))) ?? 0
                let e = Int(ns.substring(with: m.range(at: 3))) ?? 0
                return Match(season: s, episode: e, showName: show.isEmpty ? name : show)
            }
        }
        return nil
    }

    /// Grup adı + kanal adından medya türü tahmini.
    public static func kind(forName name: String, group: String) -> MediaKind {
        let g = group.lowercased()
        if detect(name) != nil { return .series }
        if g.contains("dizi") || g.contains("series") { return .series }
        if g.contains("film") || g.contains("movie") || g.contains("vod") { return .vod }
        return .live
    }
}
