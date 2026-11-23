import Foundation

enum WeightUnit: String, CaseIterable, Codable, Identifiable {
    case kilograms = "kg"
    case pounds = "lb"

    var id: String { rawValue }
    private static let kilogramsPerPound = 0.45359237

    func toKilograms(_ value: Double) -> Double {
        self == .kilograms ? value : value * Self.kilogramsPerPound
    }

    func fromKilograms(_ value: Double) -> Double {
        self == .kilograms ? value : value / Self.kilogramsPerPound
    }

    func parse(_ text: String) -> Double? {
        let normalized = text.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: ",", with: ".")
        guard let parsed = Double(normalized) else { return nil }
        let kilograms = toKilograms(parsed)
        return (0...10_000).contains(kilograms) ? kilograms : nil
    }

    func display(_ kilograms: Double) -> String {
        fromKilograms(kilograms).formatted(.number.grouping(.never).precision(.fractionLength(0...4)))
    }
}

enum WorkoutPhase: String, Codable {
    case ready
    case lifting
    case resting
}

struct WorkoutDraft: Codable, Identifiable {
    var id = UUID()
    var phase = WorkoutPhase.ready
    var startedAt: Date?
    var restStartedAt: Date?
    var previousRest: TimeInterval = 0
    var exercise: String?
    var kilograms: Double?
    var reps: Int?
    var previousExercise: String?
    var unloggedSets: Int?

    var isExerciseTransition: Bool {
        guard let previousExercise, let exercise else { return false }
        return previousExercise != exercise
    }

    func restTime(at now: Date) -> TimeInterval {
        if phase == .resting, let start = restStartedAt {
            return max(0, now.timeIntervalSince(start))
        }
        return previousRest
    }

    func elapsedTime(at now: Date) -> TimeInterval {
        startedAt.map { max(0, now.timeIntervalSince($0)) } ?? 0
    }
}

struct SavedSet: Codable, Identifiable {
    var id = UUID()
    let workoutID: UUID
    let exercise: String
    let kilograms: Double
    let unit: WeightUnit
    let reps: Int?
    let rest: TimeInterval?
    let date: Date
}

struct WorkoutData: Codable {
    static let maxReps = 10_000

    var exercises: [String] = []
    var weightUnit = WeightUnit.kilograms
    var draft: WorkoutDraft?
    var sets: [SavedSet] = []

    mutating func addExercise(_ input: String) throws {
        let name = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard (1...100).contains(name.count) else { throw WorkoutError.exerciseName }
        if !exercises.contains(where: { $0.caseInsensitiveCompare(name) == .orderedSame }) {
            exercises.append(name)
        }
    }

    mutating func openWorkout() {
        if draft == nil { draft = WorkoutDraft() }
    }

    mutating func selectExercise(_ name: String?) {
        guard draft?.exercise != name else { return }
        draft?.exercise = name
        draft?.kilograms = nil
        draft?.reps = nil
    }

    mutating func setLoad(kilograms: Double, unit: WeightUnit) {
        guard draft?.exercise != nil else { return }
        draft?.kilograms = kilograms
        weightUnit = unit
    }

    mutating func setReps(_ reps: Int?) {
        guard draft?.exercise != nil else { return }
        draft?.reps = reps
    }

    mutating func advance(at now: Date) throws {
        guard var current = draft else { return }
        switch current.phase {
        case .ready, .resting:
            current.previousRest = current.restTime(at: now)
            current.startedAt = current.startedAt ?? now
            current.reps = nil
            current.phase = .lifting
        case .lifting:
            if let exercise = current.exercise {
                guard let reps = current.reps, (1...Self.maxReps).contains(reps) else { throw WorkoutError.repsRequired }
                guard let kilograms = current.kilograms else { throw WorkoutError.weightRequired }
                sets.append(SavedSet(
                    workoutID: current.id,
                    exercise: exercise,
                    kilograms: kilograms,
                    unit: weightUnit,
                    reps: reps,
                    rest: current.previousExercise == exercise ? current.previousRest : nil,
                    date: now
                ))
            } else {
                current.unloggedSets = (current.unloggedSets ?? 0) + 1
            }
            current.previousExercise = current.exercise
            current.phase = .resting
            current.restStartedAt = now
        }
        draft = current
    }

    mutating func endWorkout() {
        draft = nil
    }
}

enum WorkoutError: LocalizedError {
    case exerciseName
    case repsRequired
    case weightRequired
    case storage

    var errorDescription: String? {
        switch self {
        case .exerciseName: return "Enter an exercise name between 1 and 100 characters."
        case .repsRequired: return "Enter reps before starting rest."
        case .weightRequired: return "Enter a weight to save this set."
        case .storage: return "Lift Log couldn't access its local records."
        }
    }
}

struct WorkoutFile {
    let url: URL

    func load() throws -> WorkoutData {
        do {
            return try JSONDecoder().decode(WorkoutData.self, from: Data(contentsOf: url))
        } catch let error as CocoaError where error.code == .fileReadNoSuchFile {
            return WorkoutData()
        }
    }

    func save(_ data: WorkoutData) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(data).write(to: url, options: .atomic)
    }
}

func timerText(_ interval: TimeInterval) -> String {
    let seconds = Int(max(0, interval))
    return String(format: "%ld:%02ld", seconds / 60, seconds % 60)
}
