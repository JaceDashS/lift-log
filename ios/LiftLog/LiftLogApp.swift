import SwiftUI

@main
@MainActor
struct LiftLogApp: App {
    @StateObject private var store = WorkoutStore()

    var body: some Scene {
        WindowGroup {
            HomeView()
                .environmentObject(store)
                .preferredColorScheme(.dark)
                .tint(LiftTheme.accent)
        }
    }
}

enum LiftTheme {
    static let background = Color(red: 0.04, green: 0.05, blue: 0.06)
    static let surface = Color(red: 0.08, green: 0.09, blue: 0.11)
    static let border = Color(red: 0.18, green: 0.20, blue: 0.23)
    static let accent = Color(red: 0.84, green: 1.00, blue: 0.23)
}

struct LiftButton: ButtonStyle {
    var primary = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(.subheadline, design: .default).weight(.bold))
            .lineLimit(1)
            .minimumScaleFactor(0.65)
            .frame(maxWidth: .infinity, minHeight: 48)
            .padding(.horizontal, 4)
            .foregroundStyle(primary ? Color.black : Color.white)
            .background(primary ? LiftTheme.accent : LiftTheme.surface, in: RoundedRectangle(cornerRadius: 16))
            .overlay {
                RoundedRectangle(cornerRadius: 16).strokeBorder(primary ? .clear : LiftTheme.border)
            }
            .opacity(configuration.isPressed ? 0.68 : 1)
    }
}

@MainActor
struct HomeView: View {
    @EnvironmentObject private var store: WorkoutStore
    @State private var workoutVisible = false

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                Text("LIFT LOG")
                    .font(.system(.largeTitle).weight(.black).italic())
                    .accessibilityAddTraits(.isHeader)
                Button(store.data.draft == nil ? "Start" : "Resume") {
                    workoutVisible = store.update { $0.openWorkout() }
                }
                .buttonStyle(LiftButton(primary: true))
                HStack(spacing: 10) {
                    NavigationLink("Records") { RecordsView() }
                    NavigationLink("Settings") { ExerciseListView() }
                }
                .buttonStyle(LiftButton())
                if let error = store.storageError {
                    Text(error).font(.footnote).foregroundStyle(.red)
                }
                Spacer()
            }
            .padding(20)
            .frame(maxWidth: 540)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(LiftTheme.background.ignoresSafeArea())
        }
        .fullScreenCover(isPresented: $workoutVisible) {
            WorkoutView()
        }
    }
}

@MainActor
struct ExerciseListView: View {
    @EnvironmentObject private var store: WorkoutStore
    var selecting: ((String?) -> Void)?
    var selected: String?
    @State private var adding = false
    @State private var showingFeedback = false
    @State private var newExercise = ""

    var body: some View {
        List {
            if let selecting {
                Button {
                    selecting(nil)
                } label: {
                    exerciseRow("선택 안 함", isSelected: selected == nil)
                }
            }
            ForEach(store.data.exercises, id: \.self) { exercise in
                if let selecting {
                    Button {
                        selecting(exercise)
                    } label: {
                        exerciseRow(exercise, isSelected: selected == exercise)
                    }
                } else {
                    Text(exercise)
                }
            }
            Button {
                newExercise = ""
                adding = true
            } label: { Label("Add exercise", systemImage: "plus") }
            if selecting == nil {
                Section("Lift Log") {
                    Link("About Developer", destination: URL(string: "https://jace-s.com/about")!)
                    Button("Feedback") { showingFeedback = true }
                }
            }
            if let error = store.storageError {
                Text(error).foregroundStyle(.red)
            }
        }
        .scrollContentBackground(.hidden)
        .background(LiftTheme.background.ignoresSafeArea())
        .navigationTitle(selecting == nil ? "Settings" : "Choose exercise")
        .sheet(isPresented: $showingFeedback) { FeedbackSheet() }
        .alert("Add exercise", isPresented: $adding) {
            TextField("Exercise name", text: $newExercise)
                .textInputAutocapitalization(.words)
            Button("Add") {
                let name = newExercise.trimmingCharacters(in: .whitespacesAndNewlines)
                if store.update({ try $0.addExercise(name) }),
                   let savedName = store.data.exercises.first(where: { $0.caseInsensitiveCompare(name) == .orderedSame }) {
                    selecting?(savedName)
                }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    private func exerciseRow(_ title: String, isSelected: Bool) -> some View {
        HStack {
            Text(title)
            Spacer()
            if isSelected {
                Image(systemName: "checkmark").accessibilityLabel("Selected")
            }
        }
        .contentShape(Rectangle())
    }
}

private struct FeedbackSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var content = ""
    @State private var password = ""
    @State private var sending = false
    @State private var sent = false
    @State private var errorMessage: LocalizedStringKey?

    private var trimmedContent: String { content.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var canSend: Bool {
        !sending && !trimmedContent.isEmpty && !password.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        NavigationStack {
            Form {
                if sent {
                    Text("Thank you. Your feedback has been posted.")
                } else {
                    Text("Your feedback will appear publicly on jace-s.com under Lift Log.")
                    TextField("Your feedback", text: $content, axis: .vertical)
                        .lineLimit(3...8)
                        .disabled(sending)
                    Section("Password for editing or deleting this feedback") {
                        SecureField("Feedback password", text: $password).disabled(sending)
                    }
                    if sending {
                        ProgressView("Sending…")
                    }
                    if let errorMessage {
                        Text(errorMessage).foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Feedback")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(sent ? "Done" : "Cancel") { dismiss() }.disabled(sending)
                }
                if !sent {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Send", action: send).disabled(!canSend)
                    }
                }
            }
        }
        .interactiveDismissDisabled(sending)
    }

    private func send() {
        guard canSend else { return }
        let payload = FeedbackPayload(content: trimmedContent, userPassword: password)
        sending = true
        errorMessage = nil
        Task { @MainActor in
            defer { sending = false }
            do {
                var request = URLRequest(url: URL(string: "https://api.jace-s.com/comments")!, timeoutInterval: 15)
                request.httpMethod = "POST"
                request.httpShouldHandleCookies = false
                request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
                request.httpBody = try JSONEncoder().encode(payload)
                let (_, response) = try await URLSession.shared.data(for: request)
                switch (response as? HTTPURLResponse)?.statusCode {
                case 201:
                    content = ""
                    password = ""
                    sent = true
                case 429:
                    errorMessage = "Too many attempts. Please try again later."
                default:
                    errorMessage = "Could not send feedback. Please try again."
                }
            } catch {
                errorMessage = "Could not send feedback. Please try again."
            }
        }
    }

    private struct FeedbackPayload: Encodable {
        let content: String
        let userPassword: String
        let sourceAppId = 8
    }
}
