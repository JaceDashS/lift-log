package com.example.workouttimer.ui

import android.content.Context
import android.graphics.PixelFormat
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.example.workouttimer.R
import com.example.workouttimer.data.SessionDraft
import com.example.workouttimer.data.SetInput
import com.example.workouttimer.data.WorkoutStore
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

/** Keeps the input buttons in their own windows over the touch-through timer. */
internal class OverlaySetInputs(
    context: Context,
    private val windows: WindowManager,
    private val timerLayer: View,
    private val store: WorkoutStore,
    private val test: Boolean,
    private val changeDraft: (SessionDraft, SessionDraft) -> Boolean,
    private val openSettings: () -> Unit,
) {
    private val theme = ContextThemeWrapper(context, R.style.Theme_LiftLog)
    private val exercise = compactButton(theme.getString(R.string.session_exercise))
    private val weight = compactButton(theme.getString(R.string.weight_entry_hint))
    private val windowSources = mapOf(
        exercise to timerLayer.findViewById<View>(R.id.workout_exercise),
        weight to timerLayer.findViewById<View>(R.id.workout_weight),
    )
    private val windowParams = mutableMapOf<View, WindowManager.LayoutParams>()
    private var opacity = store.buttonOpacity.value / 100f
    private var dialog: AlertDialog? = null
    private var draft: SessionDraft? = null
    private val units = listOf(theme.getString(R.string.weight_kg_unit), theme.getString(R.string.weight_lb_unit))

    init {
        exercise.setOnClickListener { chooseExercise() }
        weight.setOnClickListener { editLoad() }
    }

    private fun compactButton(label: String): TextView =
        (LayoutInflater.from(theme).inflate(R.layout.overlay_compact_button, null) as TextView).apply {
            text = label
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

    fun syncPositions() {
        for ((view, source) in windowSources) {
            if (source.width == 0 || source.height == 0) continue
            val existing = windowParams[view]
            val params = existing ?: WindowManager.LayoutParams(
                source.width,
                source.height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.LEFT }
            params.width = source.width
            params.height = source.height
            params.x = source.x.roundToInt()
            params.y = source.y.roundToInt()
            view.scaleX = source.scaleX
            view.scaleY = source.scaleY
            view.rotation = source.rotation
            if (source is TextView) {
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX, source.textSize)
            }
            if (existing == null) {
                windows.addView(view, params)
                windowParams[view] = params
            } else {
                windows.updateViewLayout(view, params)
            }
        }
    }

    fun render(current: SessionDraft) {
        if (draft != current) dialog?.dismiss()
        draft = current
        val chosen = current.exercise.isNotBlank()
        val editable = chosen && !current.setLogged
        exercise.text = current.exercise.ifBlank { theme.getString(R.string.session_exercise) }
        weight.text = current.weight.toDoubleOrNull()?.let {
            val pounds = store.weightInPounds
            theme.getString(R.string.weight_with_unit, displayWeight(it, pounds), units[if (pounds) 1 else 0])
        } ?: theme.getString(R.string.weight_entry_hint)
        weight.contentDescription = weight.text
        exercise.isEnabled = !current.setLogged
        weight.isEnabled = editable
        updateButtonOpacity()
    }

    fun setOpacity(value: Float) {
        opacity = value
        updateButtonOpacity()
        dialog?.let(::styleDialogButtons)
    }

    private fun updateButtonOpacity() {
        for (control in windowSources.keys) {
            control.alpha = opacity * if (control.isEnabled || draft?.pushing == true) 1f else 0.6f
        }
    }

    private fun styleDialogButtons(popup: AlertDialog) {
        popup.getButton(AlertDialog.BUTTON_POSITIVE)?.alpha = opacity
        popup.getButton(AlertDialog.BUTTON_NEGATIVE)?.alpha = opacity
    }

    fun show(visible: Boolean) {
        if (!visible) dialog?.dismiss()
        windowSources.forEach { (overlay, source) ->
            overlay.isVisible = visible
            source.visibility = if (visible) View.INVISIBLE else View.VISIBLE
        }
    }

    fun dispose() {
        dialog?.dismiss()
        windowParams.keys.forEach(windows::removeView)
        windowParams.clear()
    }

    fun chooseExercise() {
        val current = draft ?: return
        if (current.setLogged) return
        val names = store.exercises(test)
        val choices = listOf("") + names
        val labels = listOf(theme.getString(R.string.exercise_selection_none)) + names
        val builder = AlertDialog.Builder(theme)
            .setTitle(R.string.choose_exercise)
            .setNegativeButton(android.R.string.cancel, null)
            .setSingleChoiceItems(labels.toTypedArray(), choices.indexOf(current.exercise)) { picker, index ->
                val name = choices[index]
                if (name == current.exercise || changeDraft(current, current.copy(exercise = name, weight = "", reps = ""))) {
                    picker.dismiss()
                }
            }
        if (names.isEmpty()) {
            builder.setPositiveButton(R.string.home_settings) { _, _ -> openSettings() }
        }
        showDialog(builder.create())
    }

    private fun editLoad() {
        val current = draft ?: return
        if (current.setLogged || current.exercise.isBlank()) return
        val load = LoadInput(current)
        load.row.setPadding(dp(24), dp(8), dp(24), 0)
        val editor = AlertDialog.Builder(theme)
            .setTitle(R.string.weight_entry_hint)
            .setView(load.row)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        editor.setOnShowListener {
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val kg = load.kilograms() ?: return@setOnClickListener
                if (changeDraft(current, current.copy(weight = kg.toString()))) {
                    store.weightInPounds = load.pounds
                    draft?.let(::render)
                    editor.dismiss()
                }
            }
        }
        showDialog(editor)
    }

    private inner class LoadInput(current: SessionDraft) {
        private val initialPounds = store.weightInPounds
        private val originalKg = SetInput.weightKg(current.weight, pounds = false)
        private val initialWeight = originalKg?.let { displayWeight(it, initialPounds) }.orEmpty()
        private val input = EditText(theme).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setHint(R.string.weight_entry_hint)
            setText(initialWeight)
        }
        private val unit = Spinner(theme).apply {
            adapter = ArrayAdapter(theme, android.R.layout.simple_spinner_dropdown_item, units)
            setSelection(if (initialPounds) 1 else 0)
        }
        val pounds get() = unit.selectedItemPosition == 1
        val row = LinearLayout(theme).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(unit)
        }

        fun kilograms(): Double? {
            val enteredWeight = input.text.toString()
            val kg = if (enteredWeight == initialWeight && pounds == initialPounds) originalKg
                else SetInput.weightKg(enteredWeight, pounds)
            if (kg == null) input.error = theme.getString(R.string.error_weight)
            return kg
        }
    }

    fun confirmRest(current: SessionDraft, startRest: (Int, String) -> Boolean) {
        val load = if (SetInput.weightKg(current.weight, pounds = false) == null) LoadInput(current) else null
        val input = EditText(theme).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = if (load == null) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
            setSingleLine()
            setHint(R.string.overlay_reps_label)
            setText(current.reps)
            setSelectAllOnFocus(true)
            requestFocus()
        }
        val form = LinearLayout(theme).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            load?.let { addView(it.row) }
        }
        val editor = AlertDialog.Builder(theme)
            .setTitle(R.string.overlay_reps_label)
            .setView(form)
            .setPositiveButton(R.string.start_rest, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        editor.setOnShowListener {
            val confirm = editor.getButton(AlertDialog.BUTTON_POSITIVE)
            confirm.setOnClickListener {
                val count = SetInput.repCount(input.text.toString().trim())
                if (count == null) {
                    input.error = theme.getString(R.string.overlay_reps_range, SetInput.MAX_REPS)
                    return@setOnClickListener
                }
                val weight = if (load == null) current.weight else {
                    val kilograms = load.kilograms() ?: return@setOnClickListener
                    kilograms.toString()
                }
                if (startRest(count, weight)) {
                    if (load != null) {
                        store.weightInPounds = load.pounds
                        draft?.let(::render)
                    }
                    editor.dismiss()
                }
            }
            input.setOnEditorActionListener { _, action, _ ->
                if (action != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
                confirm.performClick()
                true
            }
        }
        showDialog(editor, showKeyboard = true)
    }

    private fun showDialog(next: AlertDialog, showKeyboard: Boolean = false) {
        dialog?.dismiss()
        dialog = next
        next.window?.apply {
            setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val keyboard = if (showKeyboard) WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE else 0
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or keyboard)
        }
        next.setOnDismissListener { if (dialog === next) dialog = null }
        next.show()
        styleDialogButtons(next)
    }

    private fun displayWeight(kg: Double, pounds: Boolean): String {
        val value = if (pounds) kg / SetInput.KG_PER_POUND else kg
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    companion object {
        fun layoutElements(root: View): Map<String, View> =
            mapOf(
                // Retain the saved positions for the exercise and weight controls.
                "workout_input_group" to root.findViewById(R.id.workout_exercise),
                "workout_weight" to root.findViewById(R.id.workout_weight),
            )
    }

    private fun dp(value: Int): Int = (value * theme.resources.displayMetrics.density + 0.5f).toInt()
}
