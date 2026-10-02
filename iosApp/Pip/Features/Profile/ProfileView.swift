import PipShared
import SwiftUI

/// Adaptive Conversation Engine's Privacy screen (PRD_TDD_pip_Voice_AI.md
/// §23.4): view the learned Interaction Profile, edit fields manually
/// (locks them against the next background recompute), reset, or delete
/// all personalization data.
struct ProfileView: View {
    var onClose: () -> Void

    @State private var client = ProfileClient()
    @State private var profile: ProfileDto?
    @State private var history: [ProfileHistoryEntryDto] = []
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var isBusy = false
    @State private var showResetConfirm = false
    @State private var showDeleteConfirm = false

    private let styleOptions = ["formal", "casual", "direct"]
    private let toneOptions = ["professional", "friendly", "gentle"]
    private let lengthOptions = ["short", "medium", "long"]
    private let technicalOptions = ["beginner", "intermediate", "advanced"]
    private let frequencyOptions = ["jarang", "kadang", "sering"]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    if let errorMessage {
                        Text(errorMessage)
                            .font(.pipBody(13))
                            .foregroundStyle(.red)
                    }

                    if isLoading {
                        ProgressView().frame(maxWidth: .infinity)
                    } else if let profile {
                        summarySection(profile)
                        editSection(profile)
                        if !history.isEmpty {
                            historySection
                        }
                        dangerZone
                    }
                }
                .padding(20)
            }
            .background(PipTheme.cream.ignoresSafeArea())
            .navigationTitle("Personalisasi")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Tutup", action: onClose)
                }
            }
        }
        .task { await load() }
        .alert("Reset profil?", isPresented: $showResetConfirm) {
            Button("Reset", role: .destructive) { Task { await reset() } }
            Button("Batal", role: .cancel) {}
        } message: {
            Text("Semua preferensi yang dipelajari akan kembali kosong. Riwayat percakapan tidak terhapus.")
        }
        .alert("Hapus semua data personalisasi?", isPresented: $showDeleteConfirm) {
            Button("Hapus", role: .destructive) { Task { await delete() } }
            Button("Batal", role: .cancel) {}
        } message: {
            Text("Profil dan seluruh riwayat perubahannya akan dihapus permanen. Tindakan ini tidak bisa dibatalkan.")
        }
    }

    private func summarySection(_ profile: ProfileDto) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Yang Pip pelajari tentangmu")
                .font(.pipDisplay(18, weight: .semibold))
                .foregroundStyle(PipTheme.ink)

            if profile.confidenceScore < 0.1 {
                Text("Belum cukup percakapan untuk mempelajari gaya kamu — makin sering ngobrol, makin Pip mengenalmu.")
                    .font(.pipBody(13))
                    .foregroundStyle(PipTheme.ink.opacity(0.6))
            } else {
                summaryRow("Gaya komunikasi", profile.communicationStyle)
                summaryRow("Nada favorit", profile.preferredTone)
                summaryRow("Panjang jawaban", profile.preferredResponseLength)
                summaryRow("Level teknis", profile.technicalLevel)
                summaryRow("Suka humor", profile.humorPreference)
                summaryRow("Suka emoji", profile.emojiPreference)
                if !profile.favoriteTopics.isEmpty {
                    summaryRow("Topik favorit", profile.favoriteTopics.joined(separator: ", "))
                }
                Text("Tingkat keyakinan: \(Int(profile.confidenceScore * 100))%")
                    .font(.pipBody(12))
                    .foregroundStyle(PipTheme.ink.opacity(0.4))
            }
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private func summaryRow(_ label: String, _ value: String?) -> some View {
        HStack {
            Text(label).font(.pipBody(13)).foregroundStyle(PipTheme.ink.opacity(0.6))
            Spacer()
            Text(value ?? "—").font(.pipBody(13, weight: .medium)).foregroundStyle(PipTheme.ink)
        }
    }

    private func editSection(_ profile: ProfileDto) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Ubah manual")
                .font(.pipDisplay(16, weight: .semibold))
                .foregroundStyle(PipTheme.ink)
            Text("Preferensi yang kamu ubah di sini tidak akan ditimpa otomatis oleh Pip.")
                .font(.pipBody(12))
                .foregroundStyle(PipTheme.ink.opacity(0.5))

            editRow("Gaya komunikasi", options: styleOptions, current: profile.communicationStyle) {
                await update(communicationStyle: $0)
            }
            editRow("Nada", options: toneOptions, current: profile.preferredTone) {
                await update(preferredTone: $0)
            }
            editRow("Panjang jawaban", options: lengthOptions, current: profile.preferredResponseLength) {
                await update(preferredResponseLength: $0)
            }
            editRow("Level teknis", options: technicalOptions, current: profile.technicalLevel) {
                await update(technicalLevel: $0)
            }
            editRow("Frekuensi humor", options: frequencyOptions, current: profile.humorPreference) {
                await update(humorPreference: $0)
            }
            editRow("Frekuensi emoji", options: frequencyOptions, current: profile.emojiPreference) {
                await update(emojiPreference: $0)
            }
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private func editRow(
        _ label: String, options: [String], current: String?, onPick: @escaping (String) async -> Void
    ) -> some View {
        HStack {
            Text(label).font(.pipBody(13)).foregroundStyle(PipTheme.ink)
            Spacer()
            Menu {
                ForEach(options, id: \.self) { option in
                    Button(option) { Task { await onPick(option) } }
                }
            } label: {
                HStack(spacing: 4) {
                    Text(current ?? "Pilih")
                        .font(.pipBody(13, weight: .medium))
                    Image(systemName: "chevron.down")
                        .font(.system(size: 10))
                }
                .foregroundStyle(PipTheme.accent)
            }
            .disabled(isBusy)
        }
    }

    private var historySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Riwayat perubahan")
                .font(.pipDisplay(16, weight: .semibold))
                .foregroundStyle(PipTheme.ink)
            ForEach(Array(history.prefix(10).enumerated()), id: \.offset) { _, entry in
                VStack(alignment: .leading, spacing: 2) {
                    Text([entry.communicationStyle, entry.preferredTone, entry.technicalLevel]
                        .compactMap { $0 }.joined(separator: " · "))
                        .font(.pipBody(12, weight: .medium))
                        .foregroundStyle(PipTheme.ink)
                    Text(entry.recordedAt)
                        .font(.pipBody(11))
                        .foregroundStyle(PipTheme.ink.opacity(0.4))
                }
                .padding(.vertical, 4)
                Divider()
            }
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var dangerZone: some View {
        VStack(spacing: 10) {
            Button(role: .destructive) { showResetConfirm = true } label: {
                Text("Reset profil").frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(isBusy)

            Button(role: .destructive) { showDeleteConfirm = true } label: {
                Text("Hapus semua data personalisasi").frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(.red)
            .disabled(isBusy)
        }
        .padding(.top, 8)
    }

    private func load() async {
        isLoading = true
        switch await client.load() {
        case .success(let p):
            profile = p
            errorMessage = nil
        case .failure(let message):
            errorMessage = message
        }
        history = await client.loadHistory()
        isLoading = false
    }

    private func update(
        communicationStyle: String? = nil, preferredTone: String? = nil,
        preferredResponseLength: String? = nil, technicalLevel: String? = nil,
        humorPreference: String? = nil, emojiPreference: String? = nil
    ) async {
        isBusy = true
        defer { isBusy = false }
        let result = await client.update(
            communicationStyle: communicationStyle, preferredTone: preferredTone,
            preferredResponseLength: preferredResponseLength, technicalLevel: technicalLevel,
            humorPreference: humorPreference, emojiPreference: emojiPreference
        )
        switch result {
        case .success(let p):
            profile = p
            errorMessage = nil
        case .failure(let message):
            errorMessage = message
        }
    }

    private func reset() async {
        isBusy = true
        defer { isBusy = false }
        switch await client.reset() {
        case .success(let p):
            profile = p
            history = await client.loadHistory()
            errorMessage = nil
        case .failure(let message):
            errorMessage = message
        }
    }

    private func delete() async {
        isBusy = true
        defer { isBusy = false }
        if await client.delete() {
            onClose()
        } else {
            errorMessage = "Gagal menghapus data. Coba lagi."
        }
    }
}

#Preview {
    ProfileView(onClose: {})
}
