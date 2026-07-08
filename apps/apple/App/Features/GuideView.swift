import SwiftUI
import Core
import Design

/// EPG rehberi — zaman çizelgeli grid. Tasarım: docs/design/ui-preview.html §Otomatik EPG.
/// Kanal sütunu solda sabit; timeline yatay kayar; kırmızı/turuncu "şimdi" çizgisi.
struct GuideView: View {
    @EnvironmentObject private var library: LibraryStore
    @State private var selected: Channel?

    // Zaman penceresi & ölçek
    private let ppm: CGFloat = 3.5          // pt / dakika (30dk = 105pt)
    private let rowH: CGFloat = 58
    private let headerH: CGFloat = 34
    private let chColW: CGFloat = 68
    private let spanHours = 8

    private var windowStart: Date {
        let cal = Calendar.current
        let hourStart = cal.dateInterval(of: .hour, for: Date())?.start ?? Date()
        return cal.date(byAdding: .hour, value: -1, to: hourStart) ?? hourStart
    }
    private var totalWidth: CGFloat { CGFloat(spanHours * 60) * ppm }

    var body: some View {
        NavigationStack {
            ScrollView(.vertical, showsIndicators: false) {
                HStack(alignment: .top, spacing: 0) {
                    channelColumn
                    ScrollView(.horizontal, showsIndicators: false) {
                        LazyVStack(alignment: .leading, spacing: 0) {
                            timeHeader
                            ForEach(library.live) { ch in
                                timelineRow(for: ch)
                                    .frame(height: rowH)
                                Divider().overlay(Color.sgLineSoft)
                            }
                        }
                        .overlay(alignment: .topLeading) { nowLine }
                    }
                }
            }
            .background(Color.sgGround)
            .navigationTitle("Rehber")
            .fullScreenCover(item: $selected) { PlayerView(channel: $0) }
        }
    }

    // MARK: - Sol sabit kanal sütunu
    private var channelColumn: some View {
        LazyVStack(alignment: .leading, spacing: 0) {
            Color.clear.frame(height: headerH)          // zaman başlığıyla hizala
            ForEach(library.live) { ch in
                HStack(spacing: 6) {
                    Text(String(ch.name.prefix(2)).uppercased())
                        .font(.system(size: 10, weight: .heavy))
                        .frame(width: 26, height: 26)
                        .background(Color.sgElevated, in: RoundedRectangle(cornerRadius: 7))
                    Text(ch.name).font(.system(size: 9.5, weight: .semibold))
                        .foregroundStyle(Color.sgDim).lineLimit(1)
                }
                .frame(width: chColW, height: rowH, alignment: .leading)
                .padding(.leading, 8)
                Divider().overlay(Color.sgLineSoft)
            }
        }
        .frame(width: chColW + 8)
        .background(Color.sgGround)
        .overlay(alignment: .trailing) { Rectangle().fill(Color.sgLineSoft).frame(width: 1) }
    }

    // MARK: - Zaman başlığı (30dk aralık)
    private var timeHeader: some View {
        HStack(spacing: 0) {
            ForEach(0..<(spanHours * 2), id: \.self) { i in
                let t = windowStart.addingTimeInterval(Double(i) * 30 * 60)
                Text(Self.hm.string(from: t))
                    .font(.system(size: 11, weight: .bold)).monospacedDigit()
                    .foregroundStyle(Color.sgMute)
                    .frame(width: 30 * ppm, alignment: .leading)
                    .padding(.leading, 6)
            }
        }
        .frame(height: headerH)
        .overlay(alignment: .bottom) { Rectangle().fill(Color.sgLineSoft).frame(height: 1) }
    }

    // MARK: - Bir kanalın program bloğu satırı
    private func timelineRow(for ch: Channel) -> some View {
        let programs = library.epg?.entries(for: ch) ?? []
        return ZStack(alignment: .topLeading) {
            if programs.isEmpty {
                Text("Program bilgisi yok").font(.caption2).foregroundStyle(Color.sgMute)
                    .frame(width: totalWidth, height: rowH, alignment: .leading).padding(.leading, 10)
            } else {
                ForEach(programs, id: \.start) { p in
                    programBlock(p, channel: ch)
                        .offset(x: xPos(p.start))
                }
            }
        }
        .frame(width: totalWidth, height: rowH, alignment: .leading)
    }

    private func programBlock(_ p: EpgEntry, channel: Channel) -> some View {
        let w = max(24, CGFloat(p.stop.timeIntervalSince(p.start) / 60) * ppm)
        let isNow = p.isLiveNow
        let isPast = p.stop < Date()
        let catchup = isPast ? library.catchupChannel(for: channel, program: p) : nil
        return VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 4) {
                if catchup != nil { Image(systemName: "arrow.uturn.backward").font(.system(size: 8, weight: .bold)).foregroundStyle(Color.sgAccent2) }
                Text(p.title).font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(isNow ? Color.sgAccent2 : Color.sgText).lineLimit(1)
            }
            Text("\(Self.hm.string(from: p.start))–\(Self.hm.string(from: p.stop))")
                .font(.system(size: 10)).monospacedDigit().foregroundStyle(Color.sgMute)
        }
        .frame(width: w, height: rowH - 8, alignment: .leading)
        .padding(.horizontal, 9)
        .opacity(isPast && catchup == nil ? 0.5 : 1)          // catchup yoksa geçmiş soluk
        .background(isNow ? Color.sgAccent.opacity(0.14) : Color.clear)
        .overlay(alignment: .trailing) { Rectangle().fill(Color.sgLineSoft).frame(width: 1) }
        .contentShape(Rectangle())
        .onTapGesture { selected = catchup ?? channel }        // catchup varsa timeshift, yoksa canlı
    }

    // MARK: - "Şimdi" çizgisi
    private var nowLine: some View {
        Rectangle()
            .fill(Color.sgLive)
            .frame(width: 2)
            .shadow(color: Color.sgLive.opacity(0.5), radius: 6)
            .offset(x: xPos(Date()))
            .frame(maxHeight: .infinity, alignment: .top)
    }

    private func xPos(_ date: Date) -> CGFloat {
        max(0, CGFloat(date.timeIntervalSince(windowStart) / 60) * ppm)
    }

    private static let hm: DateFormatter = {
        let f = DateFormatter(); f.dateFormat = "HH:mm"; f.locale = Locale(identifier: "tr_TR"); return f
    }()
}
