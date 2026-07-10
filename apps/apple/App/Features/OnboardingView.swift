import SwiftUI
import Core
import Design

/// Kaynak ekleme / onboarding. Tasarım: docs/design/ui-preview.html §Onboarding.
/// Güven-öncelikli: kimlik bilgisi Keychain'de (entegrasyon notu), nötr oynatıcı mesajı.
struct OnboardingView: View {
    @EnvironmentObject private var library: LibraryStore
    enum Tab: String, CaseIterable { case m3u = "M3U URL", xtream = "Xtream", file = "Dosya", discover = "Keşfet" }

    // iptv-org ücretsiz katalog kaynakları (yasal free-to-air topluluk listeleri).
    static let discoverSources: [(name: String, code: String, flag: String)] = [
        ("Türkiye", "tr", "🇹🇷"), ("ABD", "us", "🇺🇸"), ("İngiltere", "uk", "🇬🇧"),
        ("Almanya", "de", "🇩🇪"), ("Tüm dünya", "index", "🌍")
    ]
    static func iptvOrgURL(_ code: String) -> String {
        code == "index" ? "https://iptv-org.github.io/iptv/index.m3u"
                        : "https://iptv-org.github.io/iptv/countries/\(code).m3u"
    }
    @State private var tab: Tab = .xtream
    @State private var name = ""
    @State private var m3uURL = ""
    @State private var server = ""
    @State private var user = ""
    @State private var pass = ""
    @State private var showFilePicker = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(Brand.name).font(.system(size: 15, weight: .heavy)).foregroundStyle(Color.sgAccent2)
                Text("Kaynağını ekle,\nizlemeye başla")
                    .font(.system(size: 28, weight: .heavy)).foregroundStyle(Color.sgText)
                Text("Xtream Codes, M3U bağlantısı veya dosya. Hiçbir kanal uygulamada gömülü değil — kendi aboneliğini getirirsin.")
                    .font(.subheadline).foregroundStyle(Color.sgDim)

                Picker("", selection: $tab) {
                    ForEach(Tab.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                }.segmentedOnIOS()

                if tab != .discover { field("AD (opsiyonel)", text: $name) }

                switch tab {
                case .m3u:
                    field("M3U URL", text: $m3uURL)
                    connectButton("Yükle") {
                        if let u = URL(string: m3uURL) { await library.addM3U(name: name, url: u) }
                    }
                case .xtream:
                    field("SUNUCU", text: $server)
                    field("KULLANICI ADI", text: $user)
                    field("ŞİFRE", text: $pass, secure: true)
                    connectButton("Bağlan") {
                        if let s = XtreamCredentials.normalize(server) {
                            await library.addXtream(name: name, creds: .init(server: s, username: user, password: pass))
                        }
                    }
                case .file:
                    #if os(iOS)
                    Button { showFilePicker = true } label: {
                        Label("M3U dosyası seç", systemImage: "doc.badge.plus")
                            .font(.system(size: 14, weight: .semibold)).frame(maxWidth: .infinity)
                            .padding(13)
                            .background(Color.sgSurface, in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.sgLine))
                            .foregroundStyle(Color.sgText)
                    }
                    .sheet(isPresented: $showFilePicker) {
                        DocumentPicker { text in library.addM3UFile(name: name, text: text); showFilePicker = false }
                    }
                    #else
                    Text("Dosya seçimi iOS/iPadOS'ta desteklenir.").foregroundStyle(Color.sgDim)
                    #endif
                case .discover:
                    VStack(alignment: .leading, spacing: 10) {
                        Text("iptv-org — ücretsiz, yasal, topluluk kanalları. Kendi kaynağın olmadan hemen dene.")
                            .font(.caption).foregroundStyle(Color.sgDim)
                        ForEach(Self.discoverSources, id: \.code) { src in
                            Button {
                                if let u = URL(string: Self.iptvOrgURL(src.code)) { Task { await library.addM3U(name: "iptv-org · \(src.name)", url: u) } }
                            } label: {
                                HStack {
                                    Text(src.flag).font(.title3)
                                    Text(src.name).font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.sgText)
                                    Spacer()
                                    Image(systemName: "arrow.down.circle").foregroundStyle(Color.sgAccent2)
                                }
                                .padding(12)
                                .background(Color.sgSurface, in: RoundedRectangle(cornerRadius: 11))
                                .overlay(RoundedRectangle(cornerRadius: 11).stroke(Color.sgLine))
                            }.buttonStyle(.plain)
                        }
                    }
                }

                if let err = library.errorMessage {
                    Text(err).font(.footnote).foregroundStyle(Color.sgLive)
                }

                Label("Kimlik bilgilerin cihazında Keychain ile şifreli saklanır. Sunucu yok, telemetri yok, üçüncü-parti proxy yok.",
                      systemImage: "checkmark.shield.fill")
                    .font(.caption).foregroundStyle(Color.sgDim)
                    .padding(.top, 8)
            }
            .padding()
        }
        .background(Color.sgGround.ignoresSafeArea())
    }

    private func field(_ label: String, text: Binding<String>, secure: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.caption).foregroundStyle(Color.sgMute)
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

private extension View {
    /// Segmented picker stili yalnız iOS'ta var; tvOS'ta varsayılan stile düşer.
    @ViewBuilder func segmentedOnIOS() -> some View {
        #if os(iOS)
        self.pickerStyle(.segmented)
        #else
        self
        #endif
    }
}
