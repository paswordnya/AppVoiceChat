import PipShared
import SwiftUI

private let personalities = ["calm", "chatty", "witty", "coach"]

private let brandColorOptions: [(hex: String, label: String)] = [
    ("#3B82F6", "Blue"),
    ("#8B5CF6", "Purple"),
    ("#22C55E", "Green"),
    ("#F97316", "Orange"),
    ("#EF4444", "Red"),
    ("#EC4899", "Pink"),
    ("#14B8A6", "Teal"),
]

private func colorFromHex(_ hex: String) -> Color {
    let cleaned = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
    guard let value = UInt32(cleaned, radix: 16) else { return PipTheme.accent }
    return Color(hex: value)
}

/// The redesign's Settings screen — buddy/personality, voice & reaction
/// toggles, speaking speed, brand color, and privacy. Distinct from
/// `ProfileView` ("Personalisasi"), linked from `AccountView` instead of
/// from here.
struct SettingsView: View {
    var onClose: () -> Void
    var onOpenAccount: () -> Void

    @ObservedObject private var buddyStore = BuddyStore.shared
    private let client = SettingsClient()
    @State private var settings: UserSettingsDto?
    @State private var isLoading = true
    @State private var errorMessage: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("Your sidekick")
                    .font(.pipDisplay(18, weight: .semibold))
                    .foregroundStyle(PipTheme.ink)
                Spacer()
                Button(action: onClose) {
                    Image(systemName: "xmark")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(PipTheme.ink)
                        .frame(width: 36, height: 36)
                        .background(PipTheme.ink.opacity(0.06))
                        .clipShape(Circle())
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 20)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let errorMessage {
                        Text(errorMessage).font(.pipBody(13)).foregroundStyle(.red)
                    }

                    if isLoading || settings == nil {
                        ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
                    } else {
                        hero
                        buddySwitcher
                        personalityChips
                        voiceAndReactionsCard
                        appearanceCard
                        privacyCard

                        Button(role: .destructive) {
                            client.clearHistory()
                        } label: {
                            Text("Clear all history").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                    }
                }
                .padding(.horizontal, 18)
                .padding(.bottom, 24)
            }
        }
        .background(PipTheme.cream.ignoresSafeArea())
        .task { await load() }
    }

    private var hero: some View {
        VStack(spacing: 6) {
            ZStack {
                BlobShape()
                    .fill(
                        LinearGradient(
                            colors: [buddyStore.selected.colorFrom, buddyStore.selected.colorTo],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                PipBlobFace()
            }
            .frame(width: 64, height: 64)

            Text(buddyStore.selected.name)
                .font(.pipDisplay(18, weight: .semibold))
                .foregroundStyle(PipTheme.ink)
            Text("your calm little sidekick")
                .font(.pipBody(13))
                .foregroundStyle(PipTheme.ink.opacity(0.5))

            Button(action: onOpenAccount) {
                HStack {
                    Text("Your account")
                        .font(.pipBody(14, weight: .medium))
                        .foregroundStyle(PipTheme.ink)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundStyle(PipTheme.ink.opacity(0.3))
                }
                .padding(14)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }
            .buttonStyle(.plain)
            .padding(.top, 14)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 6)
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.pipBody(12, weight: .semibold))
            .foregroundStyle(PipTheme.ink.opacity(0.45))
    }

    private var buddySwitcher: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionLabel("Switch buddy")
            HStack(spacing: 12) {
                ForEach(Buddy.all) { buddy in
                    Button {
                        buddyStore.select(buddy)
                        Task {
                            if case .failure(let message) = await client.setBuddy(buddy.id) { showError(message) }
                        }
                    } label: {
                        VStack(spacing: 4) {
                            Circle()
                                .fill(
                                    LinearGradient(
                                        colors: [buddy.colorFrom, buddy.colorTo],
                                        startPoint: .topLeading, endPoint: .bottomTrailing
                                    )
                                )
                                .frame(width: 40, height: 40)
                            Text(buddy.name)
                                .font(.pipBody(11, weight: buddy.id == buddyStore.selected.id ? .semibold : .regular))
                                .foregroundStyle(PipTheme.ink.opacity(buddy.id == buddyStore.selected.id ? 1 : 0.6))
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var personalityChips: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionLabel("Personality")
            HStack(spacing: 8) {
                ForEach(personalities, id: \.self) { p in
                    let active = p == settings?.personality
                    Button {
                        Task {
                            switch await client.setPersonality(p) {
                            case .success(let s): settings = s
                            case .failure(let message): showError(message)
                            }
                        }
                    } label: {
                        Text(p.capitalized)
                            .font(.pipBody(13, weight: .medium))
                            .foregroundStyle(active ? .white : PipTheme.ink)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(active ? PipTheme.accent : PipTheme.ink.opacity(0.06))
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var voiceAndReactionsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionLabel("Voice & reactions")
            VStack(spacing: 0) {
                toggleRow("Voice replies", settings?.voiceReplies ?? true) {
                    Task {
                        switch await client.toggleVoiceReplies() {
                        case .success(let s): settings = s
                        case .failure(let message): showError(message)
                        }
                    }
                }
                Divider().padding(.leading, 0)
                toggleRow("Animated reactions", settings?.animatedReactions ?? true) {
                    Task {
                        switch await client.toggleAnimatedReactions() {
                        case .success(let s): settings = s
                        case .failure(let message): showError(message)
                        }
                    }
                }
                Divider()
                toggleRow("Haptics", settings?.haptics ?? true) {
                    Task {
                        switch await client.toggleHaptics() {
                        case .success(let s): settings = s
                        case .failure(let message): showError(message)
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 16))

            VStack(alignment: .leading, spacing: 10) {
                Text("Speaking speed").font(.pipBody(14)).foregroundStyle(PipTheme.ink)
                HStack {
                    Text("slow").font(.pipBody(12)).foregroundStyle(PipTheme.ink.opacity(0.45))
                    Slider(
                        value: Binding(
                            get: { Double(settings?.speakingSpeed ?? 50) },
                            set: { newValue in settings = settings.map { current in
                                UserSettingsDto(
                                    buddy: current.buddy, personality: current.personality,
                                    voiceReplies: current.voiceReplies, animatedReactions: current.animatedReactions,
                                    haptics: current.haptics, speakingSpeed: Int32(newValue), theme: current.theme,
                                    brandColor: current.brandColor, storeConversations: current.storeConversations
                                )
                            } }
                        ),
                        in: 0...100,
                        onEditingChanged: { editing in
                            guard !editing, let speed = settings?.speakingSpeed else { return }
                            Task {
                                switch await client.setSpeakingSpeed(speed) {
                                case .success(let s): settings = s
                                case .failure(let message): showError(message)
                                }
                            }
                        }
                    )
                    Text("fast").font(.pipBody(12)).foregroundStyle(PipTheme.ink.opacity(0.45))
                }
            }
            .padding(16)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
    }

    private var appearanceCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionLabel("Appearance")
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Theme").font(.pipBody(15)).foregroundStyle(PipTheme.ink)
                    Spacer()
                    Text("System Default").font(.pipBody(14)).foregroundStyle(PipTheme.ink.opacity(0.5))
                }
                VStack(alignment: .leading, spacing: 10) {
                    Text("Brand color").font(.pipBody(15)).foregroundStyle(PipTheme.ink)
                    HStack(spacing: 10) {
                        ForEach(brandColorOptions, id: \.hex) { option in
                            Button {
                                Task {
                                    switch await client.setBrandColor(option.hex) {
                                    case .success(let s): settings = s
                                    case .failure(let message): showError(message)
                                    }
                                }
                            } label: {
                                VStack(spacing: 4) {
                                    ZStack {
                                        Circle().fill(colorFromHex(option.hex)).frame(width: 28, height: 28)
                                        if option.hex.caseInsensitiveCompare(settings?.brandColor ?? "") == .orderedSame {
                                            Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
                                        }
                                    }
                                    Text(option.label).font(.pipBody(10)).foregroundStyle(PipTheme.ink.opacity(0.6))
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding(16)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
    }

    private var privacyCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionLabel("Privacy")
            VStack {
                toggleRow("Store conversations", settings?.storeConversations ?? true) {
                    Task {
                        switch await client.toggleStoreConversations() {
                        case .success(let s): settings = s
                        case .failure(let message): showError(message)
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
    }

    private func toggleRow(_ label: String, _ isOn: Bool, action: @escaping () -> Void) -> some View {
        HStack {
            Text(label).font(.pipBody(15)).foregroundStyle(PipTheme.ink)
            Spacer()
            Toggle("", isOn: Binding(get: { isOn }, set: { _ in action() }))
                .labelsHidden()
                .tint(PipTheme.accent)
        }
        .padding(.vertical, 12)
    }

    private func showError(_ message: String) {
        errorMessage = message
    }

    private func load() async {
        switch await client.load() {
        case .success(let s): settings = s
        case .failure: break
        }
        isLoading = false
    }
}

#Preview {
    SettingsView(onClose: {}, onOpenAccount: {})
}
