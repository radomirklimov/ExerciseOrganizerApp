package com.organizer.presentation.screens.schedule

import android.content.Context
import com.organizer.R
import com.organizer.data.preferences.AppLocale
import kotlin.random.Random

/**
 * Default note used for a workout reminder when the user does not write one.
 *
 * Only a stable token is persisted for automatic notes ([tokenFor]). The text
 * itself is resolved from resources at display time, so changing the app
 * language also changes notes that were created earlier. User-written notes
 * stay as plain text and are never translated.
 */
internal object MotivationalPhrases {

    private const val AUTO_TOKEN_PREFIX = "auto_motivational:"

    private val phraseResIds = intArrayOf(
        R.string.motivational_1,
        R.string.motivational_2,
        R.string.motivational_3,
        R.string.motivational_4,
        R.string.motivational_5,
        R.string.motivational_6,
        R.string.motivational_7,
        R.string.motivational_8,
        R.string.motivational_9,
        R.string.motivational_10,
        R.string.motivational_11,
        R.string.motivational_12,
        R.string.motivational_13,
        R.string.motivational_14,
        R.string.motivational_15,
        R.string.motivational_16,
        R.string.motivational_17,
        R.string.motivational_18,
        R.string.motivational_19,
        R.string.motivational_20,
        R.string.motivational_21,
        R.string.motivational_22,
        R.string.motivational_23,
        R.string.motivational_24,
        R.string.motivational_25,
        R.string.motivational_26,
        R.string.motivational_27,
        R.string.motivational_28,
        R.string.motivational_29,
        R.string.motivational_30,
    )

    private val emojis = listOf(
        "\uD83D\uDC4A", // 💪
        "\uD83D\uDD25", // 🔥
        "\uD83D\uDE80", // 🚀
        "\uD83C\uDFAF", // 🎯
        "\uD83D\uDCAF", // 💯
        "\u2728", // ✨
        "\uD83D\uDC4A", // 💪
        "\uD83D\uDE4C", // 🙌
        "\uD83C\uDFCB\uFE0F", // 🏋️
        "\uD83D\uDD25", // 🔥
        "\uD83D\uDCA5", // 💥
        "\uD83D\uDC4A", // 💪
        "\uD83C\uDF1F", // 🌟
        "\uD83D\uDE80", // 🚀
        "\u2764\uFE0F", // ❤️
        "\uD83D\uDC4A", // 👊
        "\uD83C\uDF31", // 🌱
        "\uD83E\uDDE0", // 🧠
        "\uD83C\uDFC9", // 🏆
        "\uD83C\uDFAF", // 🎯
        "\uD83D\uDE09", // 😉
        "\uD83D\uDD25", // 🔥
        "\uD83C\uDF1F", // 🌟
        "\uD83D\uDC4A", // 💪
        "\uD83D\uDD11", // 🔑
        "\u2600\uFE0F", // ☀️
        "\uD83D\uDE80", // 🚀
        "\u2764\uFE0F", // ❤️
        "\uD83D\uDC4A", // 👊
        "\uD83C\uDF31", // 🌱
    )

    /** Index of a newly chosen phrase. */
    fun randomIndex(random: Random = Random.Default): Int = random.nextInt(phraseResIds.size)

    /** Persisted representation of an automatically chosen phrase. */
    fun tokenFor(index: Int): String = AUTO_TOKEN_PREFIX + index

    /** Returns the phrase index encoded in [stored], or null for a custom note. */
    fun autoIndex(stored: String): Int? {
        if (!stored.startsWith(AUTO_TOKEN_PREFIX)) return null
        val index = stored.substring(AUTO_TOKEN_PREFIX.length).toIntOrNull() ?: return null
        return index.takeIf { it in phraseResIds.indices }
    }

    /**
     * Turns a stored note into the text shown to the user. Automatic notes are
     * looked up in the current app language; custom notes are returned as-is.
     */
    fun resolve(context: Context, stored: String): String {
        // Notifications are posted from application context, which is not
        // wrapped by MainActivity's locale handling.
        val localizedContext = AppLocale.wrap(context)
        return resolve(stored) { index -> localizedContext.getString(phraseResIds[index]) }
    }

    /** Pure overload used for tests and for callers that provide the strings. */
    fun resolve(stored: String, phrase: (Int) -> String): String {
        val index = autoIndex(stored) ?: return stored
        return phrase(index) + " " + emojis[index]
    }
}
