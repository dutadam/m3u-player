import SwiftUI
import Core
import Design

/// Kaynak ekleme / onboarding. Tasarım: docs/design/ui-preview.html §Onboarding.
/// Güven-öncelikli: kimlik bilgisi Keychain'de (entegrasyon notu), nötr oynatıcı mesajı.
struct OnboardingView: View {
    @EnvironmentObject private var library: LibraryStore
    enum Tab: String, CaseIterable { case m3u = "M3U URL", xtream = "Xtream", file = "Dosya", discover = "Keşfet" }
    @State private var tab: Tab = .xtream
    @State private var m3uURL = ""
    @State private var server = "", user = "", pass = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Kaynağını ekle,\nizlemeye başla")
                    .font(.system(size: 28, weight: .heavy)).foregroundStyle(.sgText)
                Text("Xtream Codes, M3U bağlantısı veya dosya. Hiçbir kanal uygulamada gömülü değil — kendi aboneliğini getirirsin.")
                    .font(.subheadline).foregroundStyle(.sgDim)

                Picker("", selection: $tab) {
                    ForEach(Tab.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                }.pickerStyle(.segmented)

                switch tab {
                case .m3u:
                    field("M3U URL", text: $m3uURL)
                    connectButton("Yükle") {
                        if let u = URL(string: m3uURL) { await library.loadM3U(from: u) }
                    }
                case .xtream:
                    field("SUNUCU", text: $server)
                    field("KULLANICI ADI", text: $user)
                    field("ŞİFRE", text: $pass, secure: true)
                    connectButton("Bağlan") {
                        if let s = XtreamCredentials.normalize(server) {
                            await library.loadXtream(.init(server: s, username: user, password: pass))
                        }
                    }
                case .file:
                    Text("Dosya seçici (UIDocumentPicker) bir sonraki adımda.").foregroundStyle(.sgDim)
                case .discover:
                    Text("iptv-org ücretsiz katalog (keşfet) — Faz 4.").foregroundStyle(.sgDim)
                }

                if let err = library.errorMessage {
                    Text(err).font(.footnote).foregroundStyle(.sgLive)
                }

                Label("Kimlik bilgilerin cihazında Keychain ile şifreli saklanır. Sunucu yok, telemetri yok, üçüncü-parti proxy yok.",
                      systemImage: "checkmark.shield.fill")
                    .font(.caption).foregroundStyle(.sgDim)
                    .padding(.top, 8)
            }
            .padding()
        }
        .background(Color.sgGround.ignoresSafeArea())
    }

    private func field(_ label: String, text: Binding<String>, secure: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.caption).foregroundStyle(.sgMute)
            Group {
                if secure { SecureField("", text: text) } else { TextField("", text: text) }
            }
            .textFieldStyle(.plain)
            .padding(12)
            .background(Color.sgSurface, in: RoundedRectangle(cornerRadius: 11))
            .overlay(RoundedRectangle(cornerRadius: 11).stroke(Color.sgLine))
            #if !os(tvOS)
            .autocorrectionDisabled()
            .textInputAutocapitalization(.never)
            #endif
        }
    }

    private func connectButton(_ title: String, action: @escaping () async -> Void) -> some View {
        Button {
            Task { await action() }
        } label: {
            if library.isLoading {
                ProgressView().frame(maxWidth: .infinity)
            } else {
                Text(title).font(.system(size: 14, weight: .bold)).frame(maxWidth: .infinity)
            }
        }
        .padding(13)
        .background(Color.sgAccent, in: RoundedRectangle(cornerRadius: 12))
        .foregroundStyle(.white)
    }
}
