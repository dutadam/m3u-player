// swift-tools-version: 5.9
import PackageDescription

// Design — "Signal" tasarım sistemi: renk/tipografi token'ları + yeniden kullanılabilir bileşenler.
// Görsel kaynak: docs/design/ui-preview.html. iOS · iPadOS · macOS · tvOS.
let package = Package(
    name: "Design",
    platforms: [.iOS(.v16), .tvOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "Design", targets: ["Design"])
    ],
    targets: [
        .target(name: "Design")
    ]
)
