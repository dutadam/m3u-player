import Foundation

/// Oynatma kaynağı çözümleme. Spec: docs/spec/xtream-m3u-epg.md §5.
/// PWA referansı: `streamSources()` (index.html:1246) — ancak native'de CORS/proxy YOK
/// ve mixed-content engeli YOK, bu yüzden HTTP kaynağı her zaman aday olarak eklenir.
public enum StreamResolver {

    /// Bir kaynağın hangi oynatıcıya gideceğine dair ipucu.
    public enum Engine: Sendable, Equatable {
        case avPlayer   // HLS (.m3u8) / MP4 — native
        case vlcKit     // MKV/AVI/TS/exotik codec — fallback
    }

    public struct Candidate: Sendable, Equatable {
        public let url: URL
        public let engine: Engine
        public init(url: URL, engine: Engine) { self.url = url; self.engine = engine }
        public static func == (l: Candidate, r: Candidate) -> Bool { l.url == r.url }
    }

    /// Denenecek kaynakları sırayla üretir. Native olduğu için `pageIsHTTPS` her zaman false kabul edilir
    /// (mixed-content yok); parametre yalnız birim testleri ve web-paritesi için tutulur.
    public static func candidates(for original: URL, isIOS: Bool = true) -> [Candidate] {
        var urls: [URL] = []
        func add(_ u: URL?) { if let u, !urls.contains(u) { urls.append(u) } }

        if original.scheme?.lowercased() == "https" {
            add(original)
        } else {
            // HTTP → önce HTTPS yükseltmesi (443 ve varsa aynı port), sonra orijinal HTTP.
            var https = URLComponents(url: original, resolvingAgainstBaseURL: false)
            https?.scheme = "https"
            https?.port = nil
            add(https?.url)
            if let port = original.port {
                var withPort = URLComponents(url: original, resolvingAgainstBaseURL: false)
                withPort?.scheme = "https"; withPort?.port = port
                add(withPort?.url)
            }
            add(original) // native: mixed-content engeli olmadığı için HTTP her zaman geçerli aday
        }
        if urls.isEmpty { add(original) }

        var out = urls.map { Candidate(url: $0, engine: engine(for: $0)) }

        // Xtream canlı: .m3u8 (AVPlayer/HLS) oynamazsa .ts (VLC/MPEG-TS) dene. Çoğu Xtream paneli
        // canlıyı MPEG-TS olarak sunar; AVPlayer .ts oynatamaz, VLC oynatır.
        if original.pathExtension.lowercased() == "m3u8" {
            for u in urls {
                var comps = URLComponents(url: u, resolvingAgainstBaseURL: false)
                comps?.path = ((comps?.path ?? "") as NSString).deletingPathExtension + ".ts"
                if let ts = comps?.url { out.append(Candidate(url: ts, engine: .vlcKit)) }
            }
        }
        return out
    }

    /// iOS Safari/AVPlayer, uzantısız Xtream canlı URL'lerinde `.m3u8` ekiyle daha iyi çalışır.
    /// PWA referansı: `tryNative` iOS dalı (index.html:1289).
    public static func iOSHLSVariant(of url: URL) -> URL? {
        let path = url.path.lowercased()
        let hasMediaExt = [".m3u8", ".mp4", ".ts", ".mkv", ".avi", ".flv"].contains { path.hasSuffix($0) }
        guard !hasMediaExt else { return nil }
        var comps = URLComponents(url: url, resolvingAgainstBaseURL: false)
        comps?.path += ".m3u8"
        return comps?.url
    }

    static func engine(for url: URL) -> Engine {
        let ext = url.pathExtension.lowercased()
        switch ext {
        case "m3u8", "mp4", "": return .avPlayer   // uzantısız çoğunlukla HLS
        case "mkv", "avi", "flv", "ts": return .vlcKit
        default: return .avPlayer
        }
    }
}
