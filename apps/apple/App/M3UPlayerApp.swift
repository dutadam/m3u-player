import SwiftUI

// Uygulama giriş noktası. iOS · iPadOS · macOS · tvOS ortak.
@main
struct M3UPlayerApp: App {
    @StateObject private var library = LibraryStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(library)
                .preferredColorScheme(.dark)   // "Signal" — dark-committed
        }
    }
}
