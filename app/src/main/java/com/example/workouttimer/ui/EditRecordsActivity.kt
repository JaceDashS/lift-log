package com.example.workouttimer.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.workouttimer.R
import com.example.workouttimer.data.SetInput
import com.example.workouttimer.data.WorkoutSet
import com.example.workouttimer.data.WorkoutStore
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** One row per recorded set. The date belongs to its workout and is shared by its sets. */
class EditRecordsActivity : AppCompatActivity() {
    private enum class Column(val label: Int, val width: Int, val inputType: Int) {
        DATE(R.string.edit_record_date, 112, InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE),
        EXERCISE(R.string.session_exercise, 142, InputType.TYPE_CLASS_TEXT),
        WEIGHT(R.string.weight_entry_hint, 102, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL),
        REPS(R.string.overlay_reps_label, 66, InputType.TYPE_CLASS_NUMBER),
        REST(R.string.edit_record_rest, 88, InputType.TYPE_CLASS_TEXT),
    }

    private data class Row(val date: LocalDate, val set: WorkoutSet)

    private val store by lazy { WorkoutStore.get(this) }
    private lateinit var adapter: SetTableAdapter
    private lateinit var table: HorizontalScrollView
    private lateinit var empty: TextView
    private var test = false
    private var pounds = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        test = store.supportsTestMode && (savedInstanceState?.getBoolean("test") ?: store.testMode)
        pounds = store.weightInPounds
        adapter = SetTableAdapter()

        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }
        screen.addView(TextView(this, null, 0, R.style.LiftText_Headline).apply {
            text = getString(R.string.edit_records) + if (test) getString(R.string.test_suffix) else ""
        })
        screen.addView(TextView(this, null, 0, R.style.LiftText_Note).apply {
            setText(R.string.edit_records_hint)
            setPadding(0, dp(6), 0, dp(12))
        })

        val header = buildRow(header = true)
        val list = ListView(this).apply {
            divider = null
            setItemsCanFocus(true)
            adapter = this@EditRecordsActivity.adapter
        }
        val columns = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header, LinearLayout.LayoutParams(-1, dp(42)))
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        table = HorizontalScrollView(this).apply {
            addView(columns, FrameLayout.LayoutParams(Column.entries.sumOf { dp(it.width) }, -1))
        }
        screen.addView(table, LinearLayout.LayoutParams(-1, 0, 1f))
        empty = TextView(this, null, 0, R.style.LiftText_Note).apply { setText(R.string.empty_records) }
        screen.addView(empty)
        setContentView(screen)
        reload()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("test", test)
        super.onSaveInstanceState(outState)
    }

    private fun reload() {
        adapter.rows = store.sessions(test).asReversed().flatMap { workout ->
            workout.sets.map { Row(workout.date, it) }
        }
        adapter.notifyDataSetChanged()
        val hasRows = adapter.count > 0
        table.visibility = if (hasRows) View.VISIBLE else View.GONE
        empty.visibility = if (hasRows) View.GONE else View.VISIBLE
    }

    private fun buildRow(header: Boolean): LinearLayout = LinearLayout(this).apply {
        for (column in Column.entries) {
            val cell = TextView(this@EditRecordsActivity, null, 0, if (header) R.style.LiftText_Label else R.style.LiftText)
            cell.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            cell.setPadding(dp(8), 0, dp(6), 0)
            cell.maxLines = 1
            cell.ellipsize = TextUtils.TruncateAt.END
            cell.background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(this@EditRecordsActivity, if (header) R.color.lift_surface_high else R.color.lift_surface))
                setStroke(dp(1), ContextCompat.getColor(this@EditRecordsActivity, R.color.lift_line))
            }
            if (header) {
                cell.letterSpacing = 0.04f
                cell.text = heading(column)
            } else {
                val attributes = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                cell.foreground = attributes.getDrawable(0)
                attributes.recycle()
                cell.isFocusable = true
            }
            addView(cell, LinearLayout.LayoutParams(dp(column.width), -1))
        }
    }

    private inner class SetTableAdapter : BaseAdapter() {
        var rows = emptyList<Row>()
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = getItem(position).set.id
        override fun hasStableIds() = true

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = getItem(position)
            val cells = convertView as? LinearLayout ?: buildRow(header = false).apply {
                layoutParams = AbsListView.LayoutParams(-1, dp(48))
            }
            for ((index, column) in Column.entries.withIndex()) {
                val cell = cells.getChildAt(index) as TextView
                val value = display(row, column)
                cell.text = value.ifEmpty { getString(R.string.missing_value) }
                cell.contentDescription = "${heading(column)}, ${cell.text}"
                cell.setOnClickListener { edit(row, column) }
            }
            return cells
        }
    }

    private fun heading(column: Column): String = if (column == Column.WEIGHT) {
        getString(R.string.edit_record_weight, getString(if (pounds) R.string.weight_lb_unit else R.string.weight_kg_unit))
    } else {
        getString(column.label)
    }

    private fun display(row: Row, column: Column): String = when (column) {
        Column.DATE -> row.date.toString()
        Column.EXERCISE -> row.set.exercise
        Column.WEIGHT -> BigDecimal.valueOf(if (pounds) row.set.weightKg / SetInput.KG_PER_POUND else row.set.weightKg)
            .setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        Column.REPS -> row.set.reps?.toString().orEmpty()
        Column.REST -> row.set.restMillis?.let(::duration).orEmpty()
    }

    private fun edit(row: Row, column: Column) {
        val initial = display(row, column)
        val input = EditText(this).apply {
            inputType = column.inputType
            setSingleLine()
            setText(initial)
            when (column) {
                Column.REPS -> setHint(R.string.overlay_reps_optional)
                Column.REST -> setHint(R.string.edit_record_rest_hint)
                else -> Unit
            }
            setSelectAllOnFocus(true)
            requestFocus()
        }
        val wrapper = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(input, FrameLayout.LayoutParams(-1, -2))
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(heading(column))
            .setView(wrapper)
            .setPositiveButton(R.string.save_record_edit, null)
            .setNegativeButton(android.R.string.cancel, null)
        if (column == Column.DATE) builder.setMessage(R.string.edit_record_date_hint)
        val dialog = builder.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entered = input.text.toString().trim()
                try {
                    if (entered != initial && !saveCell(row, column, entered, input)) return@setOnClickListener
                    dialog.dismiss()
                    reload()
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.error_storage, Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    reload()
                }
            }
        }
        dialog.show()
    }

    private fun saveCell(row: Row, column: Column, value: String, input: EditText): Boolean {
        fun invalid(message: Int): Boolean {
            input.error = getString(message)
            return false
        }
        val original = row.set
        val changed = when (column) {
            Column.DATE -> {
                val date = try { LocalDate.parse(value) } catch (_: DateTimeParseException) { return invalid(R.string.error_date) }
                if (date.year !in 1900..2100) return invalid(R.string.error_date)
                store.updateWorkoutDate(original.sessionId, date, test)
                return true
            }
            Column.EXERCISE -> {
                if (!SetInput.validExercise(value)) return invalid(R.string.error_exercise)
                original.copy(exercise = value)
            }
            Column.WEIGHT -> original.copy(weightKg = SetInput.weightKg(value, pounds) ?: return invalid(R.string.error_weight))
            Column.REPS -> {
                val reps = if (value.isEmpty()) null else value.toIntOrNull() ?: return invalid(R.string.error_reps)
                if (reps != null && reps !in 1..SetInput.MAX_REPS) return invalid(R.string.error_reps)
                original.copy(reps = reps)
            }
            Column.REST -> {
                val rest = if (value.isEmpty()) null else SetInput.restMillis(value) ?: return invalid(R.string.error_rest)
                original.copy(restMillis = rest)
            }
        }
        store.updateLoggedSet(original, changed, test)
        return true
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
