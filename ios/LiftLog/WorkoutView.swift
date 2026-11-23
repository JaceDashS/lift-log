import SwiftUI

@MainActor
struct WorkoutView: View {
    private enum Editor: String, Identifiable {
        case exercise, weight, startRest
        var id: String { rawValue }
    }

    @EnvironmentObject private var store: WorkoutStore
    @Environment(\.dismiss) private var dismiss
    @State private var editor: Editor?

    var body: some View {
        Group {
            if let draft = store.data.draft {
                VStack(spacing: 12) {
                    TimelineView(.periodic(from: .now, by: 1)) { context in
                        VStack(spacing: 12) {
                            HStack(spacing: 8) {
                                metric("Sets", value: totalSetCount(for: draft).formatted())
                                metric("Time", value: timerText(draft.elapsedTime(at: context.date)))
                                Spacer(minLength: 0)
                                Button(draft.phase == .lifting ? "REST" : "PUSH") {
                                    advance(draft)
                                }
                                .buttonStyle(LiftButton(primary: draft.phase == .lifting))
                                .frame(maxWidth: 120)
                                .accessibilityIdentifier("workout.phase")
                            }
                            Spacer()
                            VStack(spacing: 6) {
                                Text(phaseLabel(draft.phase))
                                    .font(.headline)
                                    .foregroundStyle(.secondary)
                                Text(draft.phase == .lifting ? "GO" : timerText(draft.restTime(at: context.date)))
                                    .font(.system(size: 96, weight: .heavy, design: .monospaced))
                                    .minimumScaleFactor(0.4)
                                    .lineLimit(1)
                                    .foregroundStyle(draft.phase == .resting ? LiftTheme.accent : .white)
                                    .accessibilityIdentifier("workout.timer")
                                if draft.isExerciseTransition {
                                    Text("Rest time is recorded only between consecutive sets of the same exercise.")
                                        .font(.footnote)
                                        .foregroundStyle(.secondary)
                                        .multilineTextAlignment(.center)
                                }
                            }
                            .allowsHitTesting(false)
                            Spacer()
                        }
                    }
                    if let error = store.storageError {
                        Text(error).font(.footnote).foregroundStyle(.red)
                    }
                    HStack(spacing: 7) {
                        Button(draft.exercise ?? "Exercise") { editor = .exercise }
                            .accessibilityIdentifier("workout.exercise")
                        Button(weightLabel(draft)) { editor = .weight }
                            .disabled(draft.exercise == nil)
                            .opacity(draft.exercise == nil && draft.phase != .lifting ? 0.52 : 1)
                            .accessibilityIdentifier("workout.weight")
                        Button("End") {
                            if store.update({ $0.endWorkout() }) { dismiss() }
                        }
                        .accessibilityIdentifier("workout.end")
                    }
                    .buttonStyle(LiftButton())
                }
                .padding(16)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(LiftTheme.background.ignoresSafeArea())
        .sheet(item: $editor) { item in
            switch item {
            case .exercise:
                NavigationStack {
                    ExerciseListView(selecting: { name in
                        if store.update({ $0.selectExercise(name) }) { editor = nil }
                    }, selected: store.data.draft?.exercise)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Cancel") { editor = nil }
                        }
                    }
                }
            case .weight, .startRest:
                let unit = store.data.weightUnit
                let originalWeight = store.data.draft?.kilograms
                let initialValue = originalWeight.map { unit.display($0) } ?? ""
                let startingRest = item == .startRest
                ValueEditor(
                    startingRest: startingRest,
                    value: initialValue,
                    unit: !startingRest || originalWeight == nil ? unit : nil
                ) { reps, value, selectedUnit in
                    let kilograms = selectedUnit.flatMap { selection in
                        selection == unit && value == initialValue ? originalWeight : selection.parse(value)
                    }
                    if let selectedUnit, kilograms == nil {
                        return "Enter a weight from 0 to \(selectedUnit.display(10_000)) \(selectedUnit.rawValue)."
                    }
                    let saved = store.update { data in
                        if let kilograms, let selectedUnit {
                            data.setLoad(kilograms: kilograms, unit: selectedUnit)
                        }
                        if startingRest {
                            data.setReps(reps)
                            try data.advance(at: .now)
                        }
                    }
                    return saved ? nil : store.storageError
                }
            }
        }
    }

    private func advance(_ draft: WorkoutDraft) {
        if draft.phase == .lifting, draft.exercise != nil {
            editor = .startRest
            return
        }
        store.update { try $0.advance(at: .now) }
    }

    private func totalSetCount(for draft: WorkoutDraft) -> Int {
        store.data.sets.lazy.filter { $0.workoutID == draft.id }.count + (draft.unloggedSets ?? 0)
    }

    private func weightLabel(_ draft: WorkoutDraft) -> String {
        guard let kilograms = draft.kilograms else { return "Weight" }
        let unit = store.data.weightUnit
        return "\(unit.display(kilograms)) \(unit.rawValue)"
    }

    private func phaseLabel(_ phase: WorkoutPhase) -> String {
        switch phase {
        case .ready: return "Ready"
        case .lifting: return "Set in progress"
        case .resting: return "Rest"
        }
    }

    private func metric(_ name: String, value: String) -> some View {
        VStack(spacing: 3) {
            Text(name).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.subheadline.bold()).monospacedDigit()
        }
        .frame(minWidth: 56, minHeight: 48)
        .padding(.horizontal, 4)
        .background(LiftTheme.surface, in: RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .combine)
    }
}

private struct ValueEditor: View {
    private enum Field: Hashable { case reps, weight }

    @Environment(\.dismiss) private var dismiss
    @FocusState private var focusedField: Field?
    @State private var reps = ""
    @State private var value: String
    @State private var unit: WeightUnit?
    @State private var error: String?
    let startingRest: Bool
    let save: (Int?, String, WeightUnit?) -> String?

    init(startingRest: Bool, value: String, unit: WeightUnit?, save: @escaping (Int?, String, WeightUnit?) -> String?) {
        self.startingRest = startingRest
        self.save = save
        _value = State(initialValue: value)
        _unit = State(initialValue: unit)
    }

    var body: some View {
        NavigationStack {
            Form {
                if startingRest {
                    TextField("Reps", text: $reps)
                        .keyboardType(.numberPad)
                        .focused($focusedField, equals: .reps)
                }
                if unit != nil {
                    Picker("Unit", selection: $unit) {
                        ForEach(WeightUnit.allCases) { option in
                            Text(option.rawValue).tag(Optional(option))
                        }
                    }
                    .pickerStyle(.segmented)
                    TextField("Weight", text: $value)
                        .keyboardType(.decimalPad)
                        .focused($focusedField, equals: .weight)
                }
                if let error {
                    Text(error).foregroundStyle(.red)
                }
            }
            .navigationTitle(startingRest ? "Reps" : "Weight")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(startingRest ? "REST" : "Save") {
                        let count = Int(reps.trimmingCharacters(in: .whitespacesAndNewlines))
                        if startingRest, count.map({ (1...WorkoutData.maxReps).contains($0) }) != true {
                            error = "Enter a number from 1 to \(WorkoutData.maxReps)."
                            return
                        }
                        error = save(count, value.trimmingCharacters(in: .whitespacesAndNewlines), unit)
                        if error == nil { dismiss() }
                    }
                }
            }
            .onAppear { focusedField = startingRest ? .reps : .weight }
        }
    }
}
