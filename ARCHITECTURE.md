# PipVoice — KMP MVVM Prototype

`index.html` is a high-fidelity, fully interactive mobile prototype for **PipVoice**, a voice AI assistant app. It runs standalone in any browser (open the file directly, or serve the folder) and simulates the exact state machine a Kotlin Multiplatform shared module would drive: every screen observes a `StateFlow<UiState>` exposed by a shared ViewModel, and every user action is an explicit event method on that ViewModel — nothing here is native to Android/iOS; the UI layer just renders whatever the ViewModel emits.

A **Prototype Console** (left panel outside the phone frame) lets you force any screen's ViewModel into `loading` / `error` / `empty` / `success` on demand, instead of waiting on the randomized network simulation (`shared/repository` fails ~14% of calls and adds 500–1100ms latency by default, so retry/error paths surface naturally too).

## Target architecture this prototype represents

```
shared/                                    (Kotlin Multiplatform — business logic, no UI)
  model/         Conversation, Message, VoiceModel, UserProfile, SettingsState
  repository/    ConversationRepository, VoiceRepository, SettingsRepository, UserRepository
  usecase/       GetConversationsUseCase, SendMessageUseCase, GetAiReplyUseCase,
                 SearchConversationsUseCase, TranscribeVoiceUseCase, UpdateSettingsUseCase, ...
  viewmodel/     SplashViewModel, OnboardingViewModel, HomeViewModel, ChatViewModel,
                 VoiceCaptureViewModel, SearchViewModel, SettingsViewModel, ProfileViewModel
                 — each exposes `val state: StateFlow<XUiState>` + public event functions

android/ (Compose)   ui/  → collectAsState() on each ViewModel's StateFlow, no business logic
ios/     (SwiftUI)   ui/  → StateFlow observed via a Swift bridge (e.g. KMP-NativeCoroutines), no business logic
```

Rule enforced throughout: **ViewModels never talk to the network directly** — they call a UseCase, which calls a Repository, which calls the API. UI never calls a UseCase or Repository directly — only its ViewModel's `state` and event methods.

---

## Navigation map

```
Splash ──(first launch)──► Onboarding ──[Get Started]──► Home
       ──(returning user)────────────────────────────────► Home

Home (tab) ──tap conversation──► Chat
Home (tab) ──mic / FAB──► Voice Capture (sheet) ──confirm──► Chat (new conversation)
Home (tab) ──search field──► Search
Search ──tap result──► Chat
Chat ──mic──► Voice Capture (sheet) ──confirm──► appends message to Chat
Profile (tab) ──gear──► Settings
Profile (tab) ──Log out──► dialog ──confirm──► Home (session cleared)

Bottom tabs: Home · Search · Profile   (Chat, Search-from-Home, Settings are pushed screens)
```

---

## 1. Splash

**UI Components:** full-bleed gradient background, app mark, wordmark, tagline, indeterminate spinner.

**User Actions:** none — this screen is not interactive; it resolves automatically.

**Shared ViewModel: `SplashViewModel`**

- **State — `SplashUiState`**
  - `status: Loading | Ready`
  - `firstLaunch: Boolean`
- **Events**
  - `init()` — invoked once on app start
- **Business Flow**
  ```
  SplashViewModel
    ↓
  (checks local session token + onboarding flag — no network call)
  ↓
  NavController.replaceRoot(firstLaunch ? Onboarding : Home)
  ```

**Navigation:** auto-navigates to Onboarding or Home once `status == Ready`. Not reachable by back-navigation.

**Loading State:** the entire screen — spinner over branded gradient.
**Error State:** none by design (local-only check; a real implementation would fall back to Home on any read failure rather than blocking the user).
**Empty State:** n/a.

---

## 2. Onboarding

**UI Components:** swipeable 3-page carousel (illustration, headline, body), page indicator dots, mic-permission disclosure card (final page only), primary CTA, Skip text button.

**User Actions:** swipe/tap dots to change page, tap Continue, tap Skip, tap Get Started (triggers OS mic permission prompt), grant permission.

**Shared ViewModel: `OnboardingViewModel`**

- **State — `OnboardingUiState`**
  - `pageIndex: Int`
  - `micPermission: Undetermined | Requesting | Granted | Denied`
- **Events**
  - `onNext()`
  - `onBack()`
  - `onDotTapped(index)`
  - `onRequestMicPermission()`
  - `onGetStarted()`
- **Business Flow**
  ```
  OnboardingViewModel
    ↓
  onRequestMicPermission() → platform permission API (Android: ActivityResultContracts,
                              iOS: AVAudioSession) via an expect/actual PermissionProvider
    ↓
  onGetStarted() → persists onboarding-complete flag → NavController.replaceRoot(Home)
  ```

**Navigation:** linear forward only; completing it replaces the whole back stack with Home (no back-swipe into Onboarding afterward).

**Loading State:** CTA label switches to "Requesting access…" while `micPermission == Requesting`.
**Error State:** `micPermission == Denied` would show an inline "Open Settings" affordance (mic-optional flows still allow text chat).
**Empty State:** n/a.

---

## 3. Home (Conversations)

**UI Components:** top app bar with mic shortcut, search field (navigates to Search), filter chips (All / Pinned / Voice), pull-to-refresh spinner, conversation list (avatar, pinned flag, title, relative timestamp, preview, overflow menu), skeleton list, error state with Retry, empty state with CTA, infinite-scroll loading footer, floating action button (new voice chat).

**User Actions:** pull to refresh, tap filter chip, tap search field, tap conversation row, long/overflow-tap → delete (with Undo snackbar), scroll to paginate, tap FAB / mic shortcut to start a new voice conversation, tap Retry on error.

**Shared ViewModel: `HomeViewModel`**

- **State — `HomeUiState`**
  - `status: Loading | Success | Error | Empty`
  - `conversations: List<Conversation>`
  - `filter: All | Pinned | Voice`
  - `searchKeyword: String`
  - `page: Int`
  - `hasMore: Boolean`
  - `isRefreshing: Boolean`
  - `isLoadingMore: Boolean`
  - `error: String?`
- **Events**
  - `onFilterSelected(filter)`
  - `onSearchShortcutTapped()`
  - `onConversationTapped(id)`
  - `onDeleteConversation(id)`
  - `onNewVoiceChat()`
  - `refresh()`
  - `loadMore()`
  - `onRetry()`
- **Business Flow**
  ```
  HomeViewModel
    ↓
  GetConversationsUseCase(page, pageSize, query, filter)
    ↓
  ConversationRepository.getConversations(...)
    ↓
  API  (paged conversation list)
  ```
  Delete flow: `HomeViewModel.onDeleteConversation → DeleteConversationUseCase → ConversationRepository.deleteConversation → API`, with an optimistic local removal + Undo snackbar that re-inserts on tap.

**Navigation:** tab root. Pushes Chat (on row tap or new-voice-chat confirm) and Search (on search field tap).

**Loading State:** shimmering skeleton rows (initial load); a top spinner during pull-to-refresh; a footer spinner during pagination — these are independent flags so refresh/paginate never blank the existing list.

**Error State:** full-screen error illustration + message + Retry button on initial-load failure; refresh/pagination failures instead surface a Snackbar with Retry so the existing list stays visible.

**Empty State:** "No conversations yet" with a "Start talking" CTA when there's no data at all; a distinct "No matching conversations" + "Clear filters" message when a filter/search produced zero results.

---

## 4. Chat

**UI Components:** app bar (back, title, online indicator, overflow), message list (user bubbles right-aligned, assistant bubbles left-aligned with avatar), voice-note bubble (waveform + duration), per-assistant-message TTS play/pause + copy buttons, animated typing indicator, inline error banner with Retry, text composer with mic button and send button.

**User Actions:** type + send text, tap mic to open Voice Capture, tap play/pause on an assistant message (simulated TTS), tap copy, tap Retry on a failed AI reply, tap back.

**Shared ViewModel: `ChatViewModel`**

- **State — `ChatUiState`**
  - `status: Loading | Success | Error | Empty`
  - `conversationId: String?`
  - `title: String`
  - `messages: List<Message>`
  - `isAiTyping: Boolean`
  - `inputText: String`
  - `playingMessageId: String?`
  - `error: String?`
- **Events**
  - `onInputChanged(text)`
  - `onSend()`
  - `onMicTapped()`
  - `onVoiceMessageReady(transcript)`
  - `onTogglePlayback(messageId)`
  - `onCopyMessage(text)`
  - `onRetryLastMessage()`
  - `onDismissError()`
  - `onRetry()`
- **Business Flow**
  ```
  ChatViewModel
    ↓
  open(conversationId) → GetMessagesUseCase → ConversationRepository.getMessages → API
  onSend() → SendMessageUseCase → ConversationRepository.sendMessage → API   (optimistic user bubble)
           → GetAiReplyUseCase   → ConversationRepository.getAiReply  → API   (isAiTyping = true meanwhile)
  onMicTapped() → delegates capture to VoiceCaptureViewModel; on confirm,
                  ChatViewModel.onVoiceMessageReady(transcript) re-enters the onSend() flow
  ```

**Navigation:** pushed from Home (existing or newly-created conversation) or from Search results; back returns to whichever screen pushed it.

**Loading State:** skeleton message bubbles while the thread loads; a three-dot typing indicator while waiting on the AI reply (independent of the thread-load state, so composer stays usable).

**Error State:** full-screen error + Retry when the thread itself fails to load; a non-blocking banner ("Pip couldn't respond… Retry") when only the AI reply call fails, leaving prior messages and the composer intact.

**Empty State:** "Say hello to Pip" placeholder for a brand-new conversation with zero messages.

---

## 5. Voice Capture (bottom sheet)

Presented modally over Home or Chat — same shared `VoiceCaptureViewModel` regardless of caller, distinguished by `mode`.

**UI Components:** scrim, draggable sheet, state label ("Listening…" / "Processing…" / "Couldn't hear that"), live elapsed timer, animated amplitude waveform (28 bars updating live), live partial transcript, Cancel / Stop / Confirm circular action buttons, retry-mic action on failure.

**User Actions:** tap Stop to finish capture and auto-send, tap Confirm to send immediately, tap Cancel or tap the scrim to dismiss, tap the mic-retry action after a capture failure.

**Shared ViewModel: `VoiceCaptureViewModel`**

- **State — `VoiceCaptureUiState`**
  - `isOpen: Boolean`
  - `phase: Listening | Processing | Error`
  - `transcript: String`
  - `elapsedSec: Int`
  - `amplitude: List<Float>` (waveform samples)
  - `mode: New | Append`
- **Events**
  - `open(mode)`
  - `onStop()`
  - `onConfirm()`
  - `onCancel()`
  - `onRetryCapture()`
- **Business Flow**
  ```
  VoiceCaptureViewModel
    ↓
  TranscribeVoiceUseCase(onPartialResult)
    ↓
  VoiceRepository.transcribeStream(...)     (mic audio → streaming partial transcripts)
    ↓
  On-device / API speech-to-text engine
    ↓
  onConfirm():
    mode == New    → creates a Conversation locally, NavController.push(Chat, newConversationId)
    mode == Append → ChatViewModel.onVoiceMessageReady(transcript) → Chat's send flow
  ```

**Navigation:** modal — does not participate in the back stack; dismissing it returns focus to whatever screen presented it (`onCancel` always available except mid-`Processing`).

**Loading State:** `phase == Processing` — spinner replaces the waveform while the transcript is being finalized/sent.

**Error State:** `phase == Error` — mic/transcription failure with a one-tap retry that restarts capture without leaving the sheet.

**Empty State:** transcript area shows a muted placeholder prompt ("Say something like…") until the first partial transcript arrives.

---

## 6. Search

**UI Components:** search input with clear button, type filter chips (All / Text / Voice), recent-searches chip list (idle state), skeleton result rows, result list (title + highlighted snippet + kind icon + relative time), error state with Retry, empty state.

**User Actions:** type a query (debounced), tap a type filter, tap a recent search chip, clear the query, tap a result to open its conversation, tap Retry on failure.

**Shared ViewModel: `SearchViewModel`**

- **State — `SearchUiState`**
  - `status: Idle | Loading | Success | Error | Empty`
  - `query: String`
  - `type: All | Text | Voice`
  - `results: List<SearchResult>`
  - `recent: List<String>`
  - `error: String?`
- **Events**
  - `onQueryChanged(text)` — debounced (320ms) before triggering search
  - `onTypeFilterChanged(type)`
  - `onRecentTapped(term)`
  - `onClear()`
  - `onResultTapped(conversationId)`
  - `onRetry()`
- **Business Flow**
  ```
  SearchViewModel
    ↓
  SearchConversationsUseCase(query, type)
    ↓
  ConversationRepository.searchAll(query, type)
    ↓
  API  (full-text search across conversations + messages)
  ```

**Navigation:** pushed from Home's search field; pushes Chat on result tap; back returns to Home.

**Loading State:** skeleton result rows shown per keystroke (post-debounce) while a search is in flight.

**Error State:** "Search failed" + Retry, preserving the current query so Retry re-issues the same search.

**Empty State:** "No results for '<query>'" once a completed search returns zero matches; a separate `Idle` state (not technically empty) shows recent-search suggestions before any query is typed.

---

## 7. Profile

**UI Components:** gradient identity card (avatar initials, name, email, plan badge), voice-minutes usage meter, grouped menu (Settings, Notifications, Privacy & Data), destructive Log out row, skeleton identity card, error state with Retry.

**User Actions:** tap Settings (push), tap Notifications / Privacy (snackbar placeholder in this prototype), tap Log out (confirmation dialog → snackbar + session reset), tap Retry on load failure.

**Shared ViewModel: `ProfileViewModel`**

- **State — `ProfileUiState`**
  - `status: Loading | Success | Error`
  - `profile: UserProfile?` (`name`, `email`, `plan`, `usage { minutesUsed, minutesLimit }`)
  - `error: String?`
- **Events**
  - `onSettingsTapped()`
  - `onLogoutTapped()`
  - `onRetry()`
- **Business Flow**
  ```
  ProfileViewModel
    ↓
  GetUserProfileUseCase → UserRepository.getProfile → API
  onLogoutTapped() → confirmation dialog → LogoutUseCase → UserRepository.logout → API
                    → clears local session → NavController back to signed-out state
  ```

**Navigation:** tab root. Pushes Settings.

**Loading State:** skeleton identity card while `status == Loading`.
**Error State:** full-screen error + Retry if the profile fails to load.
**Empty State:** n/a — a signed-in user always has a profile; an unauthenticated state would instead route to an auth flow (out of scope for this prototype).

---

## 8. Settings

**UI Components:** assistant-voice picker (4 voices, radio-style cards with inline sample playback), language row (opens picker dialog), toggle rows (auto-play responses, wake word, save history), theme selector (Light / Dark / System), skeleton state, error state with Retry.

**User Actions:** select a voice, play a voice sample, change language, toggle any switch, pick a theme, tap back.

**Shared ViewModel: `SettingsViewModel`**

- **State — `SettingsUiState`**
  - `status: Loading | Success | Error`
  - `settings: SettingsState?` (`selectedVoiceId`, `language`, `theme`, `autoPlayResponses`, `wakeWordEnabled`, `saveHistory`)
  - `savingKey: String?` (which field is mid-save, for per-row optimistic feedback)
  - `error: String?`
- **Events**
  - `onVoiceSelected(voiceId)`
  - `onPlaySample(voiceId)`
  - `onLanguageSelected(language)`
  - `onThemeSelected(theme)`
  - `onAutoPlayToggled()`
  - `onWakeWordToggled()`
  - `onSaveHistoryToggled()`
  - `onRetry()`
- **Business Flow**
  ```
  SettingsViewModel
    ↓
  GetSettingsUseCase → SettingsRepository.getSettings → API
  onXTapped() → UpdateSettingsUseCase(patch) → SettingsRepository.updateSettings → API
              (optimistic local update; reverts + Snackbar on failure)
  ```

**Navigation:** pushed from Profile; back returns to Profile.

**Loading State:** skeleton rows while `status == Loading`.
**Error State:** full-screen error + Retry on initial load failure; a per-row optimistic update that fails instead reverts silently to the prior value with a Snackbar ("Couldn't save setting — reverted") rather than blocking the screen.
**Empty State:** n/a — settings always have defaults.

---

## Interaction coverage checklist

| Interaction | Where |
|---|---|
| Loading (skeleton) | Home, Chat, Search, Settings, Profile |
| Success | all screens |
| Failure + inline error | Home, Chat, Search, Settings, Profile, Voice Capture |
| Retry | Home, Chat, Search, Settings, Profile, Voice Capture |
| Pull-to-refresh | Home |
| Pagination (infinite scroll) | Home |
| Search + debounce | Search, Home→Search shortcut |
| Filtering (chips) | Home (All/Pinned/Voice), Search (All/Text/Voice) |
| Bottom sheet | Voice Capture |
| Dialog | Delete conversation (Home), Logout confirm (Profile), Language picker (Settings) |
| Snackbar (+ Undo) | Delete conversation, copy message, settings save failure, logout confirmation |
| Optimistic update + rollback | Chat send, Settings toggles |
| Empty state | Home, Chat, Search |
