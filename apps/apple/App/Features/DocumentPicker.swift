import SwiftUI

// M3U dosyası seçici (iOS/iPadOS). tvOS'ta dosya sistemi erişimi yok → yalnız iOS.

#if os(iOS)
import UIKit
import UniformTypeIdentifiers

struct DocumentPicker: UIViewControllerRepresentable {
    var onPick: (String) -> Void

    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let m3u = UTType(filenameExtension: "m3u") ?? .plainText
        let m3u8 = UTType(filenameExtension: "m3u8") ?? .plainText
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [m3u, m3u8, .plainText, .text])
        picker.delegate = context.coordinator
        picker.allowsMultipleSelection = false
        return picker
    }

    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onPick: onPick) }

    final class Coordinator: NSObject, UIDocumentPickerDelegate {
        let onPick: (String) -> Void
        init(onPick: @escaping (String) -> Void) { self.onPick = onPick }

        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            guard let url = urls.first else { return }
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            if let text = try? String(contentsOf: url, encoding: .utf8) { onPick(text) }
        }
    }
}
#endif
