import XCTest
@testable import Core

final class XtreamModelsTests: XCTestCase {

    private func fixture(_ name: String, _ ext: String) throws -> Data {
        let url = try XCTUnwrap(Bundle.module.url(forResource: name, withExtension: ext, subdirectory: "Fixtures"))
        return try Data(contentsOf: url)
    }

    // Xtream'in en riskli yanı: sayı alanları Int veya String gelebilir.
    func testLenientIntAcceptsIntAndString() throws {
        struct Wrap: Codable { let a: LenientInt; let b: LenientInt }
        let data = Data(#"{"a": 1001, "b": "1002"}"#.utf8)
        let w = try JSONDecoder().decode(Wrap.self, from: data)
        XCTAssertEqual(w.a.value, 1001)
        XCTAssertEqual(w.b.value, 1002)
    }

    func testDecodeLiveStreamsMixedTypes() throws {
        let streams = try JSONDecoder().decode([XtreamLiveStream].self, from: fixture("live_streams", "json"))
        XCTAssertEqual(streams.count, 2)
        XCTAssertEqual(streams[0].streamId.value, 1001)     // Int
        XCTAssertEqual(streams[1].streamId.value, 1002)     // String → Int
        XCTAssertTrue(streams[0].hasCatchup)                // tv_archive: 1
        XCTAssertFalse(streams[1].hasCatchup)               // tv_archive: "0"
        XCTAssertEqual(streams[0].epgChannelId, "beinsports1.tr")
    }

    func testDecodeSeriesInfo() throws {
        let info = try JSONDecoder().decode(XtreamSeriesInfo.self, from: fixture("series_info", "json"))
        XCTAssertEqual(info.info?.name, "Breaking Bad")
        XCTAssertEqual(info.episodes.count, 2)              // 2 sezon
        let s1 = try XCTUnwrap(info.episodes["1"])
        XCTAssertEqual(s1.count, 2)
        XCTAssertEqual(s1[0].episodeNum.value, 1)           // Int
        XCTAssertEqual(s1[1].episodeNum.value, 2)           // String → Int
        XCTAssertEqual(s1[0].containerExtension, "mkv")
        XCTAssertEqual(s1[0].info?.duration, "00:58:00")
    }

    func testStreamURLsFromClient() {
        let server = XtreamCredentials.normalize("portal.example.com:8080")!
        let client = XtreamClient(creds: .init(server: server, username: "u", password: "p"))
        // Canlı → .m3u8 (iOS native HLS tercihi)
        XCTAssertEqual(client.liveURL(streamId: 1001).absoluteString,
                       "http://portal.example.com:8080/live/u/p/1001.m3u8")
        // Dizi bölümü → container_extension
        XCTAssertEqual(client.seriesURL(episodeId: "3002", ext: "mp4").absoluteString,
                       "http://portal.example.com:8080/series/u/p/3002.mp4")
        // Catchup/timeshift şeması
        let ts = client.timeshiftURL(streamId: 1001, durationMin: 90,
                                     start: Date(timeIntervalSince1970: 0))
        XCTAssertTrue(ts.absoluteString.contains("/timeshift/u/p/90/"))
        XCTAssertTrue(ts.absoluteString.hasSuffix("/1001.ts"))
    }
}
