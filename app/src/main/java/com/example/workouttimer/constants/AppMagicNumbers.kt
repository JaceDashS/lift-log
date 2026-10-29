package com.example.workouttimer.constants

object AppMagicNumbers {

    object Common {
        const val INT_ZERO = 0
        const val INT_ONE = 1
        const val INT_TWO = 2
        const val INT_THREE = 3
        const val INT_FOUR = 4
        const val INT_FIVE = 5
        const val INT_SIX = 6
        const val INT_SEVEN = 7
        const val FLOAT_ZERO = 0f
        const val FLOAT_ONE = 1f
        const val FLOAT_TWO = 2f
        const val LONG_MAX = Long.MAX_VALUE
    }

    object Alpha {
        const val MIN = 0.2f
        const val MAX = 1.0f
        const val DEFAULT = 0.85f
        const val PROGRESS_MAX = 100f
    }

    object Timer {
        const val TICK_MS = 1000L
        const val SECONDS_PER_MINUTE = 60
        const val START_COUNT_INCREMENT = 1
    }

    object Overlay {
        const val DEFAULT_EDGE_MARGIN_DP = 16
        const val DEFAULT_VISUAL_X = 0
        const val DEFAULT_VISUAL_Y = 0
        const val DEFAULT_PANEL_X = 0
        const val DEFAULT_PANEL_Y = 200
    }

    object Interaction {
        const val DIRECTION_NEGATIVE = -1f
        const val DIRECTION_NEUTRAL = 0f
        const val DIRECTION_POSITIVE = 1f
        const val MIN_SCALE = 0.6f
        const val MAX_SCALE = 3.0f
        const val ROTATION_SNAP_THRESHOLD = 10f
        const val MAGNET_SNAP_THRESHOLD_DP = 18f
        const val BOX_PADDING_RATIO = 0.05f
        const val HANDLE_SIZE_DP = 10f
        const val RESIZE_SENSITIVITY = 1.0f
        const val TAP_SLOP_PX = 12f
        const val NON_SELECTED_ALPHA = 0.7f
        const val FULL_ALPHA = 1.0f
        const val NO_ROTATION = 0f
        const val MIN_VIEW_SIZE = 1f
        const val MIN_VIEW_SIZE_INT = 1
        const val SCALE_DEFAULT = 1f
        const val DEGREE_90 = 90f
        const val DEGREE_180 = 180f
        const val DEGREE_270 = 270f
        const val DEGREE_360 = 360f
    }

    object EditSliders {
        const val PROGRESS_MIN = 0
        const val PROGRESS_MAX = 100

        const val BUTTON_FONT_MIN_SP = 10f
        const val BUTTON_FONT_MAX_SP = 28f

        const val BUTTON_SCALE_MIN = 0.8f
        const val BUTTON_SCALE_MAX = 1.8f

        const val SET_FONT_MIN_SP = 12f
        const val SET_FONT_MAX_SP = 40f
    }

    object Notification {
        const val CONTENT_INTENT_REQUEST_CODE = 100
        const val SERVICE_ID = 1001
    }
}
