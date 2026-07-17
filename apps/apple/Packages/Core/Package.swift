// swift-tools-version: 5.9
import PackageDescription

// Core — platform-bağımsız iş mantığı: M3U/Xtream/EPG ayrıştırma + stream çözümleme.
// iOS · iPadOS · macOS · tvOS ortak çekirdeği. Harici bağımlılık yok (Foundation).
let package = Package(
    name: "Core",
    platforms: [.iOS(.v16), .tvOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "Core", targets: ["Core"])
    ],
    targets: [
        .target(name: "Core"),
        .testTarget(
            name: "CoreTests",
            dependencies: ["Core"],
            resources: [.copy("Fixtures")]
        )
    ]
)
