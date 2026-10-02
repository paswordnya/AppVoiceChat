import PipShared
import SwiftUI

private struct ChatMessage: Identifiable {
    let id = UUID()
    let isUser: Bool
    let text: String
    var replyTo: ReplyReference? = nil
    var createdAt: Date = Date()
}

private struct ReplyReference {
    let messageId: UUID
    let senderLabel: String
    let snippet: String
}

private struct Topic {
    let emoji: String
    let label: String
    let prompt: String
}

private let topics: [Topic] = [
    Topic(emoji: "💬", label: "Chat Santai", prompt: "Just want to chat for a bit."),
    Topic(emoji: "📚", label: "Belajar", prompt: "Help me study for my exam."),
    Topic(emoji: "💼", label: "Kerja", prompt: "What meetings do I have today?"),
    Topic(emoji: "💻", label: "Teknologi", prompt: "What's new in tech today?"),
    Topic(emoji: "🛒", label: "Belanja", prompt: "Add milk and eggs to my list."),
    Topic(emoji: "🎬", label: "Hiburan", prompt: "Recommend something to watch."),
    Topic(emoji: "🍔", label: "Kuliner", prompt: "Find a good dinner spot nearby."),
    Topic(emoji: "✈️", label: "Travel", prompt: "Plan a weekend trip."),
    Topic(emoji: "💰", label: "Keuangan", prompt: "How much did I spend this week?"),
    Topic(emoji: "❤️", label: "Kesehatan", prompt: "Remind me to drink water."),
    Topic(emoji: "🛠️", label: "Bantuan & Tutorial", prompt: "Show me how this works."),
    Topic(emoji: "🎨", label: "Kreativitas", prompt: "Give me an idea to sketch."),
]

private let personalities = ["calm", "chatty", "witty", "coach"]

/// Renders the model's Markdown-ish replies (`**bold**`, `* item` bullets,
/// bare URLs) as actual formatting. Bold/italic/`[text](url)` links come
/// from Markdown inline parsing; bare URLs are caught separately since
/// `NSDataDetector` finds them but the Markdown parser doesn't autolink
/// them. SwiftUI's `Text(AttributedString)` opens `.link` runs via the
/// environment's `openURL` automatically, so no extra tap handling is needed.
private func formattedText(_ text: String, linkColor: Color) -> AttributedString {
    let normalized = normalizeBullets(text)
    var attributed = (try? AttributedString(
        markdown: normalized,
        options: AttributedString.MarkdownParsingOptions(interpretedSyntax: .inlineOnlyPreservingWhitespace)
    )) ?? AttributedString(normalized)

    guard let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue) else {
        return attributed
    }
    let plain = String(attributed.characters)
    let nsRange = NSRange(plain.startIndex..., in: plain)
    for match in detector.matches(in: plain, range: nsRange) {
        guard let url = match.url,
              let range = Range(match.range, in: plain),
              let attrRange = Range(range, in: attributed),
              attributed[attrRange].link == nil else { continue }
        attributed[attrRange].link = url
        attributed[attrRange].foregroundColor = linkColor
        attributed[attrRange].underlineStyle = .single
    }
    return attributed
}

/// `Text` has no block/list layout, so an inline-only Markdown parse leaves
/// `* item` / `- item` markers as literal characters instead of a bullet.
/// Swap the marker for "•" up front so lists still read as lists.
private func normalizeBullets(_ text: String) -> String {
    text
        .split(separator: "\n", omittingEmptySubsequences: false)
        .map { line -> String in
            if line.hasPrefix("* ") || line.hasPrefix("- ") {
                return "•" + line.dropFirst(1)
            }
            return String(line)
        }
        .joined(separator: "\n")
}

/// The full-screen chat sheet from the moc (`keyboardFullscreen` overlay):
/// presented when the "Tap to start chatting" card is tapped in Keyboard
/// mode. Streams replies from the FastAPI backend over `/ws/chat/{id}`
/// (CH-1, CH-2): a "thinking" indicator while waiting for the first
/// token, then the reply renders in as it streams in.
struct KeyboardSessionView: View {
    @ObservedObject private var buddyStore = BuddyStore.shared
    @StateObject private var socket = ChatSocketClient()
    @State private var messages: [ChatMessage] = []
    @State private var streamingText = ""
    @State private var isThinking = false
    @State private var isStreaming = false
    @State private var draft = ""
    @State private var isVoiceSessionPresented = false
    @State private var replyingTo: ChatMessage?
    @State private var topVisibleMessageId: UUID?
    @State private var showFloatingDate = false
    @State private var hideFloatingDateTask: Task<Void, Never>?
    @State private var showAccount = false
    @State private var showSettings = false
    @State private var showHistory = false
    @State private var showBuddyPopup = false
    @State private var showCategoryPopup = false
    private let settingsClient = SettingsClient()
    @State private var settings: UserSettingsDto?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: { showHistory = true }) {
                    Image(systemName: "line.3.horizontal")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(PipTheme.ink)
                        .frame(width: 36, height: 36)
                        .background(PipTheme.ink.opacity(0.06))
                        .clipShape(Circle())
                }

                Button(action: { showBuddyPopup = true }) {
                    HStack(spacing: 8) {
                        pipIcon
                        VStack(spacing: 4) {
                            Text(buddyStore.selected.name)
                                .font(.pipDisplay(16, weight: .semibold))
                                .foregroundStyle(PipTheme.ink)
                            Text(phaseLabel)
                                .font(.pipBody(12))
                                .foregroundStyle(PipTheme.ink.opacity(0.45))
                        }
                    }
                }
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity)

                Button(action: { showCategoryPopup = true }) {
                    Image(systemName: "square.grid.2x2")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(PipTheme.ink)
                        .frame(width: 36, height: 36)
                        .background(PipTheme.ink.opacity(0.06))
                        .clipShape(Circle())
                }

                Button(action: { showAccount = true }) {
                    Image(systemName: "person.circle")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(PipTheme.ink)
                        .frame(width: 36, height: 36)
                        .background(PipTheme.ink.opacity(0.06))
                        .clipShape(Circle())
                }
            }
            .padding(.top, 20)
            .padding(.horizontal, 18)

            ScrollViewReader { scrollProxy in
                ScrollView(showsIndicators: false) {
                    VStack(spacing: 10) {
                        ForEach(Array(messages.enumerated()), id: \.element.id) { index, message in
                            if shouldShowDateSeparator(before: index) {
                                dateSeparatorView(dateSeparatorLabel(for: message.createdAt))
                            }
                            MessageBubble(
                                message: message,
                                onReply: { replyingTo = $0 },
                                onQuoteTap: { targetId in
                                    withAnimation { scrollProxy.scrollTo(targetId, anchor: .center) }
                                }
                            )
                            .id(message.id)
                        }
                        if isStreaming {
                            streamingBubble
                                .id("streaming")
                        } else if isThinking {
                            thinkingDots
                                .id("thinking")
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .scrollTargetLayout()
                }
                .scrollPosition(id: $topVisibleMessageId, anchor: .top)
                .onScrollPhaseChange { _, newPhase, _ in
                    if newPhase.isScrolling {
                        hideFloatingDateTask?.cancel()
                        showFloatingDate = true
                    } else {
                        scheduleHideFloatingDate()
                    }
                }
                .overlay(alignment: .top) {
                    // Telegram-style floating day indicator: shows the date
                    // of whatever's scrolled to the top while actively
                    // scrolling, fades out ~1s after it settles.
                    if let topVisibleMessageId,
                       let topMessage = messages.first(where: { $0.id == topVisibleMessageId }) {
                        dateSeparatorView(dateSeparatorLabel(for: topMessage.createdAt))
                            .padding(.top, 6)
                            .opacity(showFloatingDate ? 1 : 0)
                            .animation(.easeInOut(duration: 0.25), value: showFloatingDate)
                            .allowsHitTesting(false)
                    }
                }
                .onChange(of: messages.count) {
                    guard let last = messages.last else { return }
                    withAnimation { scrollProxy.scrollTo(last.id, anchor: .bottom) }
                }
                .onChange(of: isThinking) {
                    guard isThinking else { return }
                    withAnimation { scrollProxy.scrollTo("thinking", anchor: .bottom) }
                }
                .onChange(of: streamingText) {
                    withAnimation { scrollProxy.scrollTo("streaming", anchor: .bottom) }
                }
            }

            composer
        }
        .background(PipTheme.cream.ignoresSafeArea())
        .onAppear(perform: start)
        .onDisappear { socket.disconnect() }
        .task { await loadSettings() }
        .fullScreenCover(isPresented: $isVoiceSessionPresented) {
            VoiceSessionView {
                isVoiceSessionPresented = false
                Task { await loadHistoryThenGreetIfNeeded() }
            }
        }
        .sheet(isPresented: $showAccount) {
            AccountView(onClose: { showAccount = false }, onOpenSettings: { showAccount = false; showSettings = true })
        }
        .sheet(isPresented: $showSettings) {
            SettingsView(onClose: { showSettings = false }, onOpenAccount: { showSettings = false; showAccount = true })
        }
        .sheet(isPresented: $showHistory) {
            HistoryView(onClose: { showHistory = false })
        }
        .sheet(isPresented: $showBuddyPopup) {
            buddyPersonalityPopup
        }
        .sheet(isPresented: $showCategoryPopup) {
            categoryPopup
        }
    }

    private var buddyPersonalityPopup: some View {
        VStack(spacing: 20) {
            VStack(spacing: 8) {
                ZStack {
                    BlobShape()
                        .fill(
                            LinearGradient(
                                colors: [buddyStore.selected.colorFrom, buddyStore.selected.colorTo],
                                startPoint: .topLeading, endPoint: .bottomTrailing
                            )
                        )
                    PipBlobFace()
                }
                .frame(width: 56, height: 56)
                Text(buddyStore.selected.name).font(.pipDisplay(18, weight: .semibold)).foregroundStyle(PipTheme.ink)
            }

            VStack(alignment: .leading, spacing: 10) {
                Text("SWITCH BUDDY").font(.pipBody(12, weight: .semibold)).foregroundStyle(PipTheme.ink.opacity(0.45))
                HStack {
                    ForEach(Buddy.all) { buddy in
                        Button {
                            buddyStore.select(buddy)
                            Task { _ = await settingsClient.setBuddy(buddy.id) }
                        } label: {
                            VStack(spacing: 4) {
                                Circle()
                                    .fill(
                                        LinearGradient(
                                            colors: [buddy.colorFrom, buddy.colorTo],
                                            startPoint: .topLeading, endPoint: .bottomTrailing
                                        )
                                    )
                                    .frame(width: 44, height: 44)
                                Text(buddy.name)
                                    .font(.pipBody(11, weight: buddy.id == buddyStore.selected.id ? .semibold : .regular))
                                    .foregroundStyle(PipTheme.ink)
                            }
                        }
                        .buttonStyle(.plain)
                        .frame(maxWidth: .infinity)
                    }
                }
            }

            VStack(alignment: .leading, spacing: 10) {
                Text("PERSONALITY").font(.pipBody(12, weight: .semibold)).foregroundStyle(PipTheme.ink.opacity(0.45))
                HStack(spacing: 8) {
                    ForEach(personalities, id: \.self) { p in
                        let active = p == settings?.personality
                        Button {
                            Task {
                                if case .success(let s) = await settingsClient.setPersonality(p) { settings = s }
                            }
                        } label: {
                            Text(p.capitalized)
                                .font(.pipBody(12, weight: .medium))
                                .foregroundStyle(active ? .white : PipTheme.ink)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 7)
                                .background(active ? PipTheme.accent : PipTheme.ink.opacity(0.06))
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(20)
        .background(PipTheme.cream)
        .presentationDetents([.medium])
    }

    private var categoryPopup: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Choose a topic").font(.pipDisplay(18, weight: .semibold)).foregroundStyle(PipTheme.ink)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 4), spacing: 10) {
                ForEach(Array(topics.enumerated()), id: \.offset) { _, topic in
                    Button {
                        draft = topic.prompt
                        showCategoryPopup = false
                    } label: {
                        VStack(spacing: 6) {
                            RoundedRectangle(cornerRadius: 13)
                                .fill(PipTheme.accent.opacity(0.12))
                                .frame(width: 40, height: 40)
                                .overlay(Text(topic.emoji).font(.system(size: 18)))
                            Text(topic.label)
                                .font(.pipBody(10, weight: .medium))
                                .foregroundStyle(PipTheme.ink.opacity(0.7))
                                .multilineTextAlignment(.center)
                                .lineLimit(2)
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(20)
        .background(PipTheme.cream)
        .presentationDetents([.medium])
    }

    private func loadSettings() async {
        if case .success(let s) = await settingsClient.load() { settings = s }
    }

    /// Small static Pip mascot shown at the toolbar's leading edge, so the
    /// chat header reads as "talking to Pip" the same way the voice
    /// session's full blob does.
    private var pipIcon: some View {
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
        .frame(width: 32, height: 32)
        .animation(.easeInOut(duration: 0.25), value: buddyStore.selected)
    }

    private func dateSeparatorView(_ label: String) -> some View {
        Text(label)
            .font(.pipBody(11, weight: .semibold))
            .foregroundStyle(PipTheme.ink.opacity(0.5))
            .padding(.horizontal, 12)
            .padding(.vertical, 5)
            .background(PipTheme.ink.opacity(0.07))
            .clipShape(Capsule())
            .frame(maxWidth: .infinity)
            .padding(.vertical, 4)
    }

    /// The reply/copy affordances only make sense for finished messages —
    /// this one's still streaming in, so it's a plain bubble.
    private var streamingBubble: some View {
        HStack {
            Text(formattedText(streamingText, linkColor: PipTheme.accent))
                .font(.pipBody(14))
                .foregroundStyle(PipTheme.ink)
                .tint(PipTheme.accent)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(PipTheme.ink.opacity(0.06))
                .clipShape(RoundedRectangle(cornerRadius: 16))

            Spacer(minLength: 40)
        }
    }

    private var thinkingDots: some View {
        HStack(spacing: 6) {
            ForEach(0..<3) { i in
                Circle()
                    .fill(PipTheme.accent)
                    .frame(width: 7, height: 7)
                    .opacity(isThinking ? 1 : 0.35)
                    .scaleEffect(isThinking ? 1 : 0.7)
                    .animation(
                        .easeInOut(duration: 0.6).repeatForever(autoreverses: true).delay(Double(i) * 0.15),
                        value: isThinking
                    )
            }
        }
        .frame(height: 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.leading, 4)
    }

    private var composer: some View {
        VStack(spacing: 8) {
            if let replyingTo {
                replyPreviewBanner(replyingTo)
            }

            HStack(spacing: 8) {
                TextField("Message Pip…", text: $draft)
                    .font(.pipBody(14))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 11)
                    .background(PipTheme.ink.opacity(0.06))
                    .clipShape(Capsule())
                    .onSubmit(send)

                Button(action: { isVoiceSessionPresented = true }) {
                    Image(systemName: "mic.fill")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(PipTheme.ink)
                        .frame(width: 38, height: 38)
                        .background(PipTheme.ink.opacity(0.06))
                        .clipShape(Circle())
                }

                Button(action: send) {
                    Image(systemName: "arrow.up")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 38, height: 38)
                        .background(PipTheme.accent)
                        .clipShape(Circle())
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.top, 10)
        .padding(.bottom, 24)
    }

    private func replyPreviewBanner(_ quoted: ChatMessage) -> some View {
        HStack(spacing: 8) {
            Rectangle()
                .fill(PipTheme.accent)
                .frame(width: 3)

            VStack(alignment: .leading, spacing: 1) {
                Text("Membalas \(quoted.isUser ? "diri sendiri" : buddyStore.selected.name)")
                    .font(.pipBody(11, weight: .semibold))
                    .foregroundStyle(PipTheme.accent)
                Text(quoted.text)
                    .font(.pipBody(12))
                    .foregroundStyle(PipTheme.ink.opacity(0.6))
                    .lineLimit(1)
            }

            Spacer(minLength: 0)

            Button(action: { replyingTo = nil }) {
                Image(systemName: "xmark.circle.fill")
                    .foregroundStyle(PipTheme.ink.opacity(0.35))
            }
        }
        // The accent bar `Rectangle` has no intrinsic height, so it (and
        // this whole row) would otherwise expand to fill all the space
        // the composer's VStack offers, shoving the text field/buttons
        // off-screen. `.fixedSize` forces it back down to its natural,
        // content-hugging height.
        .fixedSize(horizontal: false, vertical: true)
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(PipTheme.ink.opacity(0.05))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }

    private var phaseLabel: String {
        if isThinking || isStreaming { return "thinking…" }
        return messages.isEmpty ? "ready when you are" : "here you go"
    }

    private func scheduleHideFloatingDate() {
        hideFloatingDateTask?.cancel()
        hideFloatingDateTask = Task {
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            guard !Task.isCancelled else { return }
            showFloatingDate = false
        }
    }

    /// Telegram-style date divider: "Hari ini" / "Kemarin" for the last two
    /// days, the day name for the rest of the past week, then a full date.
    private func shouldShowDateSeparator(before index: Int) -> Bool {
        guard messages.indices.contains(index) else { return false }
        guard index > 0 else { return true }
        return !Calendar.current.isDate(messages[index].createdAt, inSameDayAs: messages[index - 1].createdAt)
    }

    private func dateSeparatorLabel(for date: Date) -> String {
        let calendar = Calendar.current
        if calendar.isDateInToday(date) { return "Hari ini" }
        if calendar.isDateInYesterday(date) { return "Kemarin" }

        let daysAgo = calendar.dateComponents(
            [.day], from: calendar.startOfDay(for: date), to: calendar.startOfDay(for: Date())
        ).day ?? 0

        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "id_ID")
        formatter.dateFormat = daysAgo < 7 ? "EEEE" : "d MMMM yyyy"
        return formatter.string(from: date).capitalized
    }

    private static let isoDateFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static func parseServerDate(_ raw: String) -> Date {
        isoDateFormatter.date(from: raw) ?? Date()
    }

    private func start() {
        guard messages.isEmpty else { return }
        if ProcessInfo.processInfo.environment["PIP_UI_PREVIEW_LINKS"] == "1" {
            messages = [
                ChatMessage(isUser: true, text: "Link plain nih, bisa diklik gak: https://example.com/promo"),
                ChatMessage(isUser: false, text: "Bisa banget! Cek **panduan lengkap** di https://developer.apple.com ya, atau baca-baca:\n* https://swift.org\n* https://apple.com")
            ]
            return
        }
        socket.connect(onEvent: handle)
        Task { await loadHistoryThenGreetIfNeeded() }
    }

    /// Restores prior turns (CH-4) instead of always starting fresh —
    /// `/start` would wipe server-side history, so only send it the very
    /// first time this session has no messages yet.
    private func loadHistoryThenGreetIfNeeded() async {
        let history = await socket.loadHistory()
        if !history.isEmpty {
            messages = history.map { message in
                let replyTo = message.replySnippet.map { snippet in
                    // No link back to the quoted message's local id survives
                    // a reload — tapping this quote is a no-op instead of
                    // scrolling, but the card itself still renders correctly.
                    ReplyReference(
                        messageId: UUID(),
                        senderLabel: message.replySenderLabel ?? "",
                        snippet: snippet
                    )
                }
                return ChatMessage(
                    isUser: message.isUser,
                    text: message.text,
                    replyTo: replyTo,
                    createdAt: Self.parseServerDate(message.createdAt)
                )
            }
            return
        }
        socket.send(message: "/start")
        isThinking = true
    }

    private func send() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }

        let replyRef = replyingTo.map { quoted in
            ReplyReference(
                messageId: quoted.id,
                senderLabel: quoted.isUser ? "Kamu" : buddyStore.selected.name,
                snippet: String(quoted.text.prefix(80))
            )
        }
        messages.append(ChatMessage(isUser: true, text: text, replyTo: replyRef))
        draft = ""
        isThinking = true

        // Reply metadata rides alongside the message instead of getting
        // baked into its text, so the backend can persist it (CH-6) and
        // the quote card survives a history reload instead of showing up
        // as raw "(membalas ...)" text. Commands stay untouched so
        // `/model gemini` etc. still parse (is_command checks for a
        // literal leading "/").
        if let replyRef, !text.hasPrefix("/") {
            socket.send(message: text, replySenderLabel: replyRef.senderLabel, replySnippet: replyRef.snippet)
        } else {
            socket.send(message: text)
        }
        replyingTo = nil
    }

    private func handle(_ event: ChatSocketClient.Event) {
        switch event {
        case .thinkingStarted:
            isThinking = true
        case .token(let piece):
            isThinking = false
            isStreaming = true
            streamingText += piece
        case .done(let text, _):
            isStreaming = false
            isThinking = false
            if !text.isEmpty {
                messages.append(ChatMessage(isUser: false, text: text))
            }
            streamingText = ""
        case .command(let text):
            isThinking = false
            isStreaming = false
            messages.append(ChatMessage(isUser: false, text: text))
        case .error(let message):
            isThinking = false
            isStreaming = false
            streamingText = ""
            messages.append(ChatMessage(isUser: false, text: "⚠️ \(message)"))
        case .notice(let message):
            messages.append(ChatMessage(isUser: false, text: "ℹ️ \(message)"))
        }
    }
}

/// A chat bubble with Telegram-style interactions: swipe right (or
/// long-press → context menu) to reply, long-press → Copy. Its own view
/// struct (rather than a method on `KeyboardSessionView`) so the drag
/// gesture gets independent `@State` per bubble.
private struct MessageBubble: View {
    let message: ChatMessage
    let onReply: (ChatMessage) -> Void
    let onQuoteTap: (UUID) -> Void

    @State private var dragOffset: CGFloat = 0

    private let replyThreshold: CGFloat = 56

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter
    }()

    var body: some View {
        VStack(alignment: message.isUser ? .trailing : .leading, spacing: 4) {
            if let replyTo = message.replyTo {
                quoteView(replyTo)
            }

            HStack {
                if message.isUser { Spacer(minLength: 40) }

                VStack(alignment: .trailing, spacing: -1) {
                    Text(formattedText(message.text, linkColor: message.isUser ? PipTheme.buddyFrom : PipTheme.accent))
                        .padding(.trailing, message.isUser ? 16 : 0)
                        .font(.pipBody(14))
                        .foregroundStyle(message.isUser ? .white : PipTheme.ink)
                        .tint(message.isUser ? PipTheme.buddyFrom : PipTheme.accent)
                        .multilineTextAlignment(.leading)
                    Text(Self.timeFormatter.string(from: message.createdAt))
                        .font(.pipBody(10))
                        .foregroundStyle((message.isUser ? Color.white : PipTheme.ink).opacity(0.6))
                        .multilineTextAlignment(.leading)
                }
                .padding(.horizontal, 14)
                .padding(.top, 10)
                .padding(.bottom, 8)
                .background(message.isUser ? PipTheme.accent : PipTheme.ink.opacity(0.06))
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .offset(x: dragOffset)
                    .gesture(
                        DragGesture(minimumDistance: 12)
                            .onChanged { value in
                                dragOffset = value.translation.width > 0 ? min(value.translation.width, 72) : 0
                            }
                            .onEnded { _ in
                                if dragOffset > replyThreshold {
                                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                    onReply(message)
                                }
                                withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                    dragOffset = 0
                                }
                            }
                    )
                    .contextMenu {
                        Button {
                            onReply(message)
                        } label: {
                            Label("Balas", systemImage: "arrowshape.turn.up.left")
                        }
                        Button {
                            UIPasteboard.general.string = message.text
                        } label: {
                            Label("Salin", systemImage: "doc.on.doc")
                        }
                    }

                if !message.isUser { Spacer(minLength: 40) }
            }
        }
    }

    @ViewBuilder
    private func quoteView(_ replyTo: ReplyReference) -> some View {
        Button(action: { onQuoteTap(replyTo.messageId) }) {
            HStack(spacing: 6) {
                Rectangle()
                    .fill(PipTheme.accent.opacity(0.6))
                    .frame(width: 3)
                VStack(alignment: .leading, spacing: 1) {
                    Text(replyTo.senderLabel)
                        .font(.pipBody(11, weight: .semibold))
                        .foregroundStyle(PipTheme.accent)
                    Text(replyTo.snippet)
                        .font(.pipBody(11))
                        .foregroundStyle(PipTheme.ink.opacity(0.5))
                        .lineLimit(1)
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(PipTheme.ink.opacity(0.04))
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: 240, alignment: message.isUser ? .trailing : .leading)
        .frame(maxWidth: .infinity, alignment: message.isUser ? .trailing : .leading)
    }
}

#Preview {
    KeyboardSessionView()
}
