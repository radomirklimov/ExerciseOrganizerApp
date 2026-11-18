package com.organizer.presentation.screens.schedule

import kotlin.random.Random

/**
 * Default note used for a workout reminder when the user does not write one.
 *
 * A phrase is picked once when the schedule sheet opens for a new entry, shown
 * as the note placeholder and stored as the entry's note, so the reminder
 * notification carries an encouraging line the user can replace at any time.
 */
internal object MotivationalPhrases {

    val all: List<String> = listOf(
        "You got this! 💪",
        "Do your best today! 🔥",
        "Keep it up! 🚀",
        "One step closer to your goal! 🎯",
        "You've got this in you! 💯",
        "Make today count! ✨",
        "Stay strong and keep going! 💪",
        "Your future self will thank you! 🙌",
        "Every rep makes a difference! 🏋️",
        "Let's make it happen! 🔥",
        "No excuses, just progress! 💥",
        "You're stronger than you think! 💪",
        "Small steps, big results! 🌟",
        "Time to crush your goals! 🚀",
        "Believe in yourself! ❤️",
        "Show up and give it your best! 👊",
        "Progress, not perfection! 🌱",
        "Stay focused! 🧠",
        "Make yourself proud today! 🏆",
        "Stay focused and stay strong! 🎯",
        "The only bad workout is the one you skip! 😉",
        "Push your limits! 🔥",
        "You're doing amazing! 🌟",
        "Let's get stronger together! 💪",
        "Consistency is the key to success! 🔑",
        "Today is another chance to improve! ☀️",
        "Keep moving forward! 🚀",
        "Challenge yourself and enjoy the journey! ❤️",
        "Give it your all! 👊",
        "Another day, another opportunity to grow! 🌱",
    )

    /** Returns a phrase for a new reminder note. */
    fun random(random: Random = Random.Default): String = all[random.nextInt(all.size)]
}
