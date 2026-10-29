package com.example.workouttimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.workouttimer.constants.AppMagicNumbers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

class OverlayService : Service() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var overlayPreferences: OverlayPreferences
    private lateinit var windowManager: WindowManager
    private var overlayHostView: View? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private val controlButtonWindows = linkedMapOf<String, ControlButtonWindow>()
    private var timer: CountDownTimer? = null
    private var elapsedSeconds = AppMagicNumbers.Common.INT_ZERO
    private var setCount = AppMagicNumbers.Common.INT_ZERO
    private var totalCount = AppMagicNumbers.Common.INT_ZERO
    private var editMode = false
    private lateinit var workoutRoot: FrameLayout
    private lateinit var timerText: TextView
    private lateinit var setCountText: TextView
    private lateinit var totalCountText: TextView
    private lateinit var startButton: Button
    private lateinit var resetButton: Button
    private lateinit var editButton: Button
    private lateinit var endButton: Button
    private lateinit var doneEditButton: Button
    private lateinit var resetLayoutButton: Button
    private lateinit var alphaSlider: SeekBar
    private lateinit var buttonFontSizeSlider: SeekBar
    private lateinit var buttonSizeSlider: SeekBar
    private lateinit var setFontSizeSlider: SeekBar
    private lateinit var editToolbar: View
    private lateinit var selectionOverlay: FrameLayout
    private lateinit var selectionBorder: View
    private lateinit var snapGuideVertical: View
    private lateinit var snapGuideHorizontal: View
    private lateinit var resizeHandles: Map<Int, ResizeHandle>
    private val editableViews = linkedMapOf<String, View>()
    private val defaultViewStates = linkedMapOf<String, ViewTransformState>()
    private val defaultButtonTints = linkedMapOf<String, ColorStateList?>()
    private val defaultTextColors = linkedMapOf<String, ColorStateList?>()
    private val selectedElementKeys = linkedSetOf<String>()
    private var selectedElementKey: String? = null
    private var longPressSelectionVisuals = false
    private var defaultToolbarX = AppMagicNumbers.Common.FLOAT_ZERO
    private var defaultToolbarY = AppMagicNumbers.Common.FLOAT_ZERO
    private var defaultButtonTextSizePx = AppMagicNumbers.Common.FLOAT_ZERO
    private var defaultSetTextSizePx = AppMagicNumbers.Common.FLOAT_ZERO
    private var rootTapDownX = AppMagicNumbers.Common.FLOAT_ZERO
    private var rootTapDownY = AppMagicNumbers.Common.FLOAT_ZERO

    private fun debugSelectionLog(message: String) {
        SelectionDebugLogger.log(this, "OverlayService", message)
    }

    override fun onCreate() {
        super.onCreate()
        overlayPreferences = OverlayPreferences(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        debugSelectionLog("service start, logFile=${SelectionDebugLogger.logPath(this)}")
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_END_SESSION -> {
                endSession()
                return START_NOT_STICKY
            }
            ACTION_START_TIMER -> {
                startOverlayIfNeeded()
                if (!editMode) startTimer()
            }
            ACTION_RESET_SET -> {
                startOverlayIfNeeded()
                if (!editMode) {
                    setCount = AppMagicNumbers.Common.INT_ZERO
                    updateCounterViews()
                }
            }
            ACTION_ENABLE_EDIT_MODE -> {
                startOverlayIfNeeded()
                toggleEditMode(true)
            }
            ACTION_START_SERVICE, null -> startOverlayIfNeeded()
        }
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun startOverlayIfNeeded() {
        if (overlayHostView != null) return
        startForeground(AppMagicNumbers.Notification.SERVICE_ID, buildNotification())
        val rootView = LayoutInflater.from(this).inflate(R.layout.overlay_workout, null, false)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = AppMagicNumbers.Common.INT_ZERO
            y = AppMagicNumbers.Common.INT_ZERO
        }
        overlayLayoutParams = params
        overlayHostView = rootView
        windowManager.addView(rootView, params)
        initializeWorkoutUi(rootView)
    }
    private fun initializeWorkoutUi(rootView: View) {
        editableViews.clear()
        defaultViewStates.clear()
        defaultButtonTints.clear()
        defaultTextColors.clear()
        selectedElementKeys.clear()
        selectedElementKey = null
        longPressSelectionVisuals = false
        workoutRoot = rootView.findViewById(R.id.workout_root)
        (workoutRoot as? TouchAwareFrameLayout)?.onPostDispatchTouchEvent = { event ->
            if (editMode) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        rootTapDownX = event.x
                        rootTapDownY = event.y
                        debugSelectionLog("postDispatch ACTION_DOWN x=${event.x}, y=${event.y}, selected=$selectedElementKeys, primary=$selectedElementKey")
                        handleRootActionDown(event.x, event.y)
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_POINTER_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            debugSelectionLog("postDispatch ACTION_UP x=${event.x}, y=${event.y}, selected=$selectedElementKeys, primary=$selectedElementKey")
                            handleRootTap(event.x, event.y)
                        }
                        clearLongPressSelectionVisuals()
                    }
                }
            }
        }
        workoutRoot.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        timerText = rootView.findViewById(R.id.text_timer)
        setCountText = rootView.findViewById(R.id.text_set_count)
        totalCountText = rootView.findViewById(R.id.text_total_count)
        startButton = rootView.findViewById(R.id.button_start)
        resetButton = rootView.findViewById(R.id.button_reset_set)
        editButton = rootView.findViewById(R.id.button_edit)
        endButton = rootView.findViewById(R.id.button_end)
        doneEditButton = rootView.findViewById(R.id.button_done_edit)
        resetLayoutButton = rootView.findViewById(R.id.button_reset_layout)
        alphaSlider = rootView.findViewById(R.id.slider_alpha)
        buttonFontSizeSlider = rootView.findViewById(R.id.slider_button_font_size)
        buttonSizeSlider = rootView.findViewById(R.id.slider_button_size)
        setFontSizeSlider = rootView.findViewById(R.id.slider_set_font_size)
        editToolbar = rootView.findViewById(R.id.edit_toolbar)
        selectionOverlay = rootView.findViewById(R.id.selection_overlay)
        selectionBorder = rootView.findViewById(R.id.selection_border)
        snapGuideVertical = rootView.findViewById(R.id.snap_guide_vertical)
        snapGuideHorizontal = rootView.findViewById(R.id.snap_guide_horizontal)
        resizeHandles = mapOf(
            R.id.handle_nw to ResizeHandle(-AppMagicNumbers.Common.FLOAT_ONE, -AppMagicNumbers.Common.FLOAT_ONE),
            R.id.handle_n to ResizeHandle(AppMagicNumbers.Common.FLOAT_ZERO, -AppMagicNumbers.Common.FLOAT_ONE),
            R.id.handle_ne to ResizeHandle(AppMagicNumbers.Common.FLOAT_ONE, -AppMagicNumbers.Common.FLOAT_ONE),
            R.id.handle_w to ResizeHandle(-AppMagicNumbers.Common.FLOAT_ONE, AppMagicNumbers.Common.FLOAT_ZERO),
            R.id.handle_e to ResizeHandle(AppMagicNumbers.Common.FLOAT_ONE, AppMagicNumbers.Common.FLOAT_ZERO),
            R.id.handle_sw to ResizeHandle(-AppMagicNumbers.Common.FLOAT_ONE, AppMagicNumbers.Common.FLOAT_ONE),
            R.id.handle_s to ResizeHandle(AppMagicNumbers.Common.FLOAT_ZERO, AppMagicNumbers.Common.FLOAT_ONE),
            R.id.handle_se to ResizeHandle(AppMagicNumbers.Common.FLOAT_ONE, AppMagicNumbers.Common.FLOAT_ONE),
        )
        startButton.setOnClickListener {
            if (!editMode) startTimer()
        }
        resetButton.setOnClickListener {
            if (!editMode) {
                setCount = AppMagicNumbers.Common.INT_ZERO
                updateCounterViews()
            }
        }
        editButton.setOnClickListener {
            if (!editMode) toggleEditMode(true)
        }
        endButton.setOnClickListener {
            if (!editMode) endSession()
        }
        doneEditButton.setOnClickListener { toggleEditMode(false) }
        resetLayoutButton.setOnClickListener {
            if (editMode) resetEditableLayout()
        }
        editableViews[KEY_TIMER] = timerText
        editableViews[KEY_SET] = setCountText
        editableViews[KEY_TOTAL] = totalCountText
        editableViews[KEY_START] = startButton
        editableViews[KEY_RESET] = resetButton
        editableViews[KEY_EDIT] = editButton
        editableViews[KEY_END] = endButton
        defaultButtonTextSizePx = startButton.textSize
        defaultSetTextSizePx = setCountText.textSize
        editableViews.forEach { (key, view) ->
            if (view is Button) {
                defaultButtonTints[key] = view.backgroundTintList
            } else if (view is TextView) {
                defaultTextColors[key] = view.textColors
            }
        }
        attachTransformHandlers()
        attachResizeHandleHandlers()
        attachEditToolbarDragHandler()
        alphaSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val alpha = OverlayPreferences.MIN_ALPHA +
                    (progress / AppMagicNumbers.Alpha.PROGRESS_MAX) * (OverlayPreferences.MAX_ALPHA - OverlayPreferences.MIN_ALPHA)
                workoutRoot.alpha = alpha
                updateOverlayWindowInteractivity()
                activityScope.launch { overlayPreferences.saveAlpha(alpha) }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        buttonFontSizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                applyButtonFontSizeSp(progressToRange(
                    progress = progress,
                    min = AppMagicNumbers.EditSliders.BUTTON_FONT_MIN_SP,
                    max = AppMagicNumbers.EditSliders.BUTTON_FONT_MAX_SP
                ))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        buttonSizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                applyButtonScale(progressToRange(
                    progress = progress,
                    min = AppMagicNumbers.EditSliders.BUTTON_SCALE_MIN,
                    max = AppMagicNumbers.EditSliders.BUTTON_SCALE_MAX
                ))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        setFontSizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                applySetFontSizeSp(progressToRange(
                    progress = progress,
                    min = AppMagicNumbers.EditSliders.SET_FONT_MIN_SP,
                    max = AppMagicNumbers.EditSliders.SET_FONT_MAX_SP
                ))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        activityScope.launch {
            val state = overlayPreferences.state.first()
            workoutRoot.alpha = state.alpha
            alphaSlider.progress = (
                (state.alpha - OverlayPreferences.MIN_ALPHA) * AppMagicNumbers.Alpha.PROGRESS_MAX /
                    (OverlayPreferences.MAX_ALPHA - OverlayPreferences.MIN_ALPHA)
                ).toInt()
        }
        initializeEditControlSliders()
        workoutRoot.post {
            updateDefaultToolbarPosition()
            editToolbar.x = defaultToolbarX
            editToolbar.y = defaultToolbarY
            val currentViewportWidth = workoutRoot.width
            val currentViewportHeight = workoutRoot.height
            editableViews.forEach { (key, view) ->
                defaultViewStates[key] = ViewTransformState(
                    x = if (key == KEY_TIMER) null else view.x,
                    y = if (key == KEY_TIMER) null else view.y,
                    scaleX = view.scaleX,
                    scaleY = view.scaleY,
                    rotation = view.rotation,
                )
            }
            activityScope.launch {
                val savedViewport = overlayPreferences.readWorkoutViewportSize()
                val viewportChanged = savedViewport != null &&
                    (savedViewport.width != currentViewportWidth || savedViewport.height != currentViewportHeight)
                editableViews.forEach { (key, view) ->
                    val transform = overlayPreferences.readElementTransform(key)
                    view.scaleX = transform.scaleX
                    view.scaleY = transform.scaleY
                    view.rotation = transform.rotation
                    if (key != KEY_TIMER && transform.x != null && transform.y != null) {
                        val scaled = scalePositionToCurrentViewport(
                            view = view,
                            savedX = transform.x.toFloat(),
                            savedY = transform.y.toFloat(),
                            savedViewport = savedViewport,
                            currentViewportWidth = currentViewportWidth,
                            currentViewportHeight = currentViewportHeight,
                        )
                        view.x = scaled.first
                        view.y = scaled.second
                        if (viewportChanged) {
                            overlayPreferences.saveElementTransform(
                                elementId = key,
                                x = scaled.first.toInt(),
                                y = scaled.second.toInt(),
                                scaleX = transform.scaleX,
                                scaleY = transform.scaleY,
                                rotation = transform.rotation,
                            )
                        }
                    }
                }
                selectedElementKey?.let { key ->
                    editableViews[key]?.let { updateSelectionBox(it) }
                }
                overlayPreferences.saveWorkoutViewportSize(currentViewportWidth, currentViewportHeight)
                syncControlButtonWindows(forceCreate = true)
            }
        }
        updateTimerView()
        updateCounterViews()
        toggleEditMode(false)
    }
    private fun attachTransformHandlers() {
        editableViews.forEach { (key, view) ->
            view.setOnTouchListener(object : View.OnTouchListener {
                val handler = Handler(Looper.getMainLooper())
                val touchSlop = ViewConfiguration.get(this@OverlayService).scaledTouchSlop.toFloat()

                var pointerStartX = AppMagicNumbers.Common.FLOAT_ZERO
                var pointerStartY = AppMagicNumbers.Common.FLOAT_ZERO
                var longPressTriggered = false
                var dragStarted = false
                val groupStartPositions = linkedMapOf<String, Pair<Float, Float>>()

                var initialDistance = AppMagicNumbers.Common.FLOAT_ZERO
                var initialAngle = AppMagicNumbers.Common.FLOAT_ZERO
                var initialScaleX = AppMagicNumbers.Common.FLOAT_ONE
                var initialScaleY = AppMagicNumbers.Common.FLOAT_ONE
                var initialRotation = AppMagicNumbers.Common.FLOAT_ZERO
                var longPressRunnable: Runnable? = null

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    if (!editMode) return false

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            hideSnapGuideLines()
                            longPressTriggered = false
                            dragStarted = false
                            pointerStartX = event.rawX
                            pointerStartY = event.rawY

                            val runnable = Runnable {
                                longPressTriggered = true
                                longPressSelectionVisuals = true
                                addElementToSelection(key)
                                addElementAtRawPositionToSelection(pointerStartX, pointerStartY)
                            }
                            longPressRunnable = runnable
                            handler.postDelayed(runnable, ViewConfiguration.getLongPressTimeout().toLong())
                            return true
                        }

                        MotionEvent.ACTION_POINTER_DOWN -> {
                            hideSnapGuideLines()
                            cancelLongPress(handler)
                            if (event.pointerCount >= AppMagicNumbers.Common.INT_TWO) {
                                initialDistance = pointerDistance(event)
                                initialAngle = pointerAngle(event)
                                initialScaleX = v.scaleX
                                initialScaleY = v.scaleY
                                initialRotation = v.rotation
                            }
                            return true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (!longPressTriggered &&
                                (abs(event.rawX - pointerStartX) > touchSlop || abs(event.rawY - pointerStartY) > touchSlop)
                            ) {
                                cancelLongPress(handler)
                                if (!dragStarted) {
                                    dragStarted = true
                                    if (!selectedElementKeys.contains(key)) {
                                        selectSingleElement(key)
                                    }
                                    cacheGroupStartPositions(groupStartPositions)
                                }
                            }

                            if (longPressTriggered) {
                                addElementAtRawPositionToSelection(event.rawX, event.rawY)
                                return true
                            }

                            if (event.pointerCount >= AppMagicNumbers.Common.INT_TWO) {
                                hideSnapGuideLines()
                                val currentDistance = pointerDistance(event)
                                if (initialDistance > AppMagicNumbers.Common.FLOAT_ZERO && currentDistance > AppMagicNumbers.Common.FLOAT_ZERO) {
                                    val scaleFactor = currentDistance / initialDistance
                                    v.scaleX = (initialScaleX * scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
                                    v.scaleY = (initialScaleY * scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
                                }

                                val currentAngle = pointerAngle(event)
                                val rawRotation = initialRotation + (currentAngle - initialAngle)
                                v.rotation = snapRotation(rawRotation)
                            } else if (key != KEY_TIMER) {
                                if (!dragStarted) return true
                                val deltaX = event.rawX - pointerStartX
                                val deltaY = event.rawY - pointerStartY
                                val snapResult = applySmartMagnetSnap(
                                    draggedKey = key,
                                    groupStartPositions = groupStartPositions,
                                    deltaX = deltaX,
                                    deltaY = deltaY,
                                )
                                updateSnapGuideLines(snapResult)
                                getSelectedViews().forEach { selectedView ->
                                    val selectedKey = editableViews.entries.firstOrNull { it.value == selectedView }?.key
                                        ?: return@forEach
                                    if (selectedKey == KEY_TIMER) return@forEach
                                    val start = groupStartPositions[selectedKey] ?: (selectedView.x to selectedView.y)
                                    val targetX = start.first + snapResult.deltaX
                                    val targetY = start.second + snapResult.deltaY
                                    val clampedX = targetX.coerceIn(AppMagicNumbers.Common.FLOAT_ZERO, (workoutRoot.width - selectedView.width).toFloat())
                                    val clampedY = targetY.coerceIn(AppMagicNumbers.Common.FLOAT_ZERO, (workoutRoot.height - selectedView.height).toFloat())
                                    selectedView.x = clampedX
                                    selectedView.y = clampedY
                                }
                            } else {
                                hideSnapGuideLines()
                            }
                            getPrimarySelectedView()?.let { updateSelectionBox(it) }
                            return true
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                            cancelLongPress(handler)

                            if (!longPressTriggered && dragStarted) {
                                getSelectedKeyValues().forEach { (selectedKey, selectedView) ->
                                    persistTransformFor(selectedKey, selectedView)
                                }
                            }

                            clearLongPressSelectionVisuals()

                            if (event.actionMasked == MotionEvent.ACTION_UP &&
                                !longPressTriggered &&
                                !dragStarted &&
                                abs(event.rawX - pointerStartX) < AppMagicNumbers.Interaction.TAP_SLOP_PX &&
                                abs(event.rawY - pointerStartY) < AppMagicNumbers.Interaction.TAP_SLOP_PX
                            ) {
                                handleElementTapSelection(key)
                                v.performClick()
                            }
                            hideSnapGuideLines()
                            return true
                        }
                    }
                    return false
                }

                private fun cacheGroupStartPositions(groupStartPositions: LinkedHashMap<String, Pair<Float, Float>>) {
                    groupStartPositions.clear()
                    getSelectedViews().forEach { selectedView ->
                        val selectedKey = editableViews.entries.firstOrNull { it.value == selectedView }?.key
                            ?: return@forEach
                        groupStartPositions[selectedKey] = selectedView.x to selectedView.y
                    }
                }

                private fun cancelLongPress(handler: Handler) {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    longPressRunnable = null
                }
            })
        }
    }

    private fun attachResizeHandleHandlers() {
        resizeHandles.forEach { (viewId, handle) ->
            workoutRoot.findViewById<View>(viewId).setOnTouchListener(object : View.OnTouchListener {
                var downRawX = AppMagicNumbers.Common.FLOAT_ZERO
                var downRawY = AppMagicNumbers.Common.FLOAT_ZERO
                var baseWidth = AppMagicNumbers.Common.FLOAT_ONE
                var baseHeight = AppMagicNumbers.Common.FLOAT_ONE
                val initialTransforms = linkedMapOf<String, ViewTransformState>()

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    if (!editMode) return false
                    val selected = getPrimarySelectedView() ?: return false

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downRawX = event.rawX
                            downRawY = event.rawY
                            baseWidth = max(AppMagicNumbers.Common.FLOAT_ONE, selected.width * selected.scaleX)
                            baseHeight = max(AppMagicNumbers.Common.FLOAT_ONE, selected.height * selected.scaleY)
                            initialTransforms.clear()
                            getSelectedKeyValues().forEach { (key, target) ->
                                initialTransforms[key] = ViewTransformState(
                                    x = target.x,
                                    y = target.y,
                                    scaleX = target.scaleX,
                                    scaleY = target.scaleY,
                                    rotation = target.rotation,
                                )
                            }
                            return true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - downRawX
                            val dy = event.rawY - downRawY
                            val delta = computeHandleScaleDeltas(dx, dy, baseWidth, baseHeight, handle)
                            getSelectedKeyValues().forEach { (key, target) ->
                                val initial = initialTransforms[key] ?: return@forEach
                                val targetInitialScaleX = initial.scaleX
                                val targetInitialScaleY = initial.scaleY
                                val targetBaseWidth = max(AppMagicNumbers.Common.FLOAT_ONE, target.width * targetInitialScaleX)
                                val targetBaseHeight = max(AppMagicNumbers.Common.FLOAT_ONE, target.height * targetInitialScaleY)
                                val targetInitialX = initial.x ?: target.x
                                val targetInitialY = initial.y ?: target.y

                                val newScaleX = (targetInitialScaleX * (AppMagicNumbers.Common.FLOAT_ONE + delta.scaleX)).coerceIn(MIN_SCALE, MAX_SCALE)
                                val newScaleY = (targetInitialScaleY * (AppMagicNumbers.Common.FLOAT_ONE + delta.scaleY)).coerceIn(MIN_SCALE, MAX_SCALE)
                                val newWidth = max(AppMagicNumbers.Common.FLOAT_ONE, target.width * newScaleX)
                                val newHeight = max(AppMagicNumbers.Common.FLOAT_ONE, target.height * newScaleY)

                                target.scaleX = newScaleX
                                target.scaleY = newScaleY
                                applyResizeAnchorPosition(
                                    selected = target,
                                    handle = handle,
                                    initialX = targetInitialX,
                                    initialY = targetInitialY,
                                    initialWidth = targetBaseWidth,
                                    initialHeight = targetBaseHeight,
                                    newWidth = newWidth,
                                    newHeight = newHeight,
                                )
                            }
                            getPrimarySelectedView()?.let { updateSelectionBox(it) }
                            return true
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            persistSelectedTransform()
                            return true
                        }
                    }
                    return false
                }
            })
        }
    }

    private fun applyResizeAnchorPosition(
        selected: View,
        handle: ResizeHandle,
        initialX: Float,
        initialY: Float,
        initialWidth: Float,
        initialHeight: Float,
        newWidth: Float,
        newHeight: Float,
    ) {
        val halfWidthDelta = (newWidth - initialWidth) / AppMagicNumbers.Common.FLOAT_TWO
        val halfHeightDelta = (newHeight - initialHeight) / AppMagicNumbers.Common.FLOAT_TWO

        selected.x = when (handle.directionX) {
            -AppMagicNumbers.Common.FLOAT_ONE -> initialX - halfWidthDelta
            AppMagicNumbers.Common.FLOAT_ONE -> initialX + halfWidthDelta
            else -> initialX
        }
        selected.y = when (handle.directionY) {
            -AppMagicNumbers.Common.FLOAT_ONE -> initialY - halfHeightDelta
            AppMagicNumbers.Common.FLOAT_ONE -> initialY + halfHeightDelta
            else -> initialY
        }
    }

    private fun computeHandleScaleDeltas(
        dx: Float,
        dy: Float,
        baseWidth: Float,
        baseHeight: Float,
        handle: ResizeHandle,
    ): ResizeScaleDelta {
        val deltaX = if (handle.directionX != AppMagicNumbers.Common.FLOAT_ZERO) {
            ((dx * handle.directionX) / baseWidth) * RESIZE_SENSITIVITY
        } else {
            AppMagicNumbers.Common.FLOAT_ZERO
        }
        val deltaY = if (handle.directionY != AppMagicNumbers.Common.FLOAT_ZERO) {
            ((dy * handle.directionY) / baseHeight) * RESIZE_SENSITIVITY
        } else {
            AppMagicNumbers.Common.FLOAT_ZERO
        }
        return ResizeScaleDelta(deltaX, deltaY)
    }

    private fun startTimer() {
        timer?.cancel()
        elapsedSeconds = AppMagicNumbers.Common.INT_ZERO
        setCount += AppMagicNumbers.Timer.START_COUNT_INCREMENT
        totalCount += AppMagicNumbers.Timer.START_COUNT_INCREMENT
        updateCounterViews()
        updateTimerView()

        timer = object : CountDownTimer(AppMagicNumbers.Common.LONG_MAX, AppMagicNumbers.Timer.TICK_MS) {
            override fun onTick(millisUntilFinished: Long) {
                elapsedSeconds += AppMagicNumbers.Timer.START_COUNT_INCREMENT
                updateTimerView()
            }

            override fun onFinish() = Unit
        }.start()
        refreshNotification()
    }

    private fun updateTimerView() {
        val minutes = elapsedSeconds / AppMagicNumbers.Timer.SECONDS_PER_MINUTE
        val seconds = elapsedSeconds % AppMagicNumbers.Timer.SECONDS_PER_MINUTE
        timerText.text = String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun updateCounterViews() {
        setCountText.text = getString(R.string.set_count_format, setCount)
        totalCountText.text = getString(R.string.total_count_format, totalCount)
        refreshNotification()
    }

    private fun syncControlButtonWindows(forceCreate: Boolean = false) {
        if (!::startButton.isInitialized || startButton.width == AppMagicNumbers.Common.INT_ZERO) return

        if (forceCreate || controlButtonWindows.isEmpty()) {
            rebuildControlButtonWindows()
        }

        controlButtonWindows.values.forEach { window ->
            val source = window.sourceButton
            window.view.text = source.text
            window.view.setTextSize(TypedValue.COMPLEX_UNIT_PX, source.textSize)
            window.view.backgroundTintList = source.backgroundTintList
            window.view.setTextColor(source.currentTextColor)
            window.view.alpha = source.alpha
            window.view.scaleX = source.scaleX
            window.view.scaleY = source.scaleY
            window.view.rotation = source.rotation
            window.layoutParams.width = max(source.width, AppMagicNumbers.Common.INT_ONE)
            window.layoutParams.height = max(source.height, AppMagicNumbers.Common.INT_ONE)
            window.layoutParams.x = source.x.roundToInt()
            window.layoutParams.y = source.y.roundToInt()
            windowManager.updateViewLayout(window.view, window.layoutParams)
        }

        updateControlPanelVisibility()
    }

    private fun rebuildControlButtonWindows() {
        controlButtonWindows.values.forEach { window ->
            windowManager.removeView(window.view)
        }
        controlButtonWindows.clear()

        createControlButtonWindow(KEY_START, startButton) {
            if (!editMode) startTimer()
        }
        createControlButtonWindow(KEY_RESET, resetButton) {
            if (!editMode) {
                setCount = AppMagicNumbers.Common.INT_ZERO
                updateCounterViews()
            }
        }
        createControlButtonWindow(KEY_EDIT, editButton) {
            if (!editMode) toggleEditMode(true)
        }
        createControlButtonWindow(KEY_END, endButton) {
            if (!editMode) endSession()
        }
    }

    private fun createControlButtonWindow(
        key: String,
        sourceButton: Button,
        onClick: () -> Unit,
    ) {
        val proxyButton = Button(this).apply {
            text = sourceButton.text
            setTextSize(TypedValue.COMPLEX_UNIT_PX, sourceButton.textSize)
            backgroundTintList = sourceButton.backgroundTintList
            setTextColor(sourceButton.currentTextColor)
            setOnClickListener { onClick() }
        }

        val params = WindowManager.LayoutParams(
            max(sourceButton.width, AppMagicNumbers.Common.INT_ONE),
            max(sourceButton.height, AppMagicNumbers.Common.INT_ONE),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = sourceButton.x.roundToInt()
            y = sourceButton.y.roundToInt()
        }

        windowManager.addView(proxyButton, params)
        controlButtonWindows[key] = ControlButtonWindow(sourceButton, proxyButton, params)
    }

    private fun updateControlPanelVisibility() {
        controlButtonWindows.values.forEach { window ->
            window.view.visibility = if (editMode) View.GONE else View.VISIBLE
        }
        startButton.visibility = if (editMode) View.VISIBLE else View.GONE
        resetButton.visibility = if (editMode) View.VISIBLE else View.GONE
        editButton.visibility = if (editMode) View.VISIBLE else View.GONE
        endButton.visibility = if (editMode) View.VISIBLE else View.GONE
    }

    private fun updateOverlayWindowInteractivity() {
        val hostView = overlayHostView ?: return
        val params = overlayLayoutParams ?: return
        val shouldPassThrough = !editMode
        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            if (shouldPassThrough) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else AppMagicNumbers.Common.INT_ZERO
        params.alpha = if (shouldPassThrough) workoutRoot.alpha.coerceAtMost(0.8f) else 1f
        windowManager.updateViewLayout(hostView, params)
        updateControlPanelVisibility()
        debugSelectionLog("updateOverlayWindowInteractivity passThrough=$shouldPassThrough, windowAlpha=${params.alpha}")
    }

    private fun refreshNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(AppMagicNumbers.Notification.SERVICE_ID, buildNotification())
    }

    private fun initializeEditControlSliders() {
        val currentButtonSizeSp = pxToSp(startButton.textSize)
        val currentSetSizeSp = pxToSp(setCountText.textSize)
        val currentButtonScale = startButton.scaleX

        buttonFontSizeSlider.progress = rangeToProgress(
            value = currentButtonSizeSp,
            min = AppMagicNumbers.EditSliders.BUTTON_FONT_MIN_SP,
            max = AppMagicNumbers.EditSliders.BUTTON_FONT_MAX_SP
        )
        buttonSizeSlider.progress = rangeToProgress(
            value = currentButtonScale,
            min = AppMagicNumbers.EditSliders.BUTTON_SCALE_MIN,
            max = AppMagicNumbers.EditSliders.BUTTON_SCALE_MAX
        )
        setFontSizeSlider.progress = rangeToProgress(
            value = currentSetSizeSp,
            min = AppMagicNumbers.EditSliders.SET_FONT_MIN_SP,
            max = AppMagicNumbers.EditSliders.SET_FONT_MAX_SP
        )
    }

    private fun applyButtonFontSizeSp(sizeSp: Float) {
        listOf(
            startButton,
            resetButton,
            editButton,
            endButton,
            doneEditButton,
            resetLayoutButton
        ).forEach { button ->
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        }
    }

    private fun applyButtonScale(scale: Float) {
        listOf(
            startButton,
            resetButton,
            editButton,
            endButton,
            doneEditButton,
            resetLayoutButton
        ).forEach { button ->
            button.scaleX = scale
            button.scaleY = scale
        }
    }

    private fun applySetFontSizeSp(sizeSp: Float) {
        setCountText.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        totalCountText.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }

    private fun progressToRange(progress: Int, min: Float, max: Float): Float {
        val p = progress.coerceIn(
            AppMagicNumbers.EditSliders.PROGRESS_MIN,
            AppMagicNumbers.EditSliders.PROGRESS_MAX
        )
        val ratio = p.toFloat() / AppMagicNumbers.EditSliders.PROGRESS_MAX.toFloat()
        return min + ((max - min) * ratio)
    }

    private fun rangeToProgress(value: Float, min: Float, max: Float): Int {
        val clamped = value.coerceIn(min, max)
        val ratio = if (max > min) (clamped - min) / (max - min) else AppMagicNumbers.Common.FLOAT_ZERO
        return (ratio * AppMagicNumbers.EditSliders.PROGRESS_MAX).roundToInt()
            .coerceIn(AppMagicNumbers.EditSliders.PROGRESS_MIN, AppMagicNumbers.EditSliders.PROGRESS_MAX)
    }

    private fun pxToSp(px: Float): Float {
        return px / resources.displayMetrics.scaledDensity
    }

    private fun toggleEditMode(enabled: Boolean) {
        editMode = enabled
        editToolbar.visibility = if (enabled) View.VISIBLE else View.GONE

        if (enabled) {
            editToolbar.post {
                updateDefaultToolbarPosition()
                editToolbar.x = defaultToolbarX
                editToolbar.y = defaultToolbarY
            }
        } else {
            workoutRoot.post {
                syncControlButtonWindows(forceCreate = true)
            }
        }

        selectedElementKeys.clear()
        selectedElementKey = null
        longPressSelectionVisuals = false
        val defaultAlpha = if (enabled) AppMagicNumbers.Interaction.NON_SELECTED_ALPHA else AppMagicNumbers.Interaction.FULL_ALPHA
        editableViews.forEach { (key, view) ->
            view.alpha = defaultAlpha
            if (view is Button) {
                view.backgroundTintList = defaultButtonTints[key]
            } else if (view is TextView) {
                defaultTextColors[key]?.let { view.setTextColor(it) }
            }
        }
        selectionOverlay.visibility = View.GONE
        hideSnapGuideLines()
        updateOverlayWindowInteractivity()
        refreshNotification()
    }

    private fun resetEditableLayout() {
        updateDefaultToolbarPosition()
        selectedElementKeys.clear()
        selectedElementKey = null
        longPressSelectionVisuals = false
        editableViews.forEach { (key, view) ->
            val default = defaultViewStates[key] ?: return@forEach
            default.x?.let { view.x = it }
            default.y?.let { view.y = it }
            view.scaleX = default.scaleX
            view.scaleY = default.scaleY
            view.rotation = default.rotation
            persistTransformFor(key, view)
        }

        editToolbar.x = defaultToolbarX
        editToolbar.y = defaultToolbarY
        startButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultButtonTextSizePx)
        resetButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultButtonTextSizePx)
        editButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultButtonTextSizePx)
        endButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultButtonTextSizePx)
        doneEditButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultButtonTextSizePx)
        resetLayoutButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultButtonTextSizePx)
        applyButtonScale(AppMagicNumbers.Common.FLOAT_ONE)
        setCountText.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultSetTextSizePx)
        totalCountText.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultSetTextSizePx)
        initializeEditControlSliders()
        editableViews.forEach { (key, view) ->
            view.alpha = AppMagicNumbers.Interaction.NON_SELECTED_ALPHA
            if (view is Button) {
                view.backgroundTintList = defaultButtonTints[key]
            } else if (view is TextView) {
                defaultTextColors[key]?.let { view.setTextColor(it) }
            }
        }
        selectionOverlay.visibility = View.GONE
        hideSnapGuideLines()
        syncControlButtonWindows(forceCreate = true)
    }

    private fun updateDefaultToolbarPosition() {
        val toolbarWidth = resolveToolbarWidthPx().toFloat()
        val toolbarHeight = resolveToolbarHeightPx().toFloat()
        val centeredX = ((workoutRoot.width - toolbarWidth) / AppMagicNumbers.Common.FLOAT_TWO)
            .coerceAtLeast(AppMagicNumbers.Common.FLOAT_ZERO)
        val centeredY = ((workoutRoot.height - toolbarHeight) / AppMagicNumbers.Common.FLOAT_TWO)
            .coerceAtLeast(AppMagicNumbers.Common.FLOAT_ZERO)
        val maxX = (workoutRoot.width - toolbarWidth).toFloat().coerceAtLeast(AppMagicNumbers.Common.FLOAT_ZERO)
        val maxY = (workoutRoot.height - toolbarHeight).toFloat().coerceAtLeast(AppMagicNumbers.Common.FLOAT_ZERO)
        defaultToolbarX = centeredX.coerceIn(AppMagicNumbers.Common.FLOAT_ZERO, maxX)
        defaultToolbarY = centeredY.coerceIn(AppMagicNumbers.Common.FLOAT_ZERO, maxY)
    }

    private fun resolveToolbarWidthPx(): Int {
        if (editToolbar.width > AppMagicNumbers.Common.INT_ZERO) return editToolbar.width
        val widthSpec = View.MeasureSpec.makeMeasureSpec(workoutRoot.width, View.MeasureSpec.AT_MOST)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(workoutRoot.height, View.MeasureSpec.AT_MOST)
        editToolbar.measure(widthSpec, heightSpec)
        return editToolbar.measuredWidth
    }

    private fun resolveToolbarHeightPx(): Int {
        if (editToolbar.height > AppMagicNumbers.Common.INT_ZERO) return editToolbar.height
        val widthSpec = View.MeasureSpec.makeMeasureSpec(workoutRoot.width, View.MeasureSpec.AT_MOST)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(workoutRoot.height, View.MeasureSpec.AT_MOST)
        editToolbar.measure(widthSpec, heightSpec)
        return editToolbar.measuredHeight
    }

    private fun getStatusBarHeightPx(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId <= AppMagicNumbers.Common.INT_ZERO) return AppMagicNumbers.Common.INT_ZERO
        return resources.getDimensionPixelSize(resourceId)
    }

    private fun attachEditToolbarDragHandler() {
        editToolbar.setOnTouchListener(object : View.OnTouchListener {
            var startX = AppMagicNumbers.Common.FLOAT_ZERO
            var startY = AppMagicNumbers.Common.FLOAT_ZERO
            var downRawX = AppMagicNumbers.Common.FLOAT_ZERO
            var downRawY = AppMagicNumbers.Common.FLOAT_ZERO

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (!editMode) return false

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (isTouchOnButtonOrSlider(event)) return false
                        startX = editToolbar.x
                        startY = editToolbar.y
                        downRawX = event.rawX
                        downRawY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val targetX = startX + (event.rawX - downRawX)
                        val targetY = startY + (event.rawY - downRawY)
                        val maxX = (workoutRoot.width - editToolbar.width).toFloat().coerceAtLeast(AppMagicNumbers.Common.FLOAT_ZERO)
                        val maxY = (workoutRoot.height - editToolbar.height).toFloat().coerceAtLeast(AppMagicNumbers.Common.FLOAT_ZERO)
                        editToolbar.x = targetX.coerceIn(AppMagicNumbers.Common.FLOAT_ZERO, maxX)
                        editToolbar.y = targetY.coerceIn(AppMagicNumbers.Common.FLOAT_ZERO, maxY)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun isPointInsideAnyEditableView(x: Float, y: Float): Boolean {
        return editableViews.values.any { view ->
            calculateTransformedBounds(view).contains(x, y)
        }
    }

    private fun handleRootTap(x: Float, y: Float) {
        val isTap = abs(x - rootTapDownX) < AppMagicNumbers.Interaction.TAP_SLOP_PX &&
            abs(y - rootTapDownY) < AppMagicNumbers.Interaction.TAP_SLOP_PX
        if (!isTap) {
            debugSelectionLog("handleRootTap ignored: not a tap x=$x, y=$y, downX=$rootTapDownX, downY=$rootTapDownY")
            return
        }
        if (isPointInsideAnyEditableView(x, y)) {
            debugSelectionLog("handleRootTap ignored: tapped selectable view x=$x, y=$y")
            return
        }
        debugSelectionLog("handleRootTap clearing selection x=$x, y=$y, selected=$selectedElementKeys, primary=$selectedElementKey")
        clearSelection()
    }

    private fun handleRootActionDown(x: Float, y: Float) {
        if (isPointInsideAnyEditableView(x, y)) return
        if (selectedElementKeys.isEmpty() && selectedElementKey == null) return
        debugSelectionLog("handleRootActionDown clearing selection x=$x, y=$y, selected=$selectedElementKeys, primary=$selectedElementKey")
        clearSelection()
    }

    private fun isTouchOnButtonOrSlider(event: MotionEvent): Boolean {
        val group = editToolbar as? ViewGroup ?: return false
        val x = event.x.toInt()
        val y = event.y.toInt()
        for (i in AppMagicNumbers.Common.INT_ZERO until group.childCount) {
            val child = group.getChildAt(i)
            if (child is Button || child is SeekBar) {
                if (x >= child.left && x <= child.right && y >= child.top && y <= child.bottom) {
                    return true
                }
            } else if (child is ViewGroup) {
                for (j in AppMagicNumbers.Common.INT_ZERO until child.childCount) {
                    val nested = child.getChildAt(j)
                    if (nested is Button || nested is SeekBar) {
                        val left = child.left + nested.left
                        val right = child.left + nested.right
                        val top = child.top + nested.top
                        val bottom = child.top + nested.bottom
                        if (x >= left && x <= right && y >= top && y <= bottom) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun selectElement(key: String) {
        selectSingleElement(key)
    }

    private fun selectSingleElement(key: String) {
        selectedElementKeys.clear()
        selectedElementKeys.add(key)
        selectedElementKey = key
        longPressSelectionVisuals = false
        debugSelectionLog("selectSingleElement key=$key")
        refreshSelectionVisuals()
    }

    private fun handleElementTapSelection(key: String) {
        debugSelectionLog("handleElementTapSelection key=$key, selectedBefore=$selectedElementKeys, primaryBefore=$selectedElementKey")
        when {
            selectedElementKeys.isEmpty() -> selectSingleElement(key)
            selectedElementKeys.contains(key) -> {
                selectedElementKey = key
                debugSelectionLog("handleElementTapSelection keepSelection key=$key")
                refreshSelectionVisuals()
            }
            else -> selectSingleElement(key)
        }
    }

    private fun toggleElementSelection(key: String) {
        if (selectedElementKeys.contains(key)) {
            selectedElementKeys.remove(key)
        } else {
            selectedElementKeys.add(key)
        }
        selectedElementKey = selectedElementKeys.lastOrNull()
        refreshSelectionVisuals()
    }

    private fun addElementToSelection(key: String) {
        if (selectedElementKeys.add(key)) {
            selectedElementKey = key
            debugSelectionLog("addElementToSelection added key=$key, selected=$selectedElementKeys")
            refreshSelectionVisuals()
        } else {
            selectedElementKey = key
            debugSelectionLog("addElementToSelection alreadySelected key=$key, selected=$selectedElementKeys")
            refreshSelectionVisuals()
        }
    }

    private fun addElementAtRawPositionToSelection(rawX: Float, rawY: Float) {
        val rootLocation = IntArray(AppMagicNumbers.Common.INT_TWO)
        workoutRoot.getLocationOnScreen(rootLocation)
        val localX = rawX - rootLocation[AppMagicNumbers.Common.INT_ZERO]
        val localY = rawY - rootLocation[AppMagicNumbers.Common.INT_ONE]

        editableViews.forEach { (key, view) ->
            val bounds = calculateTransformedBounds(view)
            if (bounds.contains(localX, localY)) {
                addElementToSelection(key)
                return
            }
        }
    }

    private fun refreshSelectionVisuals() {
        val selectedButtonColor = getColor(R.color.selection_button_accent)
        val showMultiSelectHighlight = longPressSelectionVisuals ||
            selectedElementKeys.size >= AppMagicNumbers.Common.INT_TWO
        editableViews.forEach { (entryKey, view) ->
            val isSelected = selectedElementKeys.contains(entryKey)
            view.alpha = if (isSelected) AppMagicNumbers.Interaction.FULL_ALPHA else AppMagicNumbers.Interaction.NON_SELECTED_ALPHA
            if (view is Button) {
                view.backgroundTintList = if (isSelected && showMultiSelectHighlight) {
                    ColorStateList.valueOf(selectedButtonColor)
                } else {
                    defaultButtonTints[entryKey]
                }
            } else if (view is TextView) {
                if (isSelected && showMultiSelectHighlight) {
                    view.setTextColor(selectedButtonColor)
                } else {
                    defaultTextColors[entryKey]?.let { view.setTextColor(it) }
                }
            }
        }
        getPrimarySelectedView()?.let {
            updateSelectionBox(it)
        } ?: run {
            selectionOverlay.visibility = View.GONE
        }
    }

    private fun clearLongPressSelectionVisuals() {
        if (!longPressSelectionVisuals) return
        longPressSelectionVisuals = false
    }

    private fun clearSelection() {
        if (selectedElementKeys.isEmpty() && selectedElementKey == null) {
            debugSelectionLog("clearSelection skipped: already empty")
            return
        }
        debugSelectionLog("clearSelection selectedBefore=$selectedElementKeys, primaryBefore=$selectedElementKey")
        selectedElementKeys.clear()
        selectedElementKey = null
        longPressSelectionVisuals = false
        refreshSelectionVisuals()
    }

    private fun getPrimarySelectedView(): View? {
        val key = selectedElementKey ?: return null
        return editableViews[key]
    }

    private fun getSelectedViews(): List<View> {
        if (selectedElementKeys.isEmpty()) return emptyList()
        return selectedElementKeys.mapNotNull { editableViews[it] }
    }

    private fun getSelectedKeyValues(): List<Pair<String, View>> {
        if (selectedElementKeys.isEmpty()) return emptyList()
        return selectedElementKeys.mapNotNull { key ->
            editableViews[key]?.let { key to it }
        }
    }

    private fun persistSelectedTransform() {
        getSelectedKeyValues().forEach { (key, view) ->
            persistTransformFor(key, view)
        }
    }

    private fun persistTransformFor(key: String, view: View) {
        activityScope.launch {
            val x = if (key == KEY_TIMER) null else view.x.toInt()
            val y = if (key == KEY_TIMER) null else view.y.toInt()
            overlayPreferences.saveElementTransform(
                elementId = key,
                x = x,
                y = y,
                scaleX = view.scaleX,
                scaleY = view.scaleY,
                rotation = view.rotation,
            )
            if (workoutRoot.width > AppMagicNumbers.Common.INT_ZERO && workoutRoot.height > AppMagicNumbers.Common.INT_ZERO) {
                overlayPreferences.saveWorkoutViewportSize(workoutRoot.width, workoutRoot.height)
            }
        }
    }

    private fun scalePositionToCurrentViewport(
        view: View,
        savedX: Float,
        savedY: Float,
        savedViewport: WorkoutViewportSize?,
        currentViewportWidth: Int,
        currentViewportHeight: Int,
    ): Pair<Float, Float> {
        if (savedViewport == null) {
            val maxX = (currentViewportWidth - view.width).toFloat().coerceAtLeast(AppMagicNumbers.Common.FLOAT_ZERO)
            val maxY = (currentViewportHeight - view.height).toFloat().coerceAtLeast(AppMagicNumbers.Common.FLOAT_ZERO)
            return savedX.coerceIn(AppMagicNumbers.Common.FLOAT_ZERO, maxX) to savedY.coerceIn(AppMagicNumbers.Common.FLOAT_ZERO, maxY)
        }

        val oldMaxX = (savedViewport.width - view.width).toFloat().coerceAtLeast(AppMagicNumbers.Common.FLOAT_ZERO)
        val oldMaxY = (savedViewport.height - view.height).toFloat().coerceAtLeast(AppMagicNumbers.Common.FLOAT_ZERO)
        val newMaxX = (currentViewportWidth - view.width).toFloat().coerceAtLeast(AppMagicNumbers.Common.FLOAT_ZERO)
        val newMaxY = (currentViewportHeight - view.height).toFloat().coerceAtLeast(AppMagicNumbers.Common.FLOAT_ZERO)

        val ratioX = if (oldMaxX > AppMagicNumbers.Common.FLOAT_ZERO) savedX.coerceIn(AppMagicNumbers.Common.FLOAT_ZERO, oldMaxX) / oldMaxX else AppMagicNumbers.Common.FLOAT_ZERO
        val ratioY = if (oldMaxY > AppMagicNumbers.Common.FLOAT_ZERO) savedY.coerceIn(AppMagicNumbers.Common.FLOAT_ZERO, oldMaxY) / oldMaxY else AppMagicNumbers.Common.FLOAT_ZERO

        return (ratioX * newMaxX).coerceIn(AppMagicNumbers.Common.FLOAT_ZERO, newMaxX) to (ratioY * newMaxY).coerceIn(AppMagicNumbers.Common.FLOAT_ZERO, newMaxY)
    }

    private fun updateSelectionBox(target: View) {
        val bounds = calculateTransformedBounds(target)
        val gapX = (bounds.width() * BOX_PADDING_RATIO).coerceAtLeast(AppMagicNumbers.Interaction.MIN_VIEW_SIZE)
        val gapY = (bounds.height() * BOX_PADDING_RATIO).coerceAtLeast(AppMagicNumbers.Interaction.MIN_VIEW_SIZE)
        val handleRadiusPx = dpToPx(HANDLE_SIZE_DP / AppMagicNumbers.Common.FLOAT_TWO)
        bounds.inset(-(gapX + handleRadiusPx), -(gapY + handleRadiusPx))

        val width = bounds.width().roundToInt().coerceAtLeast(AppMagicNumbers.Interaction.MIN_VIEW_SIZE_INT)
        val height = bounds.height().roundToInt().coerceAtLeast(AppMagicNumbers.Interaction.MIN_VIEW_SIZE_INT)

        val lp = selectionOverlay.layoutParams as FrameLayout.LayoutParams
        lp.width = width
        lp.height = height
        selectionOverlay.layoutParams = lp

        val borderLp = selectionBorder.layoutParams as FrameLayout.LayoutParams
        val inset = handleRadiusPx.roundToInt()
        borderLp.leftMargin = inset
        borderLp.topMargin = inset
        borderLp.rightMargin = inset
        borderLp.bottomMargin = inset
        selectionBorder.layoutParams = borderLp

        selectionOverlay.x = bounds.left
        selectionOverlay.y = bounds.top
        selectionOverlay.rotation = AppMagicNumbers.Interaction.NO_ROTATION
        selectionOverlay.visibility = if (editMode) View.VISIBLE else View.GONE
        selectionOverlay.bringToFront()
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
    }

    private fun calculateTransformedBounds(target: View): RectF {
        val points = floatArrayOf(
            AppMagicNumbers.Common.FLOAT_ZERO, AppMagicNumbers.Common.FLOAT_ZERO,
            target.width.toFloat(), AppMagicNumbers.Common.FLOAT_ZERO,
            target.width.toFloat(), target.height.toFloat(),
            AppMagicNumbers.Common.FLOAT_ZERO, target.height.toFloat(),
        )

        val matrix = Matrix(target.matrix)
        matrix.postTranslate(target.left.toFloat(), target.top.toFloat())
        matrix.mapPoints(points)

        val xs = floatArrayOf(
            points[AppMagicNumbers.Common.INT_ZERO],
            points[AppMagicNumbers.Common.INT_TWO],
            points[AppMagicNumbers.Common.INT_FOUR],
            points[AppMagicNumbers.Common.INT_SIX]
        )
        val ys = floatArrayOf(
            points[AppMagicNumbers.Common.INT_ONE],
            points[AppMagicNumbers.Common.INT_THREE],
            points[AppMagicNumbers.Common.INT_FIVE],
            points[AppMagicNumbers.Common.INT_SEVEN]
        )

        val minX = xs.minOrNull() ?: AppMagicNumbers.Common.FLOAT_ZERO
        val maxX = xs.maxOrNull() ?: AppMagicNumbers.Common.FLOAT_ZERO
        val minY = ys.minOrNull() ?: AppMagicNumbers.Common.FLOAT_ZERO
        val maxY = ys.maxOrNull() ?: AppMagicNumbers.Common.FLOAT_ZERO

        return RectF(minX, minY, maxX, maxY)
    }

    private fun pointerDistance(event: MotionEvent): Float {
        val dx = event.getX(AppMagicNumbers.Common.INT_ZERO) - event.getX(AppMagicNumbers.Common.INT_ONE)
        val dy = event.getY(AppMagicNumbers.Common.INT_ZERO) - event.getY(AppMagicNumbers.Common.INT_ONE)
        return sqrt(dx * dx + dy * dy)
    }

    private fun pointerAngle(event: MotionEvent): Float {
        val dx = event.getX(AppMagicNumbers.Common.INT_ONE) - event.getX(AppMagicNumbers.Common.INT_ZERO)
        val dy = event.getY(AppMagicNumbers.Common.INT_ONE) - event.getY(AppMagicNumbers.Common.INT_ZERO)
        return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    }

    private fun applySmartMagnetSnap(
        draggedKey: String,
        groupStartPositions: Map<String, Pair<Float, Float>>,
        deltaX: Float,
        deltaY: Float,
    ): MagnetSnapResult {
        val draggedView = editableViews[draggedKey] ?: return MagnetSnapResult(deltaX, deltaY, null, null)
        val draggedStart = groupStartPositions[draggedKey] ?: (draggedView.x to draggedView.y)

        val maxX = (workoutRoot.width - draggedView.width).toFloat().coerceAtLeast(AppMagicNumbers.Common.FLOAT_ZERO)
        val maxY = (workoutRoot.height - draggedView.height).toFloat().coerceAtLeast(AppMagicNumbers.Common.FLOAT_ZERO)
        val candidateX = (draggedStart.first + deltaX).coerceIn(AppMagicNumbers.Common.FLOAT_ZERO, maxX)
        val candidateY = (draggedStart.second + deltaY).coerceIn(AppMagicNumbers.Common.FLOAT_ZERO, maxY)
        val candidateCenterX = candidateX + (draggedView.width / AppMagicNumbers.Common.FLOAT_TWO)
        val candidateCenterY = candidateY + (draggedView.height / AppMagicNumbers.Common.FLOAT_TWO)

        val thresholdPx = dpToPx(MAGNET_SNAP_THRESHOLD_DP)
        var closestCenterX: Float? = null
        var closestCenterY: Float? = null
        var closestDistanceX = Float.MAX_VALUE
        var closestDistanceY = Float.MAX_VALUE

        editableViews.forEach { (targetKey, targetView) ->
            if (targetKey in selectedElementKeys) return@forEach

            val targetCenterX = targetView.x + (targetView.width / AppMagicNumbers.Common.FLOAT_TWO)
            val targetCenterY = targetView.y + (targetView.height / AppMagicNumbers.Common.FLOAT_TWO)
            val distanceX = abs(targetCenterX - candidateCenterX)
            val distanceY = abs(targetCenterY - candidateCenterY)

            if (distanceX <= thresholdPx && distanceX < closestDistanceX) {
                closestDistanceX = distanceX
                closestCenterX = targetCenterX
            }
            if (distanceY <= thresholdPx && distanceY < closestDistanceY) {
                closestDistanceY = distanceY
                closestCenterY = targetCenterY
            }
        }

        // Center magnet should activate on only one axis at a time.
        // Keep the stronger axis and leave the other axis for spacing smart guides.
        if (closestCenterX != null && closestCenterY != null) {
            if (closestDistanceX <= closestDistanceY) {
                closestCenterY = null
            } else {
                closestCenterX = null
            }
        }

        val spacingCenterY = findEqualSpacingCenterY(
            alignedCenterX = closestCenterX ?: candidateCenterX,
            candidateCenterY = candidateCenterY,
            thresholdPx = thresholdPx,
        )
        val spacingCenterX = findEqualSpacingCenterX(
            alignedCenterY = closestCenterY ?: candidateCenterY,
            candidateCenterX = candidateCenterX,
            thresholdPx = thresholdPx,
        )

        var snappedCenterX: Float? = closestCenterX ?: spacingCenterX
        var snappedCenterY: Float? = closestCenterY ?: spacingCenterY

        // Keep center magnet active even when close/overlapping.
        // Overlap suppression is applied only to spacing-based snap.
        val usesSpacingX = closestCenterX == null && spacingCenterX != null
        val usesSpacingY = closestCenterY == null && spacingCenterY != null
        if (usesSpacingX || usesSpacingY) {
            val resolvedCenterX = snappedCenterX ?: candidateCenterX
            val resolvedCenterY = snappedCenterY ?: candidateCenterY
            if (wouldOverlapAnyUnselectedView(draggedKey, resolvedCenterX, resolvedCenterY)) {
                if (usesSpacingX) snappedCenterX = null
                if (usesSpacingY) snappedCenterY = null
            }
        }

        val snappedDeltaX = if (snappedCenterX != null) {
            deltaX + (snappedCenterX - candidateCenterX)
        } else {
            deltaX
        }
        val snappedDeltaY = if (snappedCenterY != null) {
            deltaY + (snappedCenterY - candidateCenterY)
        } else {
            deltaY
        }

        return MagnetSnapResult(
            deltaX = snappedDeltaX,
            deltaY = snappedDeltaY,
            snappedCenterX = snappedCenterX,
            snappedCenterY = snappedCenterY,
        )
    }

    private fun findEqualSpacingCenterY(
        alignedCenterX: Float,
        candidateCenterY: Float,
        thresholdPx: Float,
    ): Float? {
        val alignedTargets = editableViews
            .filter { (key, _) -> key !in selectedElementKeys }
            .map { (_, view) ->
                (view.x + (view.width / AppMagicNumbers.Common.FLOAT_TWO)) to (view.y + (view.height / AppMagicNumbers.Common.FLOAT_TWO))
            }
            .filter { (centerX, _) -> abs(centerX - alignedCenterX) <= thresholdPx }

        if (alignedTargets.size < AppMagicNumbers.Common.INT_TWO) return null

        var bestCenterY: Float? = null
        var bestDistance = Float.MAX_VALUE

        for (i in AppMagicNumbers.Common.INT_ZERO until alignedTargets.lastIndex) {
            val firstY = alignedTargets[i].second
            for (j in i + AppMagicNumbers.Common.INT_ONE until alignedTargets.size) {
                val secondY = alignedTargets[j].second
                val gap = abs(firstY - secondY)
                if (gap < AppMagicNumbers.Common.FLOAT_ONE) continue

                val minY = minOf(firstY, secondY)
                val maxY = maxOf(firstY, secondY)
                val midpointY = (firstY + secondY) / AppMagicNumbers.Common.FLOAT_TWO
                if (candidateCenterY in minY..maxY) {
                    val midpointDistance = abs(midpointY - candidateCenterY)
                    if (midpointDistance <= thresholdPx) {
                        return midpointY
                    }
                }

                val targets = floatArrayOf(
                    midpointY,
                    firstY - gap,
                    firstY + gap,
                    secondY - gap,
                    secondY + gap,
                )
                targets.forEach { targetCenterY ->
                    val distance = abs(targetCenterY - candidateCenterY)
                    if (distance <= thresholdPx && distance < bestDistance) {
                        bestDistance = distance
                        bestCenterY = targetCenterY
                    }
                }
            }
        }
        return bestCenterY
    }

    private fun findEqualSpacingCenterX(
        alignedCenterY: Float,
        candidateCenterX: Float,
        thresholdPx: Float,
    ): Float? {
        val alignedTargets = editableViews
            .filter { (key, _) -> key !in selectedElementKeys }
            .map { (_, view) ->
                (view.x + (view.width / AppMagicNumbers.Common.FLOAT_TWO)) to (view.y + (view.height / AppMagicNumbers.Common.FLOAT_TWO))
            }
            .filter { (_, centerY) -> abs(centerY - alignedCenterY) <= thresholdPx }

        if (alignedTargets.size < AppMagicNumbers.Common.INT_TWO) return null

        var bestCenterX: Float? = null
        var bestDistance = Float.MAX_VALUE

        for (i in AppMagicNumbers.Common.INT_ZERO until alignedTargets.lastIndex) {
            val firstX = alignedTargets[i].first
            for (j in i + AppMagicNumbers.Common.INT_ONE until alignedTargets.size) {
                val secondX = alignedTargets[j].first
                val gap = abs(firstX - secondX)
                if (gap < AppMagicNumbers.Common.FLOAT_ONE) continue

                val minX = minOf(firstX, secondX)
                val maxX = maxOf(firstX, secondX)
                val midpointX = (firstX + secondX) / AppMagicNumbers.Common.FLOAT_TWO
                if (candidateCenterX in minX..maxX) {
                    val midpointDistance = abs(midpointX - candidateCenterX)
                    if (midpointDistance <= thresholdPx) {
                        return midpointX
                    }
                }

                val targets = floatArrayOf(
                    midpointX,
                    firstX - gap,
                    firstX + gap,
                    secondX - gap,
                    secondX + gap,
                )
                targets.forEach { targetCenterX ->
                    val distance = abs(targetCenterX - candidateCenterX)
                    if (distance <= thresholdPx && distance < bestDistance) {
                        bestDistance = distance
                        bestCenterX = targetCenterX
                    }
                }
            }
        }
        return bestCenterX
    }

    private fun wouldOverlapAnyUnselectedView(
        draggedKey: String,
        draggedCenterX: Float,
        draggedCenterY: Float,
    ): Boolean {
        val draggedView = editableViews[draggedKey] ?: return false
        val draggedLeft = draggedCenterX - (draggedView.width / AppMagicNumbers.Common.FLOAT_TWO)
        val draggedTop = draggedCenterY - (draggedView.height / AppMagicNumbers.Common.FLOAT_TWO)
        val draggedRight = draggedLeft + draggedView.width
        val draggedBottom = draggedTop + draggedView.height

        editableViews.forEach { (targetKey, targetView) ->
            if (targetKey == draggedKey || targetKey in selectedElementKeys) return@forEach

            val targetLeft = targetView.x
            val targetTop = targetView.y
            val targetRight = targetLeft + targetView.width
            val targetBottom = targetTop + targetView.height
            val overlapX = draggedLeft < targetRight && draggedRight > targetLeft
            val overlapY = draggedTop < targetBottom && draggedBottom > targetTop

            if (overlapX && overlapY) {
                return true
            }
        }
        return false
    }

    private fun updateSnapGuideLines(result: MagnetSnapResult) {
        if (result.snappedCenterX != null) {
            snapGuideVertical.x = (result.snappedCenterX - (snapGuideVertical.width / AppMagicNumbers.Common.FLOAT_TWO)).coerceAtLeast(AppMagicNumbers.Common.FLOAT_ZERO)
            snapGuideVertical.visibility = View.VISIBLE
        } else {
            snapGuideVertical.visibility = View.GONE
        }

        if (result.snappedCenterY != null) {
            snapGuideHorizontal.y = (result.snappedCenterY - (snapGuideHorizontal.height / AppMagicNumbers.Common.FLOAT_TWO)).coerceAtLeast(AppMagicNumbers.Common.FLOAT_ZERO)
            snapGuideHorizontal.visibility = View.VISIBLE
        } else {
            snapGuideHorizontal.visibility = View.GONE
        }
    }

    private fun hideSnapGuideLines() {
        snapGuideVertical.visibility = View.GONE
        snapGuideHorizontal.visibility = View.GONE
    }

    private fun snapRotation(rotation: Float): Float {
        val normalized = normalizeRotation(rotation)
        val snapTargets = floatArrayOf(
            AppMagicNumbers.Interaction.NO_ROTATION,
            AppMagicNumbers.Interaction.DEGREE_90,
            AppMagicNumbers.Interaction.DEGREE_180,
            AppMagicNumbers.Interaction.DEGREE_270,
            AppMagicNumbers.Interaction.DEGREE_360,
        )
        var closest = normalized
        var minDistance = Float.MAX_VALUE

        for (target in snapTargets) {
            val distance = abs(target - normalized)
            if (distance < minDistance) {
                minDistance = distance
                closest = target
            }
        }

        return if (minDistance <= ROTATION_SNAP_THRESHOLD) {
            if (closest == AppMagicNumbers.Interaction.DEGREE_360) AppMagicNumbers.Interaction.NO_ROTATION else closest
        } else {
            normalized
        }
    }

    private fun normalizeRotation(rotation: Float): Float {
        var value = rotation % AppMagicNumbers.Interaction.DEGREE_360
        if (value < AppMagicNumbers.Interaction.NO_ROTATION) value += AppMagicNumbers.Interaction.DEGREE_360
        return value
    }

    private fun endSession() {
        timer?.cancel()
        timer = null
        setCount = AppMagicNumbers.Common.INT_ZERO
        totalCount = AppMagicNumbers.Common.INT_ZERO
        elapsedSeconds = AppMagicNumbers.Common.INT_ZERO
        controlButtonWindows.values.forEach { window ->
            windowManager.removeView(window.view)
        }
        controlButtonWindows.clear()
        overlayHostView?.let { view ->
            windowManager.removeView(view)
        }
        overlayHostView = null
        overlayLayoutParams = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        timer?.cancel()
        controlButtonWindows.values.forEach { window ->
            windowManager.removeView(window.view)
        }
        controlButtonWindows.clear()
        overlayHostView?.let { view ->
            windowManager.removeView(view)
        }
        overlayHostView = null
        overlayLayoutParams = null
        activityScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            AppMagicNumbers.Notification.CONTENT_INTENT_REQUEST_CODE,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val startIntent = PendingIntent.getService(
            this,
            101,
            Intent(this, OverlayService::class.java).apply { action = ACTION_START_TIMER },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val resetIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, OverlayService::class.java).apply { action = ACTION_RESET_SET },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val editIntent = PendingIntent.getService(
            this,
            103,
            Intent(this, OverlayService::class.java).apply { action = ACTION_ENABLE_EDIT_MODE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val endIntent = PendingIntent.getService(
            this,
            104,
            Intent(this, OverlayService::class.java).apply { action = ACTION_END_SESSION },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(
                if (editMode) {
                    "Overlay edit mode is active."
                } else {
                    "Overlay is touch-through. Use notification actions to control it."
                }
            )
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(0, "Start", startIntent)
            .addAction(0, "Reset", resetIntent)
            .addAction(0, "Edit", editIntent)
            .addAction(0, "End", endIntent)
            .build()
    }

    companion object {
        const val ACTION_START_SERVICE = "com.example.workouttimer.action.START_SERVICE"
        const val ACTION_END_SESSION = "com.example.workouttimer.action.END_SESSION"
        const val ACTION_START_TIMER = "com.example.workouttimer.action.START_TIMER"
        const val ACTION_RESET_SET = "com.example.workouttimer.action.RESET_SET"
        const val ACTION_ENABLE_EDIT_MODE = "com.example.workouttimer.action.ENABLE_EDIT_MODE"

        private const val NOTIFICATION_CHANNEL_ID = "workout_overlay_channel"
        private const val KEY_TIMER = "workout_timer"
        private const val KEY_SET = "workout_set"
        private const val KEY_TOTAL = "workout_total"
        private const val KEY_START = "workout_start"
        private const val KEY_RESET = "workout_reset"
        private const val KEY_EDIT = "workout_edit"
        private const val KEY_END = "workout_end"

        private const val MIN_SCALE = AppMagicNumbers.Interaction.MIN_SCALE
        private const val MAX_SCALE = AppMagicNumbers.Interaction.MAX_SCALE
        private const val ROTATION_SNAP_THRESHOLD = AppMagicNumbers.Interaction.ROTATION_SNAP_THRESHOLD
        private const val MAGNET_SNAP_THRESHOLD_DP = AppMagicNumbers.Interaction.MAGNET_SNAP_THRESHOLD_DP
        private const val BOX_PADDING_RATIO = AppMagicNumbers.Interaction.BOX_PADDING_RATIO
        private const val HANDLE_SIZE_DP = AppMagicNumbers.Interaction.HANDLE_SIZE_DP
        private const val RESIZE_SENSITIVITY = AppMagicNumbers.Interaction.RESIZE_SENSITIVITY
    }

    private data class ResizeHandle(
        val directionX: Float,
        val directionY: Float,
    )

    private data class ResizeScaleDelta(
        val scaleX: Float,
        val scaleY: Float,
    )

    private data class MagnetSnapResult(
        val deltaX: Float,
        val deltaY: Float,
        val snappedCenterX: Float?,
        val snappedCenterY: Float?,
    )

    private data class ControlButtonWindow(
        val sourceButton: Button,
        val view: Button,
        val layoutParams: WindowManager.LayoutParams,
    )

    private data class ViewTransformState(
        val x: Float?,
        val y: Float?,
        val scaleX: Float,
        val scaleY: Float,
        val rotation: Float,
    )
}




