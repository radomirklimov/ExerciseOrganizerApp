package com.organizer.constants

object AppConfig {
    const val DATABASE_NAME = "app_db"
    const val API_BASE_URL = "https://exerciseapi-production-50be.up.railway.app"
    // Public Supabase Storage bucket holding exercise images and category icons.
    // The API returns bare file names (e.g. "Flat Bench Press.webp"); the full
    // download URL is built as "$SUPABASE_STORAGE_BASE_URL/<images|icons>/<file>".
    const val SUPABASE_STORAGE_BASE_URL = "https://rrlbswzozpijrumisxin.supabase.co/storage/v1/object/public/exercise-media"
}
