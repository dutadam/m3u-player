import Foundation

// Crash / hang / CPU-exception teşhisi — Apple-native MetricKit. ÜÇÜNCÜ-PARTİ TELEMETRİ YOK.
// Payload'lar yalnız cihazda saklanır (Documents/Diagnostics); kullanıcı isterse paylaşır.
// TestFlight beta'da stabilite hatalarını (rakiplerin "60 sn'de donma" sorunu gibi) teşhis etmek için.

#if canImport(MetricKit) && os(iOS)
import MetricKit

final class DiagnosticsMonitor: NSObject, MXMetricManagerSubscriber {
    static let shared = DiagnosticsMonitor()

    func start() { MXMetricManager.shared.add(self) }

    func didReceive(_ payloads: [MXMetricPayload]) {
        // Performans metrikleri (başlatma süresi, bellek, donma oranı) — şimdilik saklanmıyor.
    }

    @available(iOS 14.0, *)
    func didReceive(_ payloads: [MXDiagnosticPayload]) {
        for p in payloads { persist(p.jsonRepresentation()) }   // crash/hang/disk-write vb.
    }

    private func persist(_ data: Data) {
        guard let dir = try? FileManager.default.url(for: .documentDirectory, in: .userDomainMask,
                                                     appropriateFor: nil, create: true) else { return }
        let folder = dir.appendingPathComponent("Diagnostics", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let file = folder.appendingPathComponent("diag-\(Int(Date().timeIntervalSince1970)).json")
        try? data.write(to: file)
    }
}

#else

/// MetricKit yoksa (tvOS/macOS) no-op — aynı arayüz.
final class DiagnosticsMonitor {
    static let shared = DiagnosticsMonitor()
    func start() {}
}

#endif
