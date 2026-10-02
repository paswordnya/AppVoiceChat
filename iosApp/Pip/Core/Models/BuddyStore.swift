import Combine
import SwiftUI

/// Shared selected-buddy state so Main/Voice/Keyboard views stay in sync
/// when the user switches mascots.
@MainActor
final class BuddyStore: ObservableObject {
    static let shared = BuddyStore()

    @Published var selected: Buddy = .pip

    func select(_ buddy: Buddy) {
        selected = buddy
    }

    func cycleNext() {
        let all = Buddy.all
        let index = all.firstIndex(of: selected) ?? 0
        selected = all[(index + 1) % all.count]
    }
}
