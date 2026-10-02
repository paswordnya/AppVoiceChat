import PipShared
import SwiftUI

private struct SettingsLink: Identifiable {
    let id = UUID()
    let label: String
    let implemented: Bool
}

private let settingsLinks: [SettingsLink] = [
    SettingsLink(label: "Personalization", implemented: true),
    SettingsLink(label: "Account Settings", implemented: false),
    SettingsLink(label: "Privacy Settings", implemented: false),
    SettingsLink(label: "Notification Settings", implemented: false),
    SettingsLink(label: "Security Settings", implemented: false),
    SettingsLink(label: "Language Settings", implemented: false),
]

/// The redesign's "Profile" screen — personal account info (email, year of
/// birth; the backend user model has nothing richer yet, so no name/DOB/
/// phone fields like the design mockup) plus a settings-links list. Not to
/// be confused with `ProfileView` ("Personalisasi" in this list), the
/// Adaptive Conversation Engine's learned-preference screen.
struct AccountView: View {
    var onClose: () -> Void
    var onOpenSettings: () -> Void

    private let client = AccountClient()
    @State private var user: AuthUserDto?
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var openPersonalization = false
    @State private var comingSoonLabel: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onClose) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(PipTheme.ink)
                        .frame(width: 36, height: 36)
                        .background(PipTheme.ink.opacity(0.06))
                        .clipShape(Circle())
                }
                Spacer()
                Text("Profile").font(.pipDisplay(18, weight: .semibold)).foregroundStyle(PipTheme.ink)
                Spacer()
                Button(action: onOpenSettings) {
                    Image(systemName: "gearshape")
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

                    if isLoading {
                        ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
                    } else {
                        if let user { avatarHeader(user) }

                        VStack(alignment: .leading, spacing: 10) {
                            Text("PERSONAL INFORMATION")
                                .font(.pipBody(12, weight: .semibold))
                                .foregroundStyle(PipTheme.ink.opacity(0.45))
                            VStack(spacing: 0) {
                                infoRow(icon: "envelope.fill", label: "EMAIL ADDRESS", value: user?.email ?? "—")
                                Divider()
                                infoRow(
                                    icon: "birthday.cake.fill", label: "YEAR OF BIRTH",
                                    value: user?.yearOfBirth.map { "\($0)" } ?? "—"
                                )
                            }
                            .background(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                        }

                        VStack(alignment: .leading, spacing: 10) {
                            Text("SETTINGS")
                                .font(.pipBody(12, weight: .semibold))
                                .foregroundStyle(PipTheme.ink.opacity(0.45))
                            VStack(spacing: 0) {
                                ForEach(Array(settingsLinks.enumerated()), id: \.element.id) { index, link in
                                    linkRow(link.label) {
                                        if link.label == "Personalization" {
                                            openPersonalization = true
                                        } else {
                                            comingSoonLabel = link.label
                                        }
                                    }
                                    if index < settingsLinks.count - 1 { Divider() }
                                }
                            }
                            .background(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                        }
                    }
                }
                .padding(.horizontal, 18)
                .padding(.bottom, 24)
            }
        }
        .background(PipTheme.cream.ignoresSafeArea())
        .task { await load() }
        .sheet(isPresented: $openPersonalization) {
            ProfileView { openPersonalization = false }
        }
        .alert(comingSoonLabel ?? "", isPresented: Binding(get: { comingSoonLabel != nil }, set: { if !$0 { comingSoonLabel = nil } })) {
            Button("Oke") { comingSoonLabel = nil }
        } message: {
            Text("Belum tersedia — menyusul di rilis berikutnya.")
        }
    }

    private func avatarHeader(_ user: AuthUserDto) -> some View {
        VStack(spacing: 8) {
            Circle()
                .fill(PipTheme.accent)
                .frame(width: 72, height: 72)
                .overlay(
                    Text(user.email.first.map { String($0).uppercased() } ?? "?")
                        .font(.pipDisplay(28, weight: .semibold))
                        .foregroundStyle(.white)
                )
            Text(user.email).font(.pipDisplay(18, weight: .semibold)).foregroundStyle(PipTheme.ink)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
    }

    private func infoRow(icon: String, label: String, value: String) -> some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 10)
                .fill(PipTheme.accent.opacity(0.1))
                .frame(width: 36, height: 36)
                .overlay(Image(systemName: icon).font(.system(size: 15)).foregroundStyle(PipTheme.accent))
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.pipBody(11, weight: .semibold)).foregroundStyle(PipTheme.ink.opacity(0.45))
                Text(value).font(.pipBody(15, weight: .medium)).foregroundStyle(PipTheme.ink)
            }
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private func linkRow(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(label).font(.pipBody(15)).foregroundStyle(PipTheme.ink)
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 13)).foregroundStyle(PipTheme.ink.opacity(0.3))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
        }
        .buttonStyle(.plain)
    }

    private func load() async {
        switch await client.load() {
        case .success(let value):
            user = value
            errorMessage = nil
        case .failure(let message):
            errorMessage = message
        }
        isLoading = false
    }
}

#Preview {
    AccountView(onClose: {}, onOpenSettings: {})
}
