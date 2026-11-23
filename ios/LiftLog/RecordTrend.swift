import Foundation

enum RecordMetric: String, CaseIterable, Identifiable {
    case weight = "Weight"
    case reps = "Reps"
    case rest = "Rest"

    var id: Self { self }
}

enum RecordSpacing: String, CaseIterable, Identifiable {
    case day = "Per day"
    case session = "Per session"

    var id: Self { self }
}

struct RecordTrend {
    struct Point {
        let date: Date
        let x: Double
        let sets: Int
        let averages: [RecordMetric: Double]
    }

    let points: [Point]
    let changes: [RecordMetric: [Double?]]

    init(sets: [SavedSet], spacing: RecordSpacing, calendar: Calendar = .current) {
        let groups: [[SavedSet]]
        switch spacing {
        case .day:
            groups = Array(Dictionary(grouping: sets) { calendar.startOfDay(for: $0.date) }.values)
        case .session:
            groups = Array(Dictionary(grouping: sets, by: \.workoutID).values)
        }

        let datedGroups = groups.compactMap { group in
            group.map(\.date).min().map { (date: $0, sets: group) }
        }.sorted { $0.date < $1.date }
        let firstDay = datedGroups.first.map { calendar.startOfDay(for: $0.date) }
        let trendPoints: [Point] = datedGroups.enumerated().map { index, group in
            let day = calendar.startOfDay(for: group.date)
            let dayIndex = calendar.dateComponents([.day], from: firstDay ?? day, to: day).day ?? 0
            let x = spacing == .day ? dayIndex : index
            let count = Double(group.sets.count)
            var averages: [RecordMetric: Double] = [
                .weight: group.sets.map(\.kilograms).reduce(0, +) / count
            ]
            let reps = group.sets.compactMap(\.reps)
            if !reps.isEmpty {
                averages[.reps] = Double(reps.reduce(0, +)) / Double(reps.count)
            }
            let rests = group.sets.compactMap(\.rest)
            if !rests.isEmpty {
                averages[.rest] = rests.reduce(0, +) / Double(rests.count)
            }
            return Point(date: spacing == .day ? day : group.date, x: Double(x), sets: group.sets.count, averages: averages)
        }
        points = trendPoints
        changes = Dictionary(uniqueKeysWithValues: RecordMetric.allCases.map { metric in
            let values = trendPoints.map { $0.averages[metric] }
            let baseline = values.compactMap { $0 }.first { $0 > 0 }
            return (metric, values.map { value -> Double? in
                guard let value else { return nil }
                guard let baseline else { return 0 }
                let change = (value / baseline - 1) * 100
                return change.isFinite ? change : nil
            })
        })
    }
}

#if DEBUG
extension RecordTrend {
    static let sampleSets: [SavedSet] = {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: .now)
        let bench: [(Int, Double, Int?, TimeInterval)] = [
            (-34, 40, 8, 75), (-30, 42.5, 8, 95), (-23, 42.5, 10, 80),
            (-19, 45, 9, 105), (-13, 47.5, nil, 100), (-10, 45, 11, 85),
            (-4, 50, 9, 115), (-1, 52.5, 10, 90), (-1, 50, 12, 80)
        ]
        let squat: [(Int, Double, Int?, TimeInterval)] = [
            (-32, 55, 8, 95), (-27, 60, 7, 110), (-22, 60, 9, 110),
            (-17, 62.5, 8, 90), (-11, 65, 8, 120), (-6, 67.5, 9, 105), (-2, 70, 8, 110)
        ]
        return [("Bench Press", bench), ("Squat", squat)].flatMap { exercise, sessions in
            sessions.enumerated().flatMap { index, session -> [SavedSet] in
                let (offset, weight, reps, rest) = session
                guard let day = calendar.date(byAdding: .day, value: offset, to: today) else { return [] }
                let start = calendar.date(bySettingHour: 8 + index, minute: 0, second: 0, of: day) ?? day
                let workoutID = UUID()
                return (0..<3).map { set in
                    SavedSet(
                        workoutID: workoutID, exercise: exercise, kilograms: weight - Double(set) * 2.5,
                        unit: .kilograms, reps: reps.map { $0 - set }, rest: set == 0 ? nil : rest + Double(set * 15),
                        date: start.addingTimeInterval(Double(set * 180))
                    )
                }
            }
        }
    }()
}
#endif
