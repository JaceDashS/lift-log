package com.example.workouttimer

import android.content.Context
import com.example.workouttimer.constants.AppMagicNumbers
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "overlay_prefs")

data class OverlayState(
    val panelX: Int = AppMagicNumbers.Overlay.DEFAULT_PANEL_X,
    val panelY: Int = AppMagicNumbers.Overlay.DEFAULT_PANEL_Y,
    val alpha: Float = OverlayPreferences.DEFAULT_ALPHA,
)

data class ElementTransform(
    val x: Int?,
    val y: Int?,
    val scaleX: Float,
    val scaleY: Float,
    val rotation: Float,
)

data class WorkoutViewportSize(
    val width: Int,
    val height: Int,
)

class OverlayPreferences(private val context: Context) {

    val state: Flow<OverlayState> = context.dataStore.data.map { prefs ->
        OverlayState(
            panelX = prefs[PANEL_X_KEY] ?: AppMagicNumbers.Overlay.DEFAULT_PANEL_X,
            panelY = prefs[PANEL_Y_KEY] ?: AppMagicNumbers.Overlay.DEFAULT_PANEL_Y,
            alpha = (prefs[ALPHA_KEY] ?: DEFAULT_ALPHA).coerceIn(MIN_ALPHA, MAX_ALPHA)
        )
    }

    suspend fun savePosition(x: Int, y: Int) {
        context.dataStore.edit { prefs ->
            prefs[PANEL_X_KEY] = x
            prefs[PANEL_Y_KEY] = y
        }
    }

    suspend fun saveAlpha(alpha: Float) {
        context.dataStore.edit { prefs ->
            prefs[ALPHA_KEY] = alpha.coerceIn(MIN_ALPHA, MAX_ALPHA)
        }
    }

    suspend fun saveElementPosition(elementId: String, x: Int, y: Int) {
        context.dataStore.edit { prefs ->
            prefs[elementXKey(elementId)] = x
            prefs[elementYKey(elementId)] = y
        }
    }

    suspend fun readElementPosition(elementId: String): Pair<Int, Int>? {
        val prefs = context.dataStore.data.first()
        val x = prefs[elementXKey(elementId)] ?: return null
        val y = prefs[elementYKey(elementId)] ?: return null
        return x to y
    }

    suspend fun saveElementTransform(
        elementId: String,
        x: Int?,
        y: Int?,
        scaleX: Float,
        scaleY: Float,
        rotation: Float,
    ) {
        context.dataStore.edit { prefs ->
            if (x != null) prefs[elementXKey(elementId)] = x
            if (y != null) prefs[elementYKey(elementId)] = y
            prefs[elementScaleXKey(elementId)] = scaleX
            prefs[elementScaleYKey(elementId)] = scaleY
            prefs[elementRotationKey(elementId)] = rotation
        }
    }

    suspend fun saveWorkoutViewportSize(width: Int, height: Int) {
        context.dataStore.edit { prefs ->
            prefs[WORKOUT_VIEWPORT_WIDTH_KEY] = width
            prefs[WORKOUT_VIEWPORT_HEIGHT_KEY] = height
        }
    }

    suspend fun readWorkoutViewportSize(): WorkoutViewportSize? {
        val prefs = context.dataStore.data.first()
        val width = prefs[WORKOUT_VIEWPORT_WIDTH_KEY] ?: return null
        val height = prefs[WORKOUT_VIEWPORT_HEIGHT_KEY] ?: return null
        if (width <= AppMagicNumbers.Common.INT_ZERO || height <= AppMagicNumbers.Common.INT_ZERO) return null
        return WorkoutViewportSize(width = width, height = height)
    }

    suspend fun readElementTransform(elementId: String): ElementTransform {
        val prefs = context.dataStore.data.first()
        return ElementTransform(
            x = prefs[elementXKey(elementId)],
            y = prefs[elementYKey(elementId)],
            scaleX = prefs[elementScaleXKey(elementId)] ?: prefs[elementScaleKey(elementId)] ?: AppMagicNumbers.Interaction.SCALE_DEFAULT,
            scaleY = prefs[elementScaleYKey(elementId)] ?: prefs[elementScaleKey(elementId)] ?: AppMagicNumbers.Interaction.SCALE_DEFAULT,
            rotation = prefs[elementRotationKey(elementId)] ?: AppMagicNumbers.Interaction.NO_ROTATION,
        )
    }

    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.asMap().keys.forEach { key ->
                prefs.remove(key)
            }
        }
    }

    companion object {
        const val MIN_ALPHA = AppMagicNumbers.Alpha.MIN
        const val MAX_ALPHA = AppMagicNumbers.Alpha.MAX
        const val DEFAULT_ALPHA = AppMagicNumbers.Alpha.DEFAULT

        private val PANEL_X_KEY = intPreferencesKey("panel_x")
        private val PANEL_Y_KEY = intPreferencesKey("panel_y")
        private val ALPHA_KEY = floatPreferencesKey("alpha")
        private val WORKOUT_VIEWPORT_WIDTH_KEY = intPreferencesKey("workout_viewport_width")
        private val WORKOUT_VIEWPORT_HEIGHT_KEY = intPreferencesKey("workout_viewport_height")

        private fun elementXKey(elementId: String) = intPreferencesKey("${elementId}_x")
        private fun elementYKey(elementId: String) = intPreferencesKey("${elementId}_y")
        private fun elementScaleXKey(elementId: String) = floatPreferencesKey("${elementId}_scale_x")
        private fun elementScaleYKey(elementId: String) = floatPreferencesKey("${elementId}_scale_y")
        private fun elementScaleKey(elementId: String) = floatPreferencesKey("${elementId}_scale")
        private fun elementRotationKey(elementId: String) = floatPreferencesKey("${elementId}_rotation")
    }
}

