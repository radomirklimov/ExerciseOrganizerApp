package com.example.organizer.network.models

import kotlinx.serialization.Serializable

@Serializable
data class Category (
    var categoryId: Int,
    var name: String,
    var parentCategory: Category? = null,
)
