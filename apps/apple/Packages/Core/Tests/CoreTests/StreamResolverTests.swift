import XCTest
@testable import Core

final class StreamResolverTests: XCTestCase {

    func testHTTPSPassesThrough() {
        let u = URL(string: "https://secure.example.com/live/u/p/1.m3u8")!
        let c = StreamResolver.candidates(for: u)
        XCTAssertEqual(c.count, 1)
        XCTAssertEqual(c.first?.url, u)
        XCTAssertEqual(c.first?.engine, .avPlayer)
    }

    func testHTTPUpgradesThenKeepsOriginal() {
        // Native'de mixed-content yok → HTTPS yükseltmesi + orijinal HTTP her ikisi de aday.
        let u = URL(string: "http://portal.example.com:8080/live/u/p/1.ts")!
        let c = StreamResolver.candidates(for: u)
        let urls = c.map { $0.url.absoluteString }
        XCTAssertTrue(urls.contains("https://portal.example.com/live/u/p/1.ts"))
        XCTAssertTrue(urls.contains("https://portal.example.com:8080/live/u/p/1.ts"))
        XCTAssertTrue(urls.contains("http://portal.example.com:8080/live/u/p/1.ts"))
        XCTAssertEqual(c.last?.engine, .vlcKit)   // .ts → VLCKit
    }

    func testEngineSelection() {
        XCTAssertEqual(StreamResolver.candidates(for: URL(string: "https://x/y.m3u8")!).first?.engine, .avPlayer)
        XCTAssertEqual(StreamResolver.candidates(for: URL(string: "https://x/y.mkv")!).first?.engine, .vlcKit)
        XCTAssertEqual(StreamResolver.candidates(for: URL(string: "https://x/y.avi")!).first?.engine, .vlcKit)
        // Uzantısız → çoğunlukla HLS → AVPlayer
        XCTAssertEqual(StreamResolver.candidates(for: URL(string: "https://x/live/u/p/1")!).first?.engine, .avPlayer)
    }

    func testIOSHLSVariant() {
        let bare = URL(string: "http://x/live/u/p/1001")!
        XCTAssertEqual(StreamResolver.iOSHLSVariant(of: bare)?.absoluteString, "http://x/live/u/p/1001.m3u8")
        // Zaten uzantılı → nil
        XCTAssertNil(StreamResolver.iOSHLSVariant(of: URL(string: "http://x/1.m3u8")!))
        XCTAssertNil(StreamResolver.iOSHLSVariant(of: URL(string: "http://x/1.mkv")!))
    }
}

final class XtreamClientTests: XCTestCase {

    private var client: XtreamClient {
        let server = XtreamCredentials.normalize("portal.example.com:8080/")!
        return XtreamClient(creds: XtreamCredentials(server: server, username: "u", password: "p"))
    }

    func testServerNormalization() {
        XCTAssertEqual(XtreamCredentials.normalize("portal.example.com:8080/")?.absoluteString,
                       "http://portal.example.com:8080")
        XCTAssertEqual(XtreamCredentials.normalize("https://x.com///")?.absoluteString, "https://x.com")
    }

    func testAPIURLBuilding() {
        let url = client.apiURL(.seriesInfo, params: ["series_id": "42"]).absoluteString
        XCTAssertTrue(url.hasPrefix("http://portal.example.com:8080/player_api.php?"))
        XCTAssertTrue(url.contains("username=u"))
        XCTAssertTrue(url.contains("password=p"))
        XCTAssertTrue(url.contains("action=get_series_info"))
        XCTAssertTrue(url.contains("series_id=42"))
    }

    func testStreamURLSchemes() {
        XCTAssertEqual(client.liveURL(streamId: 1001).absoluteString,
                       "http://portal.example.com:8080/live/u/p/1001.m3u8")
        XCTAssertEqual(client.vodURL(streamId: 2001, ext: "mkv").absoluteString,
                       "http://portal.example.com:8080/movie/u/p/2001.mkv")
        XCTAssertEqual(client.seriesURL(episodeId: "3002", ext: "mp4").absoluteString,
                       "http://portal.example.com:8080/series/u/p/3002.mp4")
    }

    func testXMLTVURL() {
        XCTAssertTrue(client.xmltvURL.absoluteString.hasPrefix("http://portal.example.com:8080/xmltv.php?"))
    }
}

final class XMLTVParserTests: XCTestCase {

    func testParseDate() {
        let d1 = XMLTVParser.parseDate("20260706203000 +0300")
        XCTAssertNotNil(d1)
        let d2 = XMLTVParser.parseDate("20260706203000")
        XCTAssertNotNil(d2)
    }

    func testParseProgrammes() {
        let xml = """
        <?xml version="1.0"?>
        <tv>
          <programme start="20260706193000 +0300" stop="20260706214500 +0300" channel="beinsports1.tr">
            <title>GS - Real Madrid</title><desc>Çeyrek final</desc>
          </programme>
          <programme start="20260706214500 +0300" stop="20260706230000 +0300" channel="beinsports1.tr">
            <title>Maç Sonu</title>
          </programme>
        </tv>
        """
        let entries = XMLTVParser.parse(Data(xml.utf8))
        XCTAssertEqual(entries.count, 2)
        XCTAssertEqual(entries[0].title, "GS - Real Madrid")
        XCTAssertEqual(entries[0].desc, "Çeyrek final")
        XCTAssertEqual(entries[0].channelId, "beinsports1.tr")
    }

    func testEPGIndexFuzzyMatch() {
        let start = Date().addingTimeInterval(-600)
        let stop = Date().addingTimeInterval(600)
        let e = EpgEntry(channelId: "beIN Sports 1", start: start, stop: stop, title: "Canlı Maç")
        let index = EPGIndex(entries: [e])
        // tvg-id yok → isim fuzzy eşleşmesi ("beIN SPORTS 1 FHD" ↔ "beIN Sports 1")
        let ch = Channel(id: "x", name: "beIN SPORTS 1 FHD", group: "Spor",
                         url: URL(string: "http://x/1")!)
        XCTAssertEqual(index.nowPlaying(for: ch)?.title, "Canlı Maç")
    }
}
