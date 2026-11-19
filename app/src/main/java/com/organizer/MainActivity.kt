package com.organizer

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.organizer.data.preferences.AppLocale
import com.organizer.data.preferences.SettingsRepository
import com.organizer.data.preferences.ThemeMode
import com.organizer.data.preferences.UserSettings
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.Routes
import com.organizer.presentation.settings.SettingsViewModel
import com.organizer.presentation.screens.add_card.AddCardScreen
import com.organizer.presentation.screens.profile.ProfileScreen
import com.organizer.presentation.screens.sports.SportsScreen
import com.organizer.presentation.screens.schedule.ScheduleScreen
import com.organizer.presentation.screens.categories.CategoryContentScreen
import com.organizer.presentation.screens.exercises.ExerciseCard
import com.organizer.presentation.screens.workout.CreateWorkoutScreen
import com.organizer.presentation.screens.general.BottomNavigationBar
import com.organizer.presentation.screens.welcome.OnboardScreen
import com.organizer.presentation.screens.welcome.OnboardingManager
import com.organizer.presentation.screens.welcome.OnboardingProfileScreen
import com.organizer.presentation.screens.workout.WorkoutContentScreen
import com.organizer.presentation.screens.workout.WorkoutExecutionScreen
import com.organizer.presentation.screens.workout.StarterTemplatePreviewScreen
import com.organizer.presentation.screens.workouts_and_exercises.CustomWorkoutsAndExercisesScreen
import com.organizer.presentation.screens.workouts_and_exercises.WorkoutsScreen
import com.organizer.presentation.screens.workouts_and_exercises.ExercisesScreen
import com.organizer.presentation.screens.workouts_and_exercises.StarterTemplatesScreen
import com.organizer.ui.theme.AppTheme
import com.organizer.ui.theme.AppThemeProvider
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Apply the language chosen on the profile screen before any resource
        // is resolved, so the choice survives restarts.
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Pick the window/splash theme before the window is created, so dark
        // mode does not flash a light background on launch.
        val splashTheme = if (SettingsRepository.readThemeMode(this) == ThemeMode.DARK) {
            R.style.Theme_ExerciseOrganizerApp_Splash_Dark
        } else {
            R.style.Theme_ExerciseOrganizerApp_Splash
        }
        setTheme(splashTheme)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val showOnboarding = !OnboardingManager.isCompleted(this)
            AppNavigation(showOnboarding)
        }
    }
}

@Composable
fun AppNavigation(showOnboarding: Boolean) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.settings.collectAsState()

    AppThemeProvider(
        darkTheme = settings.themeMode == ThemeMode.DARK,
        fontScale = settings.fontSize.scale,
    ) {
        AppNavigationContent(
            showOnboarding = showOnboarding,
            settings = settings,
            settingsViewModel = settingsViewModel,
        )
    }
}

@Composable
private fun AppNavigationContent(
    showOnboarding: Boolean,
    settings: UserSettings,
    settingsViewModel: SettingsViewModel,
) {
    // create viewModel for instant data load
    val organizerViewModel: OrganizerViewModel = hiltViewModel()

    val navController = rememberNavController()
    val currentRoute = navController
        .currentBackStackEntryAsState()
        .value
        ?.destination
        ?.route

    val context = LocalContext.current
    val palette = AppTheme.colors
    LaunchedEffect(currentRoute, palette.isDark) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val statusBarColor = when (currentRoute) {
            Routes.Sports.route,
            Routes.Schedule.route,
            -> palette.background.toArgb()

            // The execution screen has its own header pinned under the status bar.
            Routes.WorkoutExecution.route -> palette.surface.toArgb()

            else -> if (palette.isDark) {
                palette.background.toArgb()
            } else {
                palette.backgroundSubtle.toArgb()
            }
        }
        activity.window.statusBarColor = statusBarColor
        activity.window.navigationBarColor = palette.background.toArgb()
        activity.window.setBackgroundDrawable(ColorDrawable(statusBarColor))
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !palette.isDark
            isAppearanceLightNavigationBars = !palette.isDark
        }
    }

    fun popBackScreen() {
        val currentRout = navController.currentDestination?.route
        if (currentRout != Routes.Sports.route) {
            navController.popBackStack()
        }
    }

    fun isCategoryEntry(entry: NavBackStackEntry?, categoryId: Long): Boolean {
        if (entry?.destination?.route != Routes.Subcategory.route) return false
        return entry.arguments?.getLong("categoryId") == categoryId
    }

    // Shows the requested category screen.
    //
    // If that category is already on the back stack (the user just went back
    // from it, or jumped to an ancestor via the breadcrumb), the screens above
    // it are popped and it becomes the current screen again. That keeps the
    // back stack in sync with the category hierarchy instead of piling up
    // duplicate "subcategory/{categoryId}" entries.
    //
    // When the category is not on the back stack at all, it is pushed forward.
    fun openCategory(categoryId: Long) {
        val isOnBackStack = navController.currentBackStack.value.any { entry ->
            isCategoryEntry(entry, categoryId)
        }

        if (isOnBackStack) {
            while (!isCategoryEntry(navController.currentBackStackEntry, categoryId)) {
                if (!navController.popBackStack()) break
            }
            if (isCategoryEntry(navController.currentBackStackEntry, categoryId)) return
        }

        navController.navigate(Routes.Subcategory.createRoute(categoryId))
    }

    // A bottom-bar tap leaves the home search behind: coming back to Home
    // must show the normal sports grid instead of stale search results.
    val focusManager = LocalFocusManager.current
    fun openTab(route: String) {
        // Drop the search field's focus first, otherwise the keyboard would
        // stay open over the screen the user is navigating to.
        focusManager.clearFocus()
        organizerViewModel.searchQuery.value = ""
        // Tapping the tab that is already open must not stack a duplicate
        // screen: the visible one just recomposes with the cleared search.
        navController.navigate(route) { launchSingleTop = true }
    }

    Scaffold(
        containerColor = palette.background,
        bottomBar = {
            // The workout player is an immersive flow: it provides its own
            // fixed action bar and must not show the app navigation.
            if (currentRoute != Routes.Onboarding.route &&
                currentRoute != Routes.OnboardingProfile.route &&
                currentRoute != Routes.WorkoutExecution.route
            ) {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onHomeClick = { openTab(Routes.Sports.route) },
                    onWorkoutsClick = { openTab(Routes.Workouts.route) },
                    onExercisesClick = { openTab(Routes.Exercises.route) },
                    onScheduleClick = { openTab(Routes.Schedule.route) },
                    onAddCardClick = { openTab(Routes.AddCard.route) },
                )
            }
        }
    ) { padding ->

        NavHost(
            navController = navController,
            startDestination = if (showOnboarding)
                Routes.Onboarding.route
            else
                Routes.Sports.route,
            modifier = Modifier.padding(padding),
            // Navigation's default cross-fade lasts 700 ms and fades through
            // the empty screen background, which is what reads as a white
            // flash before the destination has loaded its data. A short
            // cross-fade keeps both screens visible while it runs, so the
            // change feels immediate but still smooth.
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition = { fadeOut(animationSpec = tween(200)) },
        ) {
            if (showOnboarding) {
                composable(Routes.Onboarding.route) {
                    OnboardScreen(
                        onFinish = {
                            navController.navigate(Routes.OnboardingProfile.route) {
                                popUpTo(Routes.Onboarding.route) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(Routes.OnboardingProfile.route) {
                    val context = LocalContext.current
                    OnboardingProfileScreen(
                        onComplete = { name, language ->
                            val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
                            prefs.edit().apply {
                                putString("profile_name", name.trim().ifBlank { "Alex Morgan" })
                                putString("profile_email", "alex.morgan@fitflow.app")
                                putString("profile_language", language.tag)
                                putBoolean("onboarding_done", true)
                            }.commit()
                            settingsViewModel.reload()
                            navController.navigate(Routes.Sports.route) {
                                popUpTo(Routes.OnboardingProfile.route) { inclusive = true }
                                launchSingleTop = true
                            }
                            (context as? Activity)?.recreate()
                        }
                    )
                }
            }

            composable(Routes.Sports.route) {
                SportsScreen(
                    onSportClick = { category ->
                        navController.navigate(
                            Routes.Subcategory.createRoute(category.categoryId)
                        )
                    },
                    onExerciseClick = { exercise ->
                        navController.navigate(
                            Routes.ExerciseCard.createRoute(exercise.exerciseId)
                        )
                    },
                    onProfileClick = {
                        navController.navigate(Routes.Profile.route) {
                            launchSingleTop = true
                        }
                    },
                    avatarPath = settings.avatarPath,
                    initials = settings.initials,
                    viewModel = organizerViewModel,
                )
            }

            composable(Routes.Profile.route) {
                ProfileScreen(
                    settings = settings,
                    onBackClick = { popBackScreen() },
                    onAccountClosed = {
                        val popped = navController.popBackStack(
                            route = Routes.Sports.route,
                            inclusive = false,
                        )
                        if (!popped) {
                            navController.navigate(Routes.Sports.route) {
                                launchSingleTop = true
                            }
                        }
                    },
                    viewModel = settingsViewModel,
                )
            }

            composable(
                route = Routes.Subcategory.route,
                // Declaring the argument type makes back stack matching aware
                // of the actual category id. Without it every
                // "subcategory/{categoryId}" entry looks identical to
                // Navigation, so re-opening a category could resolve to a
                // different category screen.
                arguments = listOf(navArgument("categoryId") {
                    type = NavType.LongType
                }),
            ) { backStackEntry ->
                val categoryId = backStackEntry.arguments
                    ?.getLong("categoryId") ?: 0L

                // Give every category its own composition state. Otherwise the
                // previously opened category's rows stay on screen for a
                // moment while the new category's data loads, and tapping them
                // opens the wrong category.
                key(categoryId) {
                    CategoryContentScreen(
                        categoryId = categoryId,
                        onCategoryClick = { category ->
                            // Only follow rows of the category that is
                            // actually on screen right now. A tap can be
                            // delivered by a list from the category the user
                            // already went back from; following that row would
                            // open one of the previous category's children
                            // instead.
                            val activeCategoryId = navController
                                .currentBackStackEntry
                                ?.takeIf { it.destination.route == Routes.Subcategory.route }
                                ?.arguments
                                ?.getLong("categoryId")
                            if (activeCategoryId != null &&
                                category.parentCategoryId == activeCategoryId
                            ) {
                                openCategory(category.categoryId)
                            }
                        },
                        onCategoryAncestorClick = { category ->
                            openCategory(category.categoryId)
                        },
                        onExerciseClick = { exercise ->
                            navController.navigate(
                                Routes.ExerciseCard.createRoute(exercise.exerciseId)
                            )
                        },
                        onBackClick = {
                            popBackScreen()
                        },
                        onAddCustomExerciseClick = {
                            navController.navigate(Routes.AddCard.route)
                        },
                        viewModel = organizerViewModel
                    )
                }
            }

            composable(Routes.ExerciseCard.route) { backStackEntry ->
                val exerciseId = backStackEntry.arguments
                    ?.getString("exerciseId")
                    ?.toLongOrNull() ?: 0L

                ExerciseCard(
                    exerciseId = exerciseId,
                    onCreateWorkoutClick = { id ->
                        navController.navigate(Routes.CreateWorkout.createRoute(id))
                    },
                    onOpenWorkoutsClick = {
                        navController.navigate(Routes.Workouts.route)
                    },
                    onBackClick = {
                        popBackScreen()
                    },
                    viewModel = organizerViewModel,
                )
            }

            composable(Routes.Workouts.route) {
                WorkoutsScreen(
                    onWorkoutClick = { workout ->
                        navController.navigate(
                            Routes.Workout.createRoute(workout.workoutId)
                        )
                    },
                    onCreateWorkoutClick = {
                        navController.navigate(Routes.CreateWorkout.createRoute())
                    },
                    onTemplateClick = { templateId ->
                        navController.navigate(
                            Routes.StarterTemplate.createRoute(templateId)
                        )
                    },
                    onViewAllTemplatesClick = {
                        navController.navigate(Routes.StarterTemplates.route)
                    },
                )
            }

            composable(Routes.StarterTemplates.route) {
                StarterTemplatesScreen(
                    onTemplateClick = { templateId ->
                        navController.navigate(
                            Routes.StarterTemplate.createRoute(templateId)
                        )
                    },
                    onBackClick = {
                        popBackScreen()
                    },
                    viewModel = organizerViewModel,
                )
            }

            composable(Routes.StarterTemplate.route) { backStackEntry ->
                val templateId = backStackEntry.arguments
                    ?.getString("templateId")
                    ?.toLongOrNull() ?: 0L

                StarterTemplatePreviewScreen(
                    templateId = templateId,
                    onOpenExerciseClick = { exercise ->
                        navController.navigate(
                            Routes.ExerciseCard.createRoute(exercise.exerciseId)
                        )
                    },
                    onBackClick = {
                        popBackScreen()
                    },
                    onWorkoutCreated = { newWorkoutId ->
                        navController.navigate(
                            Routes.Workout.createRoute(newWorkoutId)
                        ) {
                            popUpTo(Routes.StarterTemplate.route) { inclusive = true }
                        }
                    },
                    viewModel = organizerViewModel,
                )
            }

            composable(Routes.Exercises.route) {
                ExercisesScreen(
                    onExerciseClick = { exercise ->
                        navController.navigate(
                            Routes.ExerciseCard.createRoute(exercise.exerciseId)
                        )
                    },
                    onCreateExerciseClick = { navController.navigate(Routes.AddCard.route) },
                    onBrowseStandardExercisesClick = { navController.navigate(Routes.Sports.route) },
                )
            }

            composable(Routes.Schedule.route) {
                ScheduleScreen(
                    onOpenWorkout = { workoutId ->
                        navController.navigate(Routes.WorkoutExecution.createRoute(workoutId))
                    },
                    // No workouts exist yet, so sending the user to the
                    // creation form would skip the list they need to browse.
                    onOpenWorkouts = {
                        navController.navigate(Routes.Workouts.route)
                    },
                )
            }

            composable(
                route = Routes.CreateWorkout.route,
                arguments = listOf(navArgument("exerciseId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }),
            ) { backStackEntry ->
                val pendingExerciseId = backStackEntry.arguments
                    ?.getLong("exerciseId")
                    ?.takeIf { it > 0L }

                CreateWorkoutScreen(
                    onBackClick = {
                        popBackScreen()
                    },
                    pendingExerciseId = pendingExerciseId,
                    viewModel = organizerViewModel,
                )
            }

            composable(Routes.Workout.route) { backStackEntry ->
                val workoutId = backStackEntry.arguments
                    ?.getString("workoutId")
                    ?.toLongOrNull() ?: 0L

                WorkoutContentScreen(
                    workoutId = workoutId,
                    onOpenExerciseClick = { exercise ->
                        navController.navigate(
                            Routes.ExerciseCard.createRoute(exercise.exerciseId)
                        )
                    },
                    onBackClick = {
                        popBackScreen()
                    },
                    onAddStandardExercisesClick = {
                        navController.navigate(Routes.Sports.route)
                    },
                    onAddCustomExercisesClick = {
                        navController.navigate(Routes.Exercises.route)
                    },
                    onCreateCustomExerciseClick = {
                        navController.navigate(Routes.AddCard.route)
                    },
                    viewModel = organizerViewModel,
                )
            }

            composable(
                route = Routes.WorkoutExecution.route,
                arguments = listOf(navArgument("workoutId") {
                    type = NavType.LongType
                }),
            ) { backStackEntry ->
                val workoutId = backStackEntry.arguments
                    ?.getLong("workoutId") ?: 0L

                WorkoutExecutionScreen(
                    workoutId = workoutId,
                    onBackClick = {
                        popBackScreen()
                    },
                    viewModel = organizerViewModel,
                )
            }

            composable(Routes.AddCard.route) {
                AddCardScreen(
                    onSaveClick = {
                        navController.navigate(Routes.AddCard.route)
                    },
                    onBackClick = {
                        popBackScreen()
                    },
                    viewModel = organizerViewModel,
                )
            }
        }
    }
}
