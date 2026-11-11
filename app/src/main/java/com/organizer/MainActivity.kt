package com.organizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.Routes
import com.organizer.presentation.screens.add_card.AddCardScreen
import com.organizer.presentation.screens.sports.SportsScreen
import com.organizer.presentation.screens.categories.CategoryContentScreen
import com.organizer.presentation.screens.exercises.ExerciseCard
import com.organizer.presentation.screens.workout.CreateWorkoutScreen
import com.organizer.presentation.screens.general.BottomNavigationBar
import com.organizer.presentation.screens.welcome.OnboardScreen
import com.organizer.presentation.screens.welcome.OnboardingManager
import com.organizer.presentation.screens.workout.WorkoutContentScreen
import com.organizer.presentation.screens.workouts_and_exercises.CustomWorkoutsAndExercisesScreen
import com.organizer.presentation.screens.workouts_and_exercises.WorkoutsScreen
import com.organizer.presentation.screens.workouts_and_exercises.ExercisesScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
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
    // create viewModel for instant data load
    val organizerViewModel: OrganizerViewModel = hiltViewModel()

    val navController = rememberNavController()
    val currentRoute = navController
        .currentBackStackEntryAsState()
        .value
        ?.destination
        ?.route

    fun popBackScreen() {
        val currentRout = navController.currentDestination?.route
        if (currentRout != Routes.Sports.route) {
            navController.popBackStack()
        }
    }

    Scaffold(
        containerColor = Color(0xFFF8F5F5),
        bottomBar = {
            if (currentRoute != Routes.Onboarding.route) {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onHomeClick = {
                        navController.navigate(Routes.Sports.route)
                    },
                    onWorkoutsClick = {
                        navController.navigate(Routes.Workouts.route)
                    },
                    onExercisesClick = {
                        navController.navigate(Routes.Exercises.route)
                    },
                    onAddCardClick = {
                        navController.navigate(Routes.AddCard.route)
                    }
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
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.Onboarding.route) {
                val context = LocalContext.current
                OnboardScreen(
                    onFinish = {
                        OnboardingManager.complete(
                            context = context
                        )
                        navController.navigate(Routes.Sports.route) {
                            popUpTo(Routes.Onboarding.route) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Routes.Sports.route) {
                SportsScreen(
                    onSportClick = { category ->
                        navController.navigate(
                            Routes.Subcategory.createRoute(category.categoryId)
                        )
                    },
                    onCategoryClick = { category ->
                        navController.navigate(
                            Routes.Subcategory.createRoute(category.categoryId)
                        )
                    },
                    onExerciseClick = { exercise ->
                        navController.navigate(
                            Routes.ExerciseCard.createRoute(exercise.exerciseId)
                        )
                    },
                    viewModel = organizerViewModel,
                )
            }

            composable(Routes.Subcategory.route) { backStackEntry ->
                val categoryId = backStackEntry.arguments
                    ?.getString("categoryId")
                    ?.toLongOrNull() ?: 0L

                CategoryContentScreen(
                    categoryId = categoryId,
                    onCategoryClick = { category ->
                        navController.navigate(
                            Routes.Subcategory.createRoute(category.categoryId)
                        )
                    },
                    onExerciseClick = { exercise ->
                        navController.navigate(
                            Routes.ExerciseCard.createRoute(exercise.exerciseId)
                        )
                    },
                    onBackClick = {
                        popBackScreen()
                    },
                    viewModel = organizerViewModel
                )
            }

            composable(Routes.ExerciseCard.route) { backStackEntry ->
                val exerciseId = backStackEntry.arguments
                    ?.getString("exerciseId")
                    ?.toLongOrNull() ?: 0L

                ExerciseCard(
                    exerciseId = exerciseId,
                    onCreateWorkoutClick = { navController.navigate(Routes.CreateWorkout.route) },
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
                    onCreateWorkoutClick = { navController.navigate(Routes.CreateWorkout.route) },
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
                )
            }

            composable(Routes.CreateWorkout.route) {
                CreateWorkoutScreen(
                    onBackClick = {
                        popBackScreen()
                    },
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
