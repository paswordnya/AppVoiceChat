import PipShared
import SwiftUI

/// The redesign's History panel — search over the current session's real
/// message history (`ChatSessionViewModel.history`, already loaded by
/// `KeyboardSessionView`'s own session start). Not the mockup's fabricated
/// multi-conversation, day-grouped list: the backend only has one active
/// session's scrollback today (`ChatRepository.getHistory()` returns a
/// single `Conversation`), so this searches that real thread instead of
/// inventing conversations that don't exist.
struct HistoryView: View {
    var onClose: () -> Void

    @State private var query = ""
    @State private var messages: [Message] = []

    private var filtered: [Message] {
        guard !query.isEmpty else { return messages }
        return messages.filter { $0.content.localizedCaseInsensitiveContains(query) }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("History").font(.pipDisplay(20, weight: .semibold)).foregroundStyle(PipTheme.ink)
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

            TextField("Search conversation", text: $query)
                .font(.pipBody(14))
                .padding(.horizontal, 16)
                .padding(.vertical, 11)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .padding(.horizontal, 18)

            if filtered.isEmpty {
                Spacer()
                Text(query.isEmpty ? "Belum ada percakapan." : "Tidak ada yang cocok.")
                    .font(.pipBody(13))
                    .foregroundStyle(PipTheme.ink.opacity(0.4))
                Spacer()
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        ForEach(filtered, id: \.id) { message in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(message.role == .user ? "You" : "Pip")
                                    .font(.pipBody(12, weight: .semibold))
                                    .foregroundStyle(PipTheme.accent)
                                Text(message.content)
                                    .font(.pipBody(14, weight: .medium))
                                    .foregroundStyle(PipTheme.ink)
                                    .lineLimit(2)
                                Text(message.createdAt)
                                    .font(.pipBody(11))
                                    .foregroundStyle(PipTheme.ink.opacity(0.4))
                            }
                        }
                    }
                    .padding(18)
                }
            }
        }
        .background(PipTheme.cream.ignoresSafeArea())
        .task { await loadHistory() }
    }

    private func loadHistory() async {
        let viewModel = IOSAccessorsKt.getChatSessionViewModel()
        guard let conversation = viewModel.history.value as? Conversation else { return }
        messages = (conversation.messages as? [Message]) ?? []
    }
}

#Preview {
    HistoryView(onClose: {})
}
