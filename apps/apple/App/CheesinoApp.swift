import SwiftUI

// Cheesino — uygulama giriş noktası. iOS · iPadOS · macOS · tvOS ortak.
@main
struct CheesinoApp: App {
    @StateObject private var library = LibraryStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(library)
                .preferredColorScheme(.dark)   // "Signal" — dark-committed
        }
    }
}

/// Marka sabitleri (tek kaynak).
enum Brand {
    static let name = "cheesino"
    static let tagline = "iOS'ta TiviMate kalitesinde yayın deneyimi"
}
