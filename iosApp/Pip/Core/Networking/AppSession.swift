import Foundation

/// One backend session id per install, shared by chat and voice so both
/// modes see the same conversation history/context (PRD CH-4).
enum AppSession {
    static let id: String = {
        let key = "app.session.id"
        let defaults = UserDefaults.standard
        if let existing = defaults.string(forKey: key) {
            return existing
        }
        let fresh = UUID().uuidString
        defaults.set(fresh, forKey: key)
        return fresh
    }()
}
