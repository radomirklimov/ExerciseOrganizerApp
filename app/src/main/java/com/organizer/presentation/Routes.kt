package com.organizer.presentation

sealed class Routes(val route: String) {
    object Onboarding : Routes("onboarding")
    object Sports: Routes("sports")
    object Subcategory: Routes("subcategory/{categoryId}") {
        fun createRoute(categoryId: Long): String {
            return "subcategory/$categoryId"
        }
    }
    object ExerciseCard: Routes("exercise/{exerciseId}") {
        fun createRoute(exerciseId: Long): String {
            return "exercise/$exerciseId"
        }
    }
    object Workouts: Routes("workouts")
    object Exercises: Routes("exercises")
    object CreateWorkout: Routes("createWorkout")
    object AddCard: Routes("addCard")
    object Workout: Routes("workout/{workoutId}") {
        fun createRoute(workoutId: Long): String {
            return "workout/$workoutId"
        }
    }
}
