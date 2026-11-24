package com.organizer.data.repo

import java.util.Locale

/** Mirrors the UI rule: trim whitespace and compare names case-insensitively. */
object WorkoutNameNormalizer {
    fun normalize(name: String): String = name.trim().lowercase(Locale.ROOT)
}