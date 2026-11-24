package com.organizer.presentation.screens.workout

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import com.organizer.data.local.db.entities.ExerciseProgressEntity
import com.organizer.data.local.db.entities.PlannedSetEntity
import com.organizer.data.preferences.UnitSystem
import com.organizer.presentation.OrganizerViewModel
import com.organizer.ui.theme.AppTheme
import com.organizer.ui.theme.themedColor
import com.organizer.presentation.screens.exercises.ExerciseImageAnimation
import com.organizer.presentation.screens.exercises.InstructionPager
import com.organizer.presentation.screens.general.CelebrationMessage
import com.organizer.presentation.screens.general.CelebrationOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val DURATION_TYPE = TYPE_DURATION

@Composable
fun WorkoutExecutionScreen(
    workoutId: Long,
    onBackClick: () -> Unit,
    viewModel: OrganizerViewModel,
    unitSystem: UnitSystem = UnitSystem.METRIC,
) {
    // Hoist the observed flows: creating them inline would restart collection on
    // every recomposition (the elapsed timer recomposes the screen every second)
    // and briefly reset the data to its initial value each time.
    val workoutFlow = remember(workoutId) { viewModel.observeWorkout(workoutId) }
    val exercisesFlow = remember(workoutId) { viewModel.observeWorkoutExercises(workoutId) }
    val workout by workoutFlow.collectAsState(initial = null)
    val exercises by exercisesFlow.collectAsState(initial = emptyList())
    val workoutName = workout?.name ?: stringResource(R.string.workout_default_name)
    val total = exercises.size
    val context = LocalContext.current

    // Session state survives configuration changes (rotation, split screen).
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    // Exercises marked "Done" (skipped ones do not count), so the completion
    // summary does not claim the user finished exercises they skipped.
    var completedCount by rememberSaveable { mutableIntStateOf(0) }
    // The finished percentage is written to the schedule calendar once.
    var completionRecorded by rememberSaveable { mutableStateOf(false) }
    val finished = total > 0 && currentIndex >= total
    val current: ExerciseEntity? =
        if (finished || total == 0) null else exercises.getOrNull(currentIndex)
    val nextExercise: ExerciseEntity? =
        if (finished || total == 0) null else exercises.getOrNull(currentIndex + 1)

    // Overall elapsed workout timer (counts up from 0). The timer stores the
    // monotonic clock reading of its start instead of counting ticks: while
    // the app is in the background, the screen is off or the process is
    // frozen the ticker does not fire, but the displayed value is always
    // recomputed from the anchor, so that time is not lost. The anchor stays
    // unset until the pre-workout countdown has finished.
    var elapsedStartRealtime by rememberSaveable { mutableLongStateOf(NO_TIMER_ANCHOR) }
    var elapsedEndRealtime by rememberSaveable { mutableLongStateOf(NO_TIMER_ANCHOR) }
    var elapsedTickRealtime by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val elapsedRunning = elapsedStartRealtime != NO_TIMER_ANCHOR &&
        elapsedEndRealtime == NO_TIMER_ANCHOR
    LaunchedEffect(elapsedRunning) {
        while (elapsedRunning) {
            elapsedTickRealtime = SystemClock.elapsedRealtime()
            delay(1.seconds)
        }
    }
    LaunchedEffect(finished) {
        if (!finished) return@LaunchedEffect
        // A session restored from an older build may have no start anchor.
        if (elapsedStartRealtime == NO_TIMER_ANCHOR) {
            elapsedStartRealtime = SystemClock.elapsedRealtime()
        }
        if (elapsedEndRealtime == NO_TIMER_ANCHOR) {
            val now = SystemClock.elapsedRealtime()
            elapsedTickRealtime = now
            elapsedEndRealtime = now
        }
    }
    val elapsedSeconds = elapsedSecondsSince(
        startRealtime = elapsedStartRealtime,
        endRealtime = elapsedEndRealtime,
        nowRealtime = elapsedTickRealtime,
    )

    // One-shot finish celebration. The flag survives process death, so
    // restoring a finished session does not replay the toast.
    var celebration by remember {
        mutableStateOf<CelebrationMessage?>(null, referentialEqualityPolicy())
    }
    var completionCelebrated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(finished) {
        if (finished && !completionCelebrated) {
            completionCelebrated = true
            val finishedIn = elapsedSecondsSince(
                startRealtime = elapsedStartRealtime,
                endRealtime = elapsedEndRealtime,
                nowRealtime = SystemClock.elapsedRealtime(),
            )
            celebration = CelebrationMessage(
                title = context.getString(R.string.workout_complete_title),
                subtitle = context.getString(R.string.workout_finished_in, formatSeconds(finishedIn)),
            )
        }
    }

    // Only exercise outcomes are tracked: Done counts, Skip does not. The
    // percentage lands on today's date in the schedule calendar.
    LaunchedEffect(finished, total, completedCount) {
        if (finished && !completionRecorded && total > 0) {
            completionRecorded = true
            viewModel.recordWorkoutCompletion(
                workoutId = workoutId,
                completedExercises = completedCount,
                totalExercises = total,
            )
        }
    }

    // The player is read-only: it shows the sets the workout asks for and
    // never records results. Progress data is only used as a fallback below.
    val currentExerciseId = current?.exerciseId

    // Targets defined for the routine. `null` means "still loading", which is
    // different from "this exercise has no plan".
    val plannedSetsFlow = remember(workoutId, currentExerciseId) {
        currentExerciseId?.let { viewModel.observePlannedSets(workoutId, it) }
            ?: flowOf<List<PlannedSetEntity>?>(null)
    }
    val plannedSetsState by plannedSetsFlow.collectAsState(initial = null)
    val plannedSets = plannedSetsState ?: emptyList()

    // Fallback for exercises without a plan: the sets recorded during the
    // most recent execution of this workout.
    val historyFlow = remember(workoutId, currentExerciseId) {
        currentExerciseId?.let { viewModel.observeExerciseSets(workoutId, it) }
            ?: flowOf(emptyList())
    }
    val exerciseHistory by historyFlow.collectAsState(initial = emptyList())
    val latestSessionSets = remember(exerciseHistory) {
        val latestSession = exerciseHistory.maxOfOrNull { it.sessionId } ?: 0L
        exerciseHistory.filter { it.sessionId == latestSession }
    }

    // The type follows the last recorded execution (or the stored type when
    // there is no history).
    val currentType = current?.let { effectiveExerciseType(it, latestSessionSets) }

    // Rows of the set block: the routine's targets when they exist, otherwise
    // what the previous execution of this workout did.
    val executionSets = remember(plannedSetsState, latestSessionSets) {
        if (plannedSets.isNotEmpty()) {
            plannedSets
                .sortedBy { it.setNumber }
                .map { ExecutionSet(it.setNumber, plannedSetSummary(context, it, unitSystem)) }
        } else {
            latestSessionSets
                .sortedBy { it.setNumber }
                .map { ExecutionSet(it.setNumber, setSummary(context, it)) }
        }
    }

    // Checked sets live only in the UI: nothing is saved to the database.
    // Kept per exercise so rotation does not lose the ticks.
    var checkedSets by rememberSaveable(stateSaver = CheckedSetsSaver) {
        mutableStateOf(emptyMap<Long, Set<Int>>())
    }
    val checkedNumbers = currentExerciseId?.let { checkedSets[it] }.orEmpty()
    val allSetsChecked =
        executionSets.isEmpty() || executionSets.all { it.number in checkedNumbers }

    fun toggleSet(setNumber: Int, checked: Boolean) {
        val exerciseId = currentExerciseId ?: return
        val currentChecked = checkedSets[exerciseId].orEmpty()
        checkedSets = checkedSets + (
            exerciseId to if (checked) {
                currentChecked + setNumber
            } else {
                currentChecked - setNumber
            }
            )
    }

    // Stopwatch for duration exercises. It is not a countdown: the user
    // starts it, pauses/resumes it and stops it when the exercise is over.
    // Like the workout timer it keeps clock anchors rather than counting
    // ticks, so background time counts and a pause stays exactly frozen.
    var stopwatchState by rememberSaveable(stateSaver = StopwatchStateSaver) {
        mutableStateOf(StopwatchState())
    }
    var stopwatchStarted by rememberSaveable { mutableStateOf(false) }
    var stopwatchStopped by rememberSaveable { mutableStateOf(false) }
    var stopwatchTickRealtime by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val stopwatchRunning = stopwatchState.running
    // Which exercise the stopwatch belongs to, so moving to the next exercise
    // resets it while rotation keeps the running measurement.
    var stopwatchExercise by rememberSaveable { mutableStateOf(0L) }

    LaunchedEffect(currentExerciseId) {
        val exerciseId = currentExerciseId ?: 0L
        if (stopwatchExercise != exerciseId) {
            stopwatchExercise = exerciseId
            stopwatchState = StopwatchState()
            stopwatchStarted = false
            stopwatchStopped = false
        }
    }

    LaunchedEffect(stopwatchRunning) {
        while (stopwatchRunning) {
            // Refreshes the display; the value itself comes from the anchors.
            stopwatchTickRealtime = SystemClock.elapsedRealtime()
            delay(250.milliseconds)
        }
    }

    val stopwatchSeconds = stopwatchState.elapsedSeconds(stopwatchTickRealtime)

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

    // A freshly opened workout counts down before the timer starts. Both the
    // countdown and workout anchors are saved, so rotation, background time and
    // process death cannot replay the countdown or lose the elapsed time.
    // Restored, already running and finished sessions skip it entirely.
    val needsCountdown = total > 0 && !finished && !unavailable &&
        elapsedStartRealtime == NO_TIMER_ANCHOR
    var countdownAnchor by rememberSaveable { mutableLongStateOf(NO_TIMER_ANCHOR) }
    var countdownTickRealtime by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(needsCountdown) {
        if (!needsCountdown) return@LaunchedEffect
        val anchor = if (countdownAnchor == NO_TIMER_ANCHOR) {
            SystemClock.elapsedRealtime().also { countdownAnchor = it }
        } else {
            countdownAnchor
        }
        while (true) {
            val now = SystemClock.elapsedRealtime()
            countdownTickRealtime = now
            if (countdownRemaining(anchor, now) == 0) {
                elapsedStartRealtime = now
                return@LaunchedEffect
            }
            delay(100.milliseconds)
        }
    }
    val countdownSeconds = countdownRemaining(countdownAnchor, countdownTickRealtime)
    val showCountdown = needsCountdown && countdownSeconds > 0

    var showExitDialog by rememberSaveable { mutableStateOf(false) }

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
                            UpNextCard(
                                nextExercise = nextExercise,
                                currentIndex = currentIndex,
                                total = total,
                            )

                            ExerciseHeroCard(
                                exercise = current,
                                typeLabel = exerciseTypeLabel(currentType ?: current.exerciseType),
                                viewModel = viewModel,
                            )

                            SetsCard(
                                sets = executionSets,
                                checkedNumbers = checkedNumbers,
                                onToggleSet = { setNumber, checked ->
                                    toggleSet(setNumber, checked)
                                },
                            )

                            if (currentType == DURATION_TYPE) {
                                ExerciseStopwatchCard(
                                    seconds = stopwatchSeconds,
                                    started = stopwatchStarted,
                                    running = stopwatchRunning,
                                    stopped = stopwatchStopped,
                                    onStart = {
                                        stopwatchState = StopwatchState()
                                            .start(SystemClock.elapsedRealtime())
                                        stopwatchStarted = true
                                        stopwatchStopped = false
                                    },
                                    onPause = {
                                        stopwatchState = stopwatchState
                                            .pause(SystemClock.elapsedRealtime())
                                    },
                                    onResume = {
                                        stopwatchState = stopwatchState
                                            .resume(SystemClock.elapsedRealtime())
                                    },
                                    onStop = {
                                        stopwatchState = stopwatchState
                                            .stop(SystemClock.elapsedRealtime())
                                        stopwatchStopped = true
                                    },
                                )
                            }

                            val workoutNote = workout?.notes.orEmpty()
                            if (workoutNote.isNotBlank()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    color = ExecutionSurface,
                                    border = BorderStroke(1.dp, ExecutionCardBorder),
                                    shadowElevation = 1.dp,
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.EditNote,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = ExecutionBrand,
                                            )
                                            Text(
                                                text = stringResource(R.string.workout_note_label),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ExecutionInkSecondary,
                                            )
                                        }
                                        Text(
                                            text = workoutNote,
                                            fontSize = 14.sp,
                                            color = ExecutionInk,
                                            lineHeight = 20.sp,
                                        )
                                    }
                                }
                            }

                        }
                    }
                }
            }

            if (!finished && !unavailable && !showCountdown && total > 0) {
                ExecutionActionBar(
                    doneEnabled = allSetsChecked,
                    onDone = {
                        completedCount++
                        currentIndex++
                    },
                    onSkip = { currentIndex++ },
                )
            }
        }

        if (showCountdown) {
            StartCountdownOverlay(seconds = countdownSeconds)
        }

        if (finished) {
            CompletionOverlay(
                completed = completedCount,
                total = total,
                onBackClick = onBackClick,
            )
        }

        // Drawn last so the finish toast and confetti sit above the summary.
        CelebrationOverlay(
            message = celebration,
            onDismiss = { celebration = null },
        )
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
                            contentDescription = stringResource(R.string.workout_exit),
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
                            text = stringResource(R.string.workout_exercise_of, currentIndex.coerceAtMost(total - 1) + 1, total),
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
    typeLabel: String,
    viewModel: OrganizerViewModel,
) {
    // Step instructions stay tucked away by default so the image and the set
    // checklist get the space; the key resets the state per exercise.
    var instructionsExpanded by rememberSaveable(exercise.exerciseId) {
        mutableStateOf(false)
    }

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

            if (exercise.instructions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                InstructionToggle(
                    expanded = instructionsExpanded,
                    onClick = { instructionsExpanded = !instructionsExpanded },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                AnimatedVisibility(visible = instructionsExpanded) {
                    // Same component as the exercise card: step cards with the
                    // horizontal step paging and progress dots. Re-keyed per
                    // exercise so every exercise starts at step 1.
                    key(exercise.exerciseId) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            InstructionPager(instructions = exercise.instructions)
                        }
                    }
                }
            }
        }
    }
}

/** Small pill that expands the exercise's step instructions in place. */
@Composable
private fun InstructionToggle(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = ExecutionBrand50,
        border = BorderStroke(1.dp, ExecutionBrand100),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = ExecutionBrand,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (expanded) stringResource(R.string.workout_hide_instructions) else stringResource(R.string.workout_instructions),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ExecutionBrand,
            )
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

private val CheckedSetsSaver: Saver<Map<Long, Set<Int>>, List<String>> = Saver(
    save = { checked ->
        checked.map { (exerciseId, sets) -> "$exerciseId:${sets.sorted().joinToString(",")}" }
    },
    restore = { entries ->
        entries.mapNotNull { entry ->
            val separator = entry.indexOf(':')
            if (separator <= 0) return@mapNotNull null
            val exerciseId = entry.substring(0, separator).toLongOrNull()
                ?: return@mapNotNull null
            val sets = entry.substring(separator + 1)
                .split(',')
                .mapNotNull { it.toIntOrNull() }
                .toSet()
            exerciseId to sets
        }.toMap()
    },
)

/** One row of the read-only set checklist. */
private data class ExecutionSet(
    val number: Int,
    val summary: String,
)

@Composable
private fun SetsCard(
    sets: List<ExecutionSet>,
    checkedNumbers: Set<Int>,
    onToggleSet: (setNumber: Int, checked: Boolean) -> Unit,
) {
    val doneCount = sets.count { it.number in checkedNumbers }
    val firstUnchecked = sets.firstOrNull { it.number !in checkedNumbers }?.number

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = ExecutionSurface,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, ExecutionCardBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.workout_sets_label),
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
                        text = if (sets.isEmpty()) {
                            stringResource(R.string.workout_no_sets)
                        } else {
                            stringResource(R.string.workout_sets_done, doneCount, sets.size)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ExecutionInkSecondary,
                    )
                }
            }

            if (sets.isEmpty()) {
                Text(
                    text = stringResource(R.string.workout_no_sets_defined),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = ExecutionInkMuted,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sets.forEach { set ->
                        ExecutionSetRow(
                            set = set,
                            checked = set.number in checkedNumbers,
                            isCurrent = set.number == firstUnchecked,
                            onCheckedChange = { onToggleSet(set.number, it) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One row of the workout's set list. The checkbox is UI-only: the player shows
 * what the routine asks for and does not record results.
 */
@Composable
private fun ExecutionSetRow(
    set: ExecutionSet,
    checked: Boolean,
    isCurrent: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val background = when {
        checked -> ExecutionGreenLight.copy(alpha = 0.6f)
        isCurrent -> ExecutionSurface
        else -> ExecutionSurfaceMuted
    }
    val borderColor = when {
        checked -> ExecutionGreenBorder.copy(alpha = 0.8f)
        isCurrent -> ExecutionBrand
        else -> ExecutionFieldBorder
    }
    val numberBackground = when {
        checked -> ExecutionGreenDark
        isCurrent -> ExecutionBrand
        else -> ExecutionSurfaceStrong
    }

    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = shape,
        color = background,
        border = BorderStroke(if (isCurrent) 2.dp else 1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
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
                    text = set.number.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (checked || isCurrent) AppTheme.colors.onPrimary else ExecutionInkMuted,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.track_set_label),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (checked || isCurrent) ExecutionInk else ExecutionInkMuted,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = set.summary,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (checked || isCurrent) ExecutionInk else ExecutionInkMuted,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.size(32.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = ExecutionGreen,
                    uncheckedColor = ExecutionFieldBorder,
                    checkmarkColor = AppTheme.colors.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun ExerciseStopwatchCard(
    seconds: Int,
    started: Boolean,
    running: Boolean,
    stopped: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val status = when {
        !started -> stringResource(R.string.timer_ready)
        running -> stringResource(R.string.timer_running)
        stopped -> stringResource(R.string.timer_stopped)
        else -> stringResource(R.string.timer_paused)
    }

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
                        text = stringResource(R.string.workout_stopwatch),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = ExecutionInk,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (stopped) ExecutionGreenLight else ExecutionBrand50,
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (stopped) ExecutionGreenDark else ExecutionBrandDark,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = formatSeconds(seconds),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 44.sp,
                color = if (stopped) ExecutionGreen else ExecutionInk,
            )
            Text(
                text = stringResource(R.string.workout_elapsed),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = ExecutionInkMuted,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    running -> {
                        PrimaryTimerButton(
                            label = stringResource(R.string.workout_pause),
                            icon = Icons.Filled.Pause,
                            onClick = onPause,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryTimerButton(
                            label = stringResource(R.string.workout_stop),
                            icon = Icons.Filled.Stop,
                            onClick = onStop,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    started && !stopped -> {
                        PrimaryTimerButton(
                            label = stringResource(R.string.workout_resume),
                            icon = Icons.Filled.PlayArrow,
                            onClick = onResume,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryTimerButton(
                            label = stringResource(R.string.workout_stop),
                            icon = Icons.Filled.Stop,
                            onClick = onStop,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Ready, or stopped after a measurement: Start always
                    // begins a fresh stopwatch from zero.
                    else -> {
                        PrimaryTimerButton(
                            label = stringResource(R.string.workout_start),
                            icon = Icons.Filled.PlayArrow,
                            onClick = onStart,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryTimerButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ExecutionBrand,
            contentColor = AppTheme.colors.onPrimary,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SecondaryTimerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
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
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
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
                    listOf(ExecutionBrand50, themedColor(Color(0xFFEEF2FF), Color(0xFF232A3A)))
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
                text = stringResource(R.string.workout_up_next),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = ExecutionBrand,
            )
            Text(
                text = nextExercise?.name ?: stringResource(R.string.workout_complete),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ExecutionInk,
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
    doneEnabled: Boolean,
    onDone: () -> Unit,
    onSkip: () -> Unit,
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
            PrimaryActionButton(
                label = stringResource(R.string.workout_done),
                icon = Icons.Filled.Check,
                containerColor = ExecutionGreen,
                enabled = doneEnabled,
                onClick = onDone,
            )
            // Same shape, height and icon treatment as Done; only the colour
            // and the icon differ, so skipping reads as the alternate answer
            // instead of a lesser action.
            PrimaryActionButton(
                label = stringResource(R.string.workout_skip_exercise),
                icon = Icons.Filled.SkipNext,
                containerColor = ExecutionRed,
                enabled = true,
                onClick = onSkip,
            )
        }
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = AppTheme.colors.onPrimary,
            disabledContainerColor = containerColor.copy(alpha = 0.35f),
            disabledContentColor = AppTheme.colors.onPrimary.copy(alpha = 0.7f),
        ),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(AppTheme.colors.onPrimary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = AppTheme.colors.onPrimary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// Encouragement follows how much of the workout was actually performed, so a
// run with a few skipped exercises is not celebrated like a full one.
private val LowCompletionMessages = listOf(
    R.string.encourage_beginner_1,
    R.string.encourage_beginner_2,
    R.string.encourage_beginner_3,
    R.string.encourage_beginner_4,
)

private val MidCompletionMessages = listOf(
    R.string.encourage_mid_1,
    R.string.encourage_mid_2,
    R.string.encourage_mid_3,
    R.string.encourage_mid_4,
)

private val HighCompletionMessages = listOf(
    R.string.encourage_high_1,
    R.string.encourage_high_2,
    R.string.encourage_high_3,
    R.string.encourage_high_4,
)

/** Under 20%, 20–50% and over 50%. */
private fun completionMessages(percent: Int): List<Int> = when {
    percent < 20 -> LowCompletionMessages
    percent <= 50 -> MidCompletionMessages
    else -> HighCompletionMessages
}

/** Blocks the player until the pre-workout countdown reaches zero. */
@Composable
private fun StartCountdownOverlay(seconds: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ExecutionBrand900.copy(alpha = 0.94f))
            // Swallow taps: sets must not be ticked before the timer starts.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.workout_countdown_get_ready),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ExecutionBrand100,
            )
            Spacer(Modifier.height(20.dp))
            // `key` restarts the number on each second so the countdown reads
            // as three distinct steps rather than a slowly changing value.
            key(seconds) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(ExecutionBrand),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = seconds.toString(),
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Black,
                        color = AppTheme.colors.onPrimary,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.workout_countdown_message, seconds),
                fontSize = 14.sp,
                color = ExecutionBrand100,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CompletionOverlay(
    completed: Int,
    total: Int,
    onBackClick: () -> Unit,
) {
    val percent = completionPercent(completed, total)
    // Stable while the overlay is visible (and across rotation), so the
    // encouragement does not change on every recomposition.
    val encouragementRes = rememberSaveable(completed, total) {
        completionMessages(percent).random()
    }
    val encouragement = stringResource(encouragementRes)

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
                // Success rate at a glance: the ring fills to the share of
                // exercises actually completed, with the rounded value inside.
                Box(
                    modifier = Modifier.size(112.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = if (percent >= 50) ExecutionGreenDark else ExecutionBrand,
                        trackColor = ExecutionGreenLight,
                        strokeWidth = 9.dp,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$percent%",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = ExecutionInk,
                        )
                        Text(
                            text = stringResource(R.string.workout_success_label),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = ExecutionInkSecondary,
                            letterSpacing = 1.sp,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.workout_complete),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = ExecutionInk,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (completed >= total) {
                        "$encouragement ${stringResource(R.string.workout_finished_all, total)}"
                    } else {
                        "$encouragement ${stringResource(R.string.workout_finished_some, completed, total)}"
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
                        contentColor = AppTheme.colors.onPrimary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.workout_back_to_schedule),
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
            text = stringResource(R.string.workout_no_exercises),
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
            Text(stringResource(R.string.profile_back))
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
            text = stringResource(R.string.workout_unavailable),
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
            Text(stringResource(R.string.profile_back))
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
                text = stringResource(R.string.workout_exit_title),
                fontWeight = FontWeight.Bold,
                color = ExecutionInk,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.workout_exit_message),
                fontSize = 14.sp,
                color = ExecutionInkSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.workout_exit_button),
                    fontWeight = FontWeight.Bold,
                    color = ExecutionInkSecondary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.workout_keep_going),
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

private fun setSummary(context: Context, set: ExerciseProgressEntity): String = when (set.inferredExerciseType()) {
    DURATION_TYPE -> formatDuration(set.durationSeconds ?: 0)
    "REPS" -> context.getString(R.string.workout_set_summary_reps, set.reps ?: 0)
    else -> context.getString(
        R.string.workout_set_summary_weighted,
        formatWeight(context, set.weight, UnitSystem.entries.firstOrNull { it.name == set.unitSystem } ?: UnitSystem.METRIC),
        set.reps ?: 0,
    )
}

private fun formatElapsed(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val m = safe / 60
    val s = safe % 60
    return "%d:%02d".format(m, s)
}

private fun plannedSetSummary(context: Context, planned: PlannedSetEntity, unitSystem: UnitSystem = UnitSystem.METRIC): String = when {
    planned.durationSeconds != null -> formatDuration(planned.durationSeconds)
    planned.weight != null && planned.weight > 0.0 ->
        context.getString(R.string.workout_set_summary_weighted, formatWeight(context, planned.weight, unitSystem), planned.reps ?: 0)
    else -> context.getString(R.string.workout_set_summary_reps, planned.reps ?: 0)
}

private fun formatSeconds(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val m = safe / 60
    val s = safe % 60
    return "%02d:%02d".format(m, s)
}
