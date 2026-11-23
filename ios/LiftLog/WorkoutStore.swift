import Foundation
import SwiftUI

@MainActor
final class WorkoutStore: ObservableObject {
    @Published private(set) var data = WorkoutData()
    @Published private(set) var storageError: String?

    private let file: WorkoutFile
    private var didLoad = false

    init(file: WorkoutFile = .init(url: URL.applicationSupportDirectory
        .appendingPathComponent("LiftLog", isDirectory: true)
        .appendingPathComponent("workouts.json"))) {
        self.file = file
        do {
            data = try file.load()
            didLoad = true
        } catch {
            storageError = WorkoutError.storage.localizedDescription
        }
    }

    @discardableResult
    func update(_ edit: (inout WorkoutData) throws -> Void) -> Bool {
        guard didLoad else { return false }
        var next = data
        do {
            try edit(&next)
            try file.save(next)
            data = next
            storageError = nil
            return true
        } catch {
            storageError = (error as? WorkoutError ?? .storage).localizedDescription
            return false
        }
    }
}
