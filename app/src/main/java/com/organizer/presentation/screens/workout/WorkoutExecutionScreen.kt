package com.organizer.presentation.screens.workout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.ExerciseProgressEntity
import com.organizer.data.local.db.entities.PlannedSetEntity
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.screens.exercises.ExerciseImageAnimation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration.Companion.seconds

private const val DURATION_TYPE = TYPE_DURATION
private const val DEFAULT_DURATION_SECONDS = 45
private const val DEFAULT_REST_SECONDS = 90

@Composable
fun WorkoutExecutionScreen(
    workoutId: Long,
    onBackClick: () -> Unit,
    viewModel: OrganizerViewModel,
) {
    // Hoist the observed flows: creating them inline would restart collection on
    // every recomposition (the elapsed timer recomposes the screen every second)
    // and briefly reset the data to its initial value each time.
    val workoutFlow = remember(workoutId) { viewModel.observeWorkout(workoutId) }
    val exercisesFlow = remember(workoutId) { viewModel.observeWorkoutExercises(workoutId) }
    val workout by workoutFlow.collectAsState(initial = null)
    val exercises by exercisesFlow.collectAsState(initial = emptyList())
    val workoutName = workout?.name ?: "Workout"
    val total = exercises.size

    // Session state survives configuration changes (rotation, split screen).
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    // Exercises marked "Done" (skipped ones do not count), so the completion
    // summary does not claim the user finished exercises they skipped.
    var completedCount by rememberSaveable { mutableIntStateOf(0) }
    val finished = total > 0 && currentIndex >= total
    val current: ExerciseEntity? =
        if (finished || total == 0) null else exercises.getOrNull(currentIndex)
    val nextExercise: ExerciseEntity? =
        if (finished || total == 0) null else exercises.getOrNull(currentIndex + 1)

    // Overall elapsed workout timer (counts up from 0).
    var elapsedSeconds by rememberSaveable { mutableIntStateOf(0) }
    var elapsedRunning by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(elapsedRunning) {
        while (elapsedRunning) {
            delay(1.seconds)
            elapsedSeconds++
        }
    }
    LaunchedEffect(finished) {
        if (finished) elapsedRunning = false
    }

    // One id per execution. Progress is numbered and displayed per session, so
    // a new workout starts at "Set 1" instead of continuing last week's list.
    var sessionId by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }

    // Sets are observed for the current exercise and session only. A stable
    // flow keeps the card from blinking when the parent recomposes.
    val currentExerciseId = current?.exerciseId
    val setsFlow = remember(workoutId, currentExerciseId, sessionId) {
        currentExerciseId?.let { viewModel.observeSessionSets(workoutId, it, sessionId) }
            ?: flowOf<List<ExerciseProgressEntity>?>(null)
    }
    val sessionSetsState by setsFlow.collectAsState(initial = null)
    val trackedSets = sessionSetsState ?: emptyList()

    // Targets defined for the routine. `null` means "still loading", which is
    // different from "this exercise has no plan".
    val plannedSetsFlow = remember(workoutId, currentExerciseId) {
        currentExerciseId?.let { viewModel.observePlannedSets(workoutId, it) }
            ?: flowOf<List<PlannedSetEntity>?>(null)
    }
    val plannedSetsState by plannedSetsFlow.collectAsState(initial = null)
    val plannedSets = plannedSetsState ?: emptyList()

    // The type shown follows the sets recorded during this session, matching
    // the workout detail screen instead of ignoring newly logged sets.
    val currentType = current?.let { effectiveExerciseType(it, trackedSets) }

    // First planned set that has not been logged yet: it is the "current" row
    // and its duration is the target for the exercise timer.
    val nextPlannedSet = plannedSets.firstOrNull { planned ->
        trackedSets.none { it.setNumber == planned.setNumber }
    }
    val plannedTargetDuration = nextPlannedSet?.durationSeconds?.takeIf { it > 0 }

    // Duration timer for the current exercise.
    var timerSeconds by rememberSaveable { mutableIntStateOf(0) }
    var timerTotal by rememberSaveable { mutableIntStateOf(DEFAULT_DURATION_SECONDS) }
    var timerRunning by rememberSaveable { mutableStateOf(false) }
    var timerDone by rememberSaveable { mutableStateOf(false) }
    // The exercise and planned set the countdown was last started for. Keeping
    // both makes the setup idempotent after rotation, while still restarting
    // with the next set's target once a set is logged. `-1` means "no plan".
    var timerInitializedExercise by rememberSaveable { mutableStateOf(0L) }
    var timerInitializedSet by rememberSaveable { mutableIntStateOf(-1) }

    LaunchedEffect(currentExerciseId, currentType, plannedSetsState, nextPlannedSet?.setNumber) {
        if (currentType != DURATION_TYPE) {
            // Keep an already-started timer of this exercise: while sets reload
            // after a configuration change the effective type can briefly fall
            // back to the stored type.
            if (timerInitializedExercise != currentExerciseId) {
                timerRunning = false
            }
            return@LaunchedEffect
        }
        // Wait for the plan so the countdown uses the target duration.
        if (plannedSetsState == null) return@LaunchedEffect
        // The whole plan is done: keep the finished timer instead of starting
        // a default one.
        if (plannedSets.isNotEmpty() && nextPlannedSet == null) return@LaunchedEffect

        val targetSetNumber = nextPlannedSet?.setNumber ?: -1
        if (
            timerInitializedExercise == currentExerciseId &&
            timerInitializedSet == targetSetNumber
        ) {
            return@LaunchedEffect
        }

        timerInitializedExercise = currentExerciseId ?: 0L
        timerInitializedSet = targetSetNumber
        val target = plannedTargetDuration ?: DEFAULT_DURATION_SECONDS
        timerDone = false
        timerTotal = target
        timerSeconds = target
        timerRunning = true
    }

    LaunchedEffect(timerRunning) {
        while (timerRunning) {
            delay(1.seconds)
            if (timerSeconds > 0) timerSeconds--
            if (timerSeconds <= 0) {
                timerRunning = false
                timerDone = true
            }
        }
    }

    // Rest timer, started automatically after a set is logged.
    var restRemaining by rememberSaveable { mutableIntStateOf(DEFAULT_REST_SECONDS) }
    var restTotal by rememberSaveable { mutableIntStateOf(DEFAULT_REST_SECONDS) }
    var restRunning by rememberSaveable { mutableStateOf(false) }
    var restAutoEnabled by rememberSaveable { mutableStateOf(true) }
    // Amount of sets the timer was last synced with, per exercise. Switching
    // exercises adopts the new count instead of starting a rest period for
    // sets that were logged earlier.
    var restExerciseId by rememberSaveable { mutableStateOf(0L) }
    var restTriggeredSetCount by rememberSaveable { mutableIntStateOf(-1) }

    LaunchedEffect(currentExerciseId, sessionSetsState) {
        val sets = sessionSetsState ?: return@LaunchedEffect
        val exerciseKey = currentExerciseId ?: 0L
        if (restExerciseId != exerciseKey || restTriggeredSetCount < 0) {
            // First load (also after rotation): adopt the current amount
            // instead of firing a rest period for sets logged earlier.
            restExerciseId = exerciseKey
            restTriggeredSetCount = sets.size
            return@LaunchedEffect
        }
        if (restAutoEnabled && sets.size > restTriggeredSetCount) {
            restTotal = DEFAULT_REST_SECONDS
            restRemaining = DEFAULT_REST_SECONDS
            restRunning = true
        }
        restTriggeredSetCount = sets.size
    }

    LaunchedEffect(restRunning) {
        while (restRunning) {
            delay(1.seconds)
            if (restRemaining > 0) restRemaining--
            if (restRemaining <= 0) restRunning = false
        }
    }

    LaunchedEffect(finished) {
        if (finished) restRunning = false
    }

    LaunchedEffect(exercises) {
        if (exercises.isNotEmpty()) viewModel.prioritizeWorkoutMedia(exercises)
    }

    // A workout that was deleted while the screen was opening would otherwise
    // leave the screen spinning forever.
    var unavailable by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(workout, exercises) {
        if (workout != null || exercises.isNotEmpty()) {
            // Recover if the data arrives after the timeout fired.
            unavailable = false
            return@LaunchedEffect
        }
        delay(3.seconds)
        if (workout == null && exercises.isEmpty()) unavailable = true
    }

    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    var showTrackDialog by rememberSaveable { mutableStateOf(false) }
    // "Log Set N" opens the tracker directly in the new-set editor, while
    // tapping a logged set opens it on the edit view of that set.
    var trackDialogStartAdding by rememberSaveable { mutableStateOf(false) }
    var showPlanDialog by rememberSaveable { mutableStateOf(false) }
    // Target values copied into the new-set editor so logging a planned set
    // starts from the routine instead of zero.
    var trackPrefillReps by rememberSaveable { mutableStateOf<Int?>(null) }
    var trackPrefillWeight by rememberSaveable { mutableStateOf<Double?>(null) }
    var trackPrefillDuration by rememberSaveable { mutableStateOf<Int?>(null) }

    fun openTrackDialog(startAdding: Boolean, target: PlannedSetEntity?) {
        trackPrefillReps = target?.reps
        trackPrefillWeight = target?.weight
        trackPrefillDuration = target?.durationSeconds
        trackDialogStartAdding = startAdding
        showTrackDialog = true
    }

    val hasActiveWorkout = total > 0 && current != null && !finished && !unavailable

    BackHandler(enabled = hasActiveWorkout && !showExitDialog) {
        showExitDialog = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ExecutionBackground),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExecutionHeader(
                workoutName = workoutName,
                currentIndex = currentIndex,
                total = total,
                elapsedSeconds = elapsedSeconds,
                onBackClick = {
                    if (hasActiveWorkout) showExitDialog = true else onBackClick()
                },
            )

            Box(modifier = Modifier.weight(1f)) {
                when {
                    unavailable -> UnavailableState(onBackClick = onBackClick)

                    workout == null && exercises.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ExecutionBrand)
                        }
                    }

                    total == 0 -> EmptyWorkoutState(onBackClick = onBackClick)

                    finished -> Unit // The completion overlay covers the screen.

                    current != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                                .padding(top = 12.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ExerciseHeroCard(
                                exercise = current,
                                workoutNote = workout?.notes?.trim().orEmpty(),
                                typeLabel = exerciseTypeLabel(currentType ?: current.exerciseType),
                                viewModel = viewModel,
                            )

                            SetsCard(
                                loggedSets = trackedSets,
                                plannedSets = plannedSets,
                                nextSetNumber = (trackedSets.maxOfOrNull { it.setNumber } ?: 0) + 1,
                                onEditPlan = { showPlanDialog = true },
                                onOpenTracker = { openTrackDialog(false, null) },
                                onLogSet = { target -> openTrackDialog(true, target) },
                            )

                            if (trackedSets.isNotEmpty() || restRunning) {
                                RestTimerCard(
                                    seconds = restRemaining,
                                    total = restTotal,
                                    running = restRunning,
                                    autoEnabled = restAutoEnabled,
                                    onAdd15 = {
                                        restRemaining += 15
                                        restTotal += 15
                                    },
                                    onToggle = {
                                        if (restRemaining <= 0) {
                                            restRemaining = restTotal
                                            restRunning = true
                                        } else {
                                            restRunning = !restRunning
                                        }
                                    },
                                    onSkip = {
                                        restRunning = false
                                        restRemaining = 0
                                    },
                                    onToggleAuto = { restAutoEnabled = !restAutoEnabled },
                                )
                            }

                            if (currentType == DURATION_TYPE) {
                                ExerciseTimerCard(
                                    seconds = timerSeconds,
                                    total = timerTotal,
                                    running = timerRunning,
                                    done = timerDone,
                                    onAdd15 = {
                                        timerSeconds += 15
                                        timerTotal += 15
                                        if (timerDone) {
                                            timerDone = false
                                            timerRunning = true
                                        }
                                    },
                                    onToggle = {
                                        if (timerDone || timerSeconds <= 0) {
                                            timerDone = false
                                            timerSeconds = timerTotal
                                            timerRunning = true
                                        } else {
                                            timerRunning = !timerRunning
                                        }
                                    },
                                    onSkip = {
                                        timerRunning = false
                                        timerDone = true
                                        timerSeconds = 0
                                    },
                                )
                            }

                            UpNextCard(
                                nextExercise = nextExercise,
                                currentIndex = currentIndex,
                                total = total,
                            )
                        }
                    }
                }
            }

            if (!finished && !unavailable && total > 0) {
                ExecutionActionBar(
                    onDone = {
                        completedCount++
                        currentIndex++
                    },
                    onSkip = { currentIndex++ },
                    onFinish = {
                        elapsedRunning = false
                        currentIndex = total
                    },
                )
            }
        }

        if (finished) {
            CompletionOverlay(
                completed = completedCount,
                total = total,
                onBackClick = onBackClick,
            )
        }
    }

    if (showExitDialog) {
        ExitWorkoutDialog(
            onConfirm = {
                showExitDialog = false
                onBackClick()
            },
            onDismiss = { showExitDialog = false },
        )
    }

    val trackedExercise = current
    if (showTrackDialog && trackedExercise != null) {
        TrackExerciseDialog(
            workoutId = workoutId,
            exercise = trackedExercise,
            sets = trackedSets,
            viewModel = viewModel,
            startAddingSet = trackDialogStartAdding,
            sessionId = sessionId,
            initialReps = trackPrefillReps,
            initialWeight = trackPrefillWeight,
            initialDurationSeconds = trackPrefillDuration,
            onDismiss = {
                showTrackDialog = false
                trackDialogStartAdding = false
                trackPrefillReps = null
                trackPrefillWeight = null
                trackPrefillDuration = null
            },
        )
    }

    if (showPlanDialog && current != null) {
        PlannedSetsDialog(
            workoutId = workoutId,
            exercise = current,
            // The routine owns the target type; the session type can drift when
            // the user logs a set in another mode.
            exerciseType = current.exerciseType,
            plannedSets = plannedSets,
            viewModel = viewModel,
            onDismiss = { showPlanDialog = false },
        )
    }
}

@Composable
private fun ExecutionHeader(
    workoutName: String,
    currentIndex: Int,
    total: Int,
    elapsedSeconds: Int,
    onBackClick: () -> Unit,
) {
    Surface(
        color = ExecutionSurface,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onBackClick,
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = ExecutionSurfaceStrong,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit workout",
                            modifier = Modifier.size(20.dp),
                            tint = ExecutionInk,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = workoutName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ExecutionInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (total > 0) {
                        Text(
                            text = "Exercise ${currentIndex.coerceAtMost(total - 1) + 1} of $total",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ExecutionBrand,
                            maxLines = 1,
                        )
                    }
                }

                ElapsedTimerPill(elapsedSeconds)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(total) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                when {
                                    index < currentIndex -> ExecutionGreen
                                    index == currentIndex -> ExecutionBrand
                                    else -> ExecutionFieldBorder
                                }
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ElapsedTimerPill(seconds: Int) {
    val pulse = rememberInfiniteTransition(label = "elapsedPulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "elapsedPulseAlpha",
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ExecutionBrand50)
            .border(1.dp, ExecutionBrand200.copy(alpha = 0.8f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(ExecutionBrand.copy(alpha = dotAlpha)),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = formatElapsed(seconds),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = ExecutionBrandDark,
        )
    }
}

@Composable
private fun ExerciseHeroCard(
    exercise: ExerciseEntity,
    workoutNote: String,
    typeLabel: String,
    viewModel: OrganizerViewModel,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = ExecutionSurface,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, ExecutionCardBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TypePill(text = typeLabel.uppercase())

            Spacer(Modifier.height(8.dp))

            if (exercise.imageUrls.isNotEmpty()) {
                // Let the illustration keep its aspect ratio (the component
                // draws with FillWidth). Constraining the height alone would
                // crop the picture to a horizontal band.
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    ExerciseImageAnimation(
                        imageUrls = exercise.imageUrls,
                        exerciseName = exercise.name,
                        isCustom = exercise.isCustom,
                        viewModel = viewModel,
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ExecutionSurfaceMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = ExecutionInkMuted,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = exercise.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = ExecutionInk,
                modifier = Modifier.fillMaxWidth(),
            )

            val instruction = exercise.instructions.firstOrNull()?.takeIf { it.isNotBlank() }
            if (instruction != null) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = ExecutionBrand50.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, ExecutionBrand100),
                ) {
                    Text(
                        text = instruction,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = ExecutionInkSecondary,
                    )
                }
            }

            if (workoutNote.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = ExecutionBrand50.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, ExecutionBrand100),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = ExecutionBrand,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = workoutNote,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = ExecutionInkSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypePill(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = ExecutionBrand100,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = ExecutionBrandDark,
        )
    }
}

@Composable
private fun SetsCard(
    loggedSets: List<ExerciseProgressEntity>,
    plannedSets: List<PlannedSetEntity>,
    nextSetNumber: Int,
    onEditPlan: () -> Unit,
    onOpenTracker: () -> Unit,
    onLogSet: (PlannedSetEntity?) -> Unit,
) {
    val hasPlan = plannedSets.isNotEmpty()
    val doneCount = plannedSets.count { planned ->
        loggedSets.any { it.setNumber == planned.setNumber }
    }
    val currentPlanned = plannedSets.firstOrNull { planned ->
        loggedSets.none { it.setNumber == planned.setNumber }
    }
    // Sets logged beyond the plan stay visible as extra green rows.
    val extraLoggedSets = loggedSets.filter { logged ->
        plannedSets.none { it.setNumber == logged.setNumber }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = ExecutionSurface,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, ExecutionCardBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SETS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = ExecutionInk,
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ExecutionSurfaceStrong,
                ) {
                    Text(
                        text = if (hasPlan) {
                            "$doneCount / ${plannedSets.size} done"
                        } else {
                            "${loggedSets.size} logged"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ExecutionInkSecondary,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEditPlan) {
                    Text(
                        text = "Plan",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ExecutionBrand,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasPlan) {
                    plannedSets.forEach { planned ->
                        val logged = loggedSets.firstOrNull {
                            it.setNumber == planned.setNumber
                        }
                        val isCurrent = currentPlanned?.setNumber == planned.setNumber

                        PlannedSetRow(
                            planned = planned,
                            logged = logged,
                            isCurrent = isCurrent,
                            onClick = when {
                                logged != null -> onOpenTracker
                                isCurrent -> ({ onLogSet(planned) })
                                else -> null
                            },
                        )
                    }

                    extraLoggedSets.forEach { set ->
                        LoggedSetRow(set = set, onClick = onOpenTracker)
                    }

                    if (currentPlanned == null) {
                        AddSetRow(setNumber = nextSetNumber, onClick = { onLogSet(null) })
                    }
                } else {
                    loggedSets.forEach { set ->
                        LoggedSetRow(set = set, onClick = onOpenTracker)
                    }
                    AddSetRow(setNumber = nextSetNumber, onClick = { onLogSet(null) })
                }
            }
        }
    }
}

/**
 * One row of the routine: green when the set was logged, brand-outlined while
 * it is the next set to do, muted for everything still ahead.
 */
@Composable
private fun PlannedSetRow(
    planned: PlannedSetEntity,
    logged: ExerciseProgressEntity?,
    isCurrent: Boolean,
    onClick: (() -> Unit)?,
) {
    val done = logged != null
    val shape = RoundedCornerShape(16.dp)
    val background = when {
        done -> ExecutionGreenLight.copy(alpha = 0.6f)
        isCurrent -> ExecutionSurface
        else -> ExecutionSurfaceMuted
    }
    val borderColor = when {
        done -> ExecutionGreenBorder.copy(alpha = 0.8f)
        isCurrent -> ExecutionBrand
        else -> ExecutionFieldBorder
    }
    val numberBackground = when {
        done -> ExecutionGreenDark
        isCurrent -> ExecutionBrand
        else -> ExecutionSurfaceStrong
    }

    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = background,
        border = BorderStroke(if (isCurrent) 2.dp else 1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(numberBackground),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = planned.setNumber.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (done || isCurrent) Color.White else ExecutionInkMuted,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Set",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (done || isCurrent) ExecutionInk else ExecutionInkMuted,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (logged != null) setSummary(logged) else plannedSetSummary(planned),
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (done || isCurrent) ExecutionInk else ExecutionInkMuted,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (done) ExecutionGreen else Color.Transparent)
                    .border(
                        width = if (done) 0.dp else 1.5.dp,
                        color = if (isCurrent) ExecutionBrand else ExecutionFieldBorder,
                        shape = RoundedCornerShape(9.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Set ${planned.setNumber} logged",
                        modifier = Modifier.size(18.dp),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoggedSetRow(
    set: ExerciseProgressEntity,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ExecutionGreenLight.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, ExecutionGreenBorder.copy(alpha = 0.8f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(ExecutionGreenDark),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = set.setNumber.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Set",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ExecutionInk,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = setSummary(set),
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ExecutionInk,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(ExecutionGreen),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Set ${set.setNumber} logged",
                    modifier = Modifier.size(18.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun AddSetRow(
    setNumber: Int,
    onClick: () -> Unit,
) {
    val borderColor = ExecutionBrand.copy(alpha = 0.4f)
    val cornerRadius = 16.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(ExecutionBrand50.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .drawBehind {
                val strokeWidth = 2.dp.toPx()
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(12.dp.toPx(), 8.dp.toPx())
                        ),
                    ),
                )
            }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.AddCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = ExecutionBrand,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Log Set $setNumber",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ExecutionBrand,
            )
        }
    }
}

@Composable
private fun ExerciseTimerCard(
    seconds: Int,
    total: Int,
    running: Boolean,
    done: Boolean,
    onAdd15: () -> Unit,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
) {
    val progress = if (total > 0) (total - seconds).toFloat() / total.toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(300),
        label = "timerProgress",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = ExecutionSurface,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, ExecutionCardBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = ExecutionBrand,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "EXERCISE TIMER",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = ExecutionInk,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ExecutionBrand50,
                ) {
                    Text(
                        text = when {
                            done -> "Complete"
                            running -> "Running"
                            else -> "Paused"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ExecutionBrandDark,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(144.dp)) {
                Canvas(modifier = Modifier.size(144.dp)) {
                    val stroke = 8.dp.toPx()
                    val diameter = size.minDimension - stroke
                    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                    drawArc(
                        color = ExecutionSurfaceStrong,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = if (done) ExecutionGreen else ExecutionBrand,
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatSeconds(seconds),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 30.sp,
                        color = if (done) ExecutionGreen else ExecutionInk,
                    )
                    Text(
                        text = if (total > 0) "TARGET ${formatSeconds(total)}" else "SECONDS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = ExecutionInkMuted,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryTimerButton(
                    label = "+15s",
                    onClick = onAdd15,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onToggle,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExecutionBrand,
                        contentColor = Color.White,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(
                        imageVector = if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = when {
                            done -> "Start"
                            running -> "Pause"
                            else -> "Resume"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                SecondaryTimerButton(
                    label = "Skip",
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SecondaryTimerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ExecutionSurfaceStrong,
            contentColor = ExecutionInkSecondary,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun RestTimerCard(
    seconds: Int,
    total: Int,
    running: Boolean,
    autoEnabled: Boolean,
    onAdd15: () -> Unit,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onToggleAuto: () -> Unit,
) {
    // Ring depletes as the rest elapses, matching the design reference.
    val progress = if (total > 0) seconds.toFloat() / total.toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(300),
        label = "restProgress",
    )
    val finished = seconds <= 0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = ExecutionSurface,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, ExecutionCardBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = ExecutionBrand,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "REST BETWEEN SETS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = ExecutionInk,
                    )
                }
                Surface(
                    onClick = onToggleAuto,
                    shape = RoundedCornerShape(50),
                    color = if (autoEnabled) ExecutionBrand50 else ExecutionSurfaceStrong,
                ) {
                    Text(
                        text = if (autoEnabled) "Auto-Rest Active" else "Auto-Rest Off",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (autoEnabled) ExecutionBrandDark else ExecutionInkSecondary,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(144.dp)) {
                Canvas(modifier = Modifier.size(144.dp)) {
                    val stroke = 8.dp.toPx()
                    val diameter = size.minDimension - stroke
                    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                    drawArc(
                        color = ExecutionSurfaceStrong,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = if (finished) ExecutionGreen else ExecutionBrand,
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatSeconds(seconds),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 30.sp,
                        color = if (finished) ExecutionGreen else ExecutionInk,
                    )
                    Text(
                        text = "SECONDS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = ExecutionInkMuted,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryTimerButton(
                    label = "+15s",
                    onClick = onAdd15,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onToggle,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExecutionBrand,
                        contentColor = Color.White,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(
                        imageVector = if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = when {
                            running -> "Pause"
                            finished -> "Start"
                            seconds >= total -> "Start"
                            else -> "Resume"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                SecondaryTimerButton(
                    label = "Skip Rest",
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun UpNextCard(
    nextExercise: ExerciseEntity?,
    currentIndex: Int,
    total: Int,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(ExecutionBrand50, Color(0xFFEEF2FF))
                )
            )
            .border(1.dp, ExecutionBrand200.copy(alpha = 0.5f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ExecutionSurface)
                .border(1.dp, ExecutionBrand100, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = ExecutionBrand,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = "UP NEXT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = ExecutionBrand,
            )
            Text(
                text = nextExercise?.name ?: "Workout Complete!",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ExecutionInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = nextExercise?.let { exerciseTypeLabel(it.exerciseType) }
                    ?: "Cool down & hydrate",
                fontSize = 11.sp,
                color = ExecutionInkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = "#${if (nextExercise != null) currentIndex + 2 else total}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = ExecutionInkMuted,
        )
    }
}

@Composable
private fun ExecutionActionBar(
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
) {
    Surface(
        color = ExecutionSurface,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ExecutionGreen,
                    contentColor = Color.White,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Done",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SecondaryActionButton(
                    label = "Skip Exercise",
                    background = ExecutionSurfaceStrong,
                    contentColor = ExecutionInkSecondary,
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                )
                SecondaryActionButton(
                    label = "Finish Workout",
                    background = ExecutionBrand50,
                    contentColor = ExecutionBrand,
                    onClick = onFinish,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SecondaryActionButton(
    label: String,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = contentColor,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CompletionOverlay(
    completed: Int,
    total: Int,
    onBackClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ExecutionBrand900.copy(alpha = 0.82f))
            // Swallow taps so the header and content behind the overlay cannot
            // be activated while the workout is complete.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 340.dp)
                .padding(24.dp),
            shape = RoundedCornerShape(28.dp),
            color = ExecutionSurface,
            shadowElevation = 24.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(ExecutionGreenLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = ExecutionGreenDark,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Workout Complete!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = ExecutionInk,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (completed >= total) {
                        "Outstanding effort! You finished all $total exercises."
                    } else {
                        "Outstanding effort! You finished $completed of $total exercises."
                    },
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = ExecutionInkSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onBackClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExecutionBrand,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = "Back to Schedule",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyWorkoutState(onBackClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No exercises in this workout yet.",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = ExecutionInk,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onBackClick,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = ExecutionBrand),
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun UnavailableState(onBackClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "This workout is no longer available.",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = ExecutionInk,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onBackClick,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = ExecutionBrand),
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun ExitWorkoutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Exit workout?",
                fontWeight = FontWeight.Bold,
                color = ExecutionInk,
            )
        },
        text = {
            Text(
                text = "Your logged sets stay saved, but the current workout progress will be lost.",
                fontSize = 14.sp,
                color = ExecutionInkSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Exit",
                    fontWeight = FontWeight.Bold,
                    color = ExecutionInkSecondary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Keep going",
                    fontWeight = FontWeight.Bold,
                    color = ExecutionBrand,
                )
            }
        },
        containerColor = ExecutionSurface,
        titleContentColor = ExecutionInk,
        textContentColor = ExecutionInkSecondary,
    )
}

private fun setSummary(set: ExerciseProgressEntity): String = when (set.inferredExerciseType()) {
    DURATION_TYPE -> formatDuration(set.durationSeconds ?: 0)
    "REPS" -> "${set.reps ?: 0} reps"
    else -> "${formatWeight(set.weight)} × ${set.reps ?: 0} reps"
}

private fun formatElapsed(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val m = safe / 60
    val s = safe % 60
    return "%d:%02d".format(m, s)
}

private fun plannedSetSummary(planned: PlannedSetEntity): String = when {
    planned.durationSeconds != null -> formatDuration(planned.durationSeconds)
    planned.weight != null && planned.weight > 0.0 ->
        "${formatWeight(planned.weight)} × ${planned.reps ?: 0} reps"
    else -> "${planned.reps ?: 0} reps"
}

private fun formatSeconds(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val m = safe / 60
    val s = safe % 60
    return "%02d:%02d".format(m, s)
}
