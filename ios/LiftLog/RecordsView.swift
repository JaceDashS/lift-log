import SwiftUI

@MainActor
struct RecordsView: View {
    @EnvironmentObject private var store: WorkoutStore
    @AppStorage("recordsSpacing") private var spacing = RecordSpacing.day
    @State private var selectedExercise = ""
    @State private var visibleMetrics = Set(RecordMetric.allCases)
    #if DEBUG
    @State private var sampleSelection: Bool?

    private var sampleMode: Binding<Bool> {
        Binding(
            get: { sampleSelection ?? store.data.sets.isEmpty },
            set: { sampleSelection = $0 }
        )
    }
    #endif

    private var sets: [SavedSet] {
        #if DEBUG
        if sampleMode.wrappedValue { return RecordTrend.sampleSets }
        #endif
        return store.data.sets
    }

    var body: some View {
        let records = sets
        let byExercise = Dictionary(grouping: records, by: \.exercise)
        let exercises = byExercise.keys.sorted { $0.localizedStandardCompare($1) == .orderedAscending }
        let exercise = byExercise[selectedExercise] == nil ? exercises.first ?? "" : selectedExercise

        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                #if DEBUG
                Picker("Record source", selection: sampleMode) {
                    Text("Saved").tag(false)
                    Text("Sample").tag(true)
                }
                .pickerStyle(.segmented)
                #endif
                if let exerciseSets = byExercise[exercise] {
                    chartControls(exercise: exercise, exercises: exercises)
                    TrendChart(
                        trend: RecordTrend(sets: exerciseSets, spacing: spacing),
                        spacing: spacing, visibleMetrics: visibleMetrics, unit: store.data.weightUnit
                    )
                    .id(exerciseSets.first?.id)
                } else {
                    Text("No sets recorded yet.").foregroundStyle(.secondary)
                }
            }
            .padding(16)
            .frame(maxWidth: 640)
            .frame(maxWidth: .infinity)
        }
        .background(LiftTheme.background.ignoresSafeArea())
        .navigationTitle("Records")
    }

    private func chartControls(exercise: String, exercises: [String]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Menu {
                ForEach(exercises, id: \.self) { option in
                    Button {
                        selectedExercise = option
                    } label: {
                        if option == exercise {
                            Label(option, systemImage: "checkmark")
                        } else {
                            Text(option)
                        }
                    }
                }
            } label: {
                Label(exercise, systemImage: "chevron.down")
                    .font(.headline)
            }
            .accessibilityLabel("Exercise, " + exercise)

            Picker("Chart spacing", selection: $spacing) {
                ForEach(RecordSpacing.allCases) { option in
                    Text(option.rawValue).tag(option)
                }
            }
            .pickerStyle(.segmented)

            HStack {
                ForEach(RecordMetric.allCases) { metric in
                    let visible = visibleMetrics.contains(metric)
                    Button {
                        if visible {
                            visibleMetrics.remove(metric)
                        } else {
                            visibleMetrics.insert(metric)
                        }
                    } label: {
                        Label(metric.rawValue, systemImage: visible ? "circle.fill" : "circle")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(visible ? metric.color : Color.secondary)
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .background(LiftTheme.surface, in: RoundedRectangle(cornerRadius: 10))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(visible ? .isSelected : [])
                }
            }
        }
    }
}

private struct TrendChart: View {
    let trend: RecordTrend
    let spacing: RecordSpacing
    let visibleMetrics: Set<RecordMetric>
    let unit: WeightUnit
    @State private var selectedIndex: Int?

    private let plotHeight: CGFloat = 200
    private let inset: CGFloat = 26

    private var index: Int { selectedIndex ?? trend.points.count - 1 }

    private var ticks: [Double] {
        let values = visibleMetrics.flatMap { (trend.changes[$0] ?? []).compactMap { $0 } }
        let bottom = min(0, values.min() ?? 0)
        let top = max(0, values.max() ?? 0)
        let target = max(5, (top - bottom) / 3)
        let magnitude = pow(10, floor(log10(target)))
        let step = ([1.0, 2, 2.5, 5, 10].first { $0 * magnitude >= target } ?? 10) * magnitude
        let lower = floor(bottom / step) * step
        let upper = max(ceil(top / step) * step, lower + step * 2)
        return Array(stride(from: lower, through: upper + step / 2, by: step).reversed())
    }

    var body: some View {
        let grid = ticks
        VStack(alignment: .leading, spacing: 12) {
            Text("Change from baseline")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            HStack(alignment: .top, spacing: 4) {
                VStack(alignment: .trailing, spacing: 0) {
                    ForEach(grid.indices, id: \.self) { index in
                        if index != 0 { Spacer(minLength: 0) }
                        Text((grid[index] / 100).formatted(.percent.precision(.fractionLength(0...1))))
                            .font(.caption2.monospacedDigit())
                            .foregroundStyle(.secondary)
                    }
                }
                .fixedSize(horizontal: true, vertical: false)
                .frame(height: plotHeight + 12)

                GeometryReader { geometry in
                    let span = CGFloat(trend.points.last?.x ?? 0)
                    let minimumWidth = spacing == .day ? span * 19 : span * 64
                    let width = max(geometry.size.width - 1, min(minimumWidth, 12_000) + 2 * inset)
                    ScrollViewReader { reader in
                        ScrollView(.horizontal, showsIndicators: true) {
                            HStack(spacing: 0) {
                                plot(width: width, grid: grid)
                                Color.clear.frame(width: 1).id("latest")
                            }
                        }
                        .onAppear { showLatest(using: reader) }
                        .onChange(of: spacing) { _ in
                            selectedIndex = nil
                            showLatest(using: reader)
                        }
                    }
                }
                .frame(height: plotHeight + 46)
            }
            if trend.points.indices.contains(index) {
                details(for: trend.points[index])
            }
        }
        .padding(12)
        .background(LiftTheme.surface, in: RoundedRectangle(cornerRadius: 16))
    }

    private func showLatest(using reader: ScrollViewProxy) {
        DispatchQueue.main.async { reader.scrollTo("latest", anchor: .trailing) }
    }

    private func plot(width: CGFloat, grid: [Double]) -> some View {
        let span = CGFloat(trend.points.last?.x ?? 0)
        let xPositions = trend.points.map { point in
            span == 0 ? width / 2 : inset + CGFloat(point.x) / span * (width - inset * 2)
        }
        let top = grid[0]
        let bottom = grid[grid.count - 1]
        let y: (Double) -> CGFloat = { value in
            6 + CGFloat((top - value) / (top - bottom)) * plotHeight
        }

        return Canvas { context, _ in
            for tick in grid {
                let line = Path { path in
                    path.move(to: CGPoint(x: 0, y: y(tick)))
                    path.addLine(to: CGPoint(x: width, y: y(tick)))
                }
                context.stroke(line, with: .color(LiftTheme.border), lineWidth: tick == 0 ? 2 : 1)
            }
            if xPositions.indices.contains(index) {
                let guide = Path { path in
                    path.move(to: CGPoint(x: xPositions[index], y: 6))
                    path.addLine(to: CGPoint(x: xPositions[index], y: plotHeight + 6))
                }
                context.stroke(guide, with: .color(.white.opacity(0.45)), style: StrokeStyle(dash: [4, 4]))
            }
            for metric in RecordMetric.allCases where visibleMetrics.contains(metric) {
                var line = Path()
                var hasPrevious = false
                var dots: [(CGPoint, CGFloat)] = []
                for (offset, value) in (trend.changes[metric] ?? []).enumerated() {
                    guard let value else {
                        hasPrevious = false
                        continue
                    }
                    let point = CGPoint(x: xPositions[offset], y: y(value))
                    if hasPrevious {
                        line.addLine(to: point)
                    } else {
                        line.move(to: point)
                    }
                    hasPrevious = true
                    dots.append((point, offset == index ? 5 : 3))
                }
                context.stroke(line, with: .color(metric.color), style: StrokeStyle(lineWidth: 2, lineCap: .round))
                for (point, radius) in dots {
                    let rect = CGRect(x: point.x - radius, y: point.y - radius, width: radius * 2, height: radius * 2)
                    context.fill(Path(ellipseIn: rect), with: .color(metric.color))
                }
            }
            var nextLabelPosition = -CGFloat.infinity
            let lastX = xPositions.last ?? 0
            for (offset, point) in trend.points.enumerated() {
                let x = xPositions[offset]
                guard offset == trend.points.count - 1 || x >= nextLabelPosition && x <= lastX - 55 else { continue }
                context.draw(
                    Text(point.date, format: .dateTime.month(.abbreviated).day())
                        .font(.caption2)
                        .foregroundColor(.secondary),
                    at: CGPoint(x: x, y: plotHeight + 22)
                )
                nextLabelPosition = x + 55
            }
        }
        .frame(width: width, height: plotHeight + 36)
        .contentShape(Rectangle())
        .gesture(SpatialTapGesture().onEnded { tap in
            selectedIndex = xPositions.indices.min {
                abs(xPositions[$0] - tap.location.x) < abs(xPositions[$1] - tap.location.x)
            }
        })
        .accessibilityLabel("Workout trends")
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: selectedIndex = min(index + 1, trend.points.count - 1)
            case .decrement: selectedIndex = max(index - 1, 0)
            @unknown default: break
            }
        }
    }

    private func details(for point: RecordTrend.Point) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(point.date.formatted(spacing == .day ? .dateTime.month().day() : .dateTime.month().day().hour().minute()))
                    .font(.subheadline.weight(.semibold))
                Spacer()
                Text("\(point.sets) sets")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            HStack(alignment: .top) {
                ForEach(RecordMetric.allCases) { metric in
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Avg. " + metric.rawValue)
                            .font(.caption2)
                            .foregroundStyle(metric.color)
                        Text(valueText(point.averages[metric], metric: metric))
                            .font(.subheadline.weight(.semibold).monospacedDigit())
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityElement(children: .combine)
                }
            }
        }
    }

    private func valueText(_ value: Double?, metric: RecordMetric) -> String {
        guard let value else { return "—" }
        switch metric {
        case .weight:
            return unit.fromKilograms(value).formatted(.number.precision(.fractionLength(0...2))) + " " + unit.rawValue
        case .reps:
            return value.formatted(.number.precision(.fractionLength(0...1))) + " reps"
        case .rest:
            return timerText(value.rounded())
        }
    }
}

private extension RecordMetric {
    var color: Color {
        switch self {
        case .weight: return LiftTheme.accent
        case .reps: return Color(red: 0.38, green: 0.72, blue: 1)
        case .rest: return Color(red: 1, green: 0.65, blue: 0.34)
        }
    }
}
