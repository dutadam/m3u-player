import XCTest
@testable import Core

final class M3UParserTests: XCTestCase {

    private func loadSample() throws -> String {
        let url = try XCTUnwrap(Bundle.module.url(forResource: "sample", withExtension: "m3u",
                                                  subdirectory: "Fixtures"))
        return try String(contentsOf: url, encoding: .utf8)
    }

    func testParsesAllChannels() throws {
        let result = M3UParser.parse(try loadSample())
        XCTAssertEqual(result.channels.count, 6)
    }

    func testHeaderEPGURL() throws {
        let result = M3UParser.parse(try loadSample())
        XCTAssertEqual(result.epgURL?.absoluteString, "http://example.com/epg.xml")
    }

    func testAttributesAndGroup() throws {
        let ch = M3UParser.parse(try loadSample()).channels
        XCTAssertEqual(ch[0].name, "beIN SPORTS 1 FHD")
        XCTAssertEqual(ch[0].group, "Spor")
        XCTAssertEqual(ch[0].tvgId, "beinsports1.tr")
        XCTAssertEqual(ch[0].quality, .fhd)
    }

    func testEmptyGroupFallsBack() throws {
        let cnn = M3UParser.parse(try loadSample()).channels.last!
        XCTAssertEqual(cnn.name, "CNN International")
        XCTAssertEqual(cnn.group, "Diğer")   // group-title="" → "Diğer"
        XCTAssertNil(cnn.quality)
    }

    func testQualityDetection() {
        XCTAssertEqual(Quality.detect(from: "Movie 4K UHD"), .uhd4k)
        XCTAssertEqual(Quality.detect(from: "Channel FHD"), .fhd)
        XCTAssertEqual(Quality.detect(from: "Channel HD"), .hd)
        XCTAssertEqual(Quality.detect(from: "Channel SD"), .sd)
        XCTAssertNil(Quality.detect(from: "Plain Channel"))
    }

    func testSeriesDetection() {
        let m1 = SeriesDetector.detect("Breaking Bad S01E02 Cat's in the Bag")
        XCTAssertEqual(m1?.season, 1); XCTAssertEqual(m1?.episode, 2)
        XCTAssertEqual(m1?.showName, "Breaking Bad")

        let m2 = SeriesDetector.detect("Kurulus Osman Sezon 5 Bölüm 12")
        XCTAssertEqual(m2?.season, 5); XCTAssertEqual(m2?.episode, 12)

        XCTAssertNil(SeriesDetector.detect("beIN SPORTS 1 FHD"))
    }

    func testMediaKindClassification() throws {
        let ch = M3UParser.parse(try loadSample()).channels
        XCTAssertEqual(ch[0].kind, .live)                 // Spor → live
        XCTAssertEqual(ch[2].kind, .vod)                  // Filmler grubu → vod
        XCTAssertEqual(ch[3].kind, .series)               // S01E02 deseni → series
        XCTAssertEqual(ch[4].kind, .series)               // Sezon/Bölüm deseni → series
    }
}
