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
            VStack(spacing: 22) {
                header
                formCard
                trustFooter
            }
            .padding(20)
            .frame(maxWidth: 540)
            .frame(maxWidth: .infinity)
        }
        .background(backdrop)
    }

    // MARK: - Bölümler
    private var backdrop: some View {
        ZStack {
            Color.sgGround
            RadialGradient(colors: [Color.sgAccent.opacity(0.16), .clear],
                           center: .top, startRadius: 8, endRadius: 360)
        }.ignoresSafeArea()
    }

    private var header: some View {
        VStack(spacing: 12) {
            BrandMark(size: 84, glow: true)
            Text(Brand.name).font(.system(size: 30, weight: .heavy)).foregroundStyle(Color.sgText)
            Text("iOS'ta premium, çökmeyen yayın deneyimi")
                .font(.subheadline).foregroundStyle(Color.sgAccent2).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity).padding(.top, 24)
    }

    private var formCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Kaynağını ekle").font(.system(size: 17, weight: .bold)).foregroundStyle(Color.sgText)

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
                        }.buttonStyle(PressableStyle())
                    }
                }
            }

            if let err = library.errorMessage {
                Label(err, systemImage: "exclamationmark.triangle.fill")
                    .font(.footnote).foregroundStyle(Color.sgLive)
            }
        }
        .padding(16)
        .background(Color.white.opacity(0.03), in: RoundedRectangle(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.sgLine))
    }

    private var trustFooter: some View {
        VStack(spacing: 10) {
            HStack(spacing: 18) {
                trustBadge("lock.shield.fill", "Şifreli")
                trustBadge("eye.slash.fill", "Telemetri yok")
                trustBadge("bolt.slash.fill", "Proxy yok")
            }
            Text("Kimlik bilgilerin cihazda Keychain ile saklanır. Hiçbir kanal gömülü değil — kendi aboneliğini getirirsin.")
                .font(.caption2).foregroundStyle(Color.sgDim).multilineTextAlignment(.center)
        }
        .padding(.top, 4)
    }

    private func trustBadge(_ icon: String, _ text: String) -> some View {
        VStack(spacing: 4) {
            Image(systemName: icon).font(.system(size: 15)).foregroundStyle(Color.sgAccent2)
            Text(text).font(.system(size: 10, weight: .semibold)).foregroundStyle(Color.sgDim)
        }
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
            Group {
                if library.isLoading { ProgressView().tint(.white) }
                else { Text(title).font(.system(size: 15, weight: .bold)) }
            }
            .frame(maxWidth: .infinity).padding(14)
            .background(LinearGradient(colors: [Color.sgAccent, Color.sgGold],
                                       startPoint: .leading, endPoint: .trailing),
                        in: RoundedRectangle(cornerRadius: 13))
            .foregroundStyle(.white)
            .shadow(color: Color.sgAccent.opacity(0.4), radius: 12, y: 4)
        }
        .buttonStyle(PressableStyle())
        .disabled(library.isLoading)
    }
}

extension View {
    /// Segmented picker stili yalnız iOS/Catalyst'te var; tvOS'ta varsayılan stile düşer. (Modül geneli)
    @ViewBuilder func segmentedOnIOS() -> some View {
        #if os(iOS)
        self.pickerStyle(.segmented)
        #else
        self
        #endif
    }
}
