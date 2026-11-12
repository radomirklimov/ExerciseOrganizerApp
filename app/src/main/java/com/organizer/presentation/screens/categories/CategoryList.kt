package com.organizer.presentation.screens.categories

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.screens.exercises.ExerciseListItem


@Composable
fun CategoryList(
    categoryId: Long,
    categoryName: String?,
    categoryFullPath: String?,
    onCategoryClick: (CategoryEntity) -> Unit,
    onExerciseClick: (ExerciseEntity) -> Unit,
    onAddCustomClick: () -> Unit,
    viewModel: OrganizerViewModel
) {
    val categories by viewModel.observeSubcategories(categoryId).collectAsState(initial = null)
    val exercises by viewModel.observeExercisesByCategory(categoryId).collectAsState(initial = emptyList())

    val context = LocalContext.current
    var showSuggestDialog by remember { mutableStateOf(false) }
    var suggestionText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        items(
            items = categories ?: emptyList(),
            key = { it.categoryId }
        ) { category ->
            CategoryListItem(
                category = category,
                onClick = {
                    onCategoryClick(category)
                },
                viewModel = viewModel
            )
        }

        items(
            items = exercises,
            key = { it.exerciseId }
        ) { exercise ->
            ExerciseListItem(
                exercise = exercise,
                deletionInfo = null,
                expanded = false,
                onClick = { onExerciseClick(exercise) },
                onOpenClick = {},
                onDeleteClick = {},
                viewModel = viewModel
            )
        }

        if (categories?.isEmpty() == true) {
            item {
                MissingExerciseBlock(
                    categoryName = categoryName ?: "this category",
                    onAddCustomClick = onAddCustomClick,
                    onSuggestClick = { showSuggestDialog = true },
                )
            }
        }
    }

    if (showSuggestDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuggestDialog = false
                suggestionText = ""
            },
            title = { Text("Suggest an exercise") },
            text = {
                OutlinedTextField(
                    value = suggestionText,
                    onValueChange = { suggestionText = it },
                    placeholder = { Text("Describe the exercise you would like to see") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        sendSuggestionEmail(
                            context = context,
                            categoryName = categoryFullPath,
                            message = suggestionText,
                        )
                        showSuggestDialog = false
                        suggestionText = ""
                    },
                    enabled = suggestionText.isNotBlank(),
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSuggestDialog = false
                        suggestionText = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MissingExerciseBlock(
    categoryName: String,
    onAddCustomClick: () -> Unit,
    onSuggestClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(Color(0xFFF7F2F8), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFF6750A4).copy(alpha = 0.1f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = Color(0xFF6750A4),
                    modifier = Modifier.size(18.dp),
                )
            }
            Column {
                Text(
                    text = "Missing an exercise?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1C1B1F),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFFE9DDFF), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = null,
                        tint = Color(0xFF4B3087),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Column {
                    Text(
                        text = "Add custom movement",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1C1B1F),
                    )
                    Text(
                        text = "Create and track your own variation",
                        fontSize = 12.sp,
                        color = Color(0xFF494551),
                    )
                }
            }
            Button(
                onClick = onAddCustomClick,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("+ Create", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFFEBE7EC), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = Color(0xFF494551),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Column {
                    Text(
                        text = "Suggest an exercise",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1C1B1F),
                    )
                    Text(
                        text = "Tell us what's missing in this category",
                        fontSize = 12.sp,
                        color = Color(0xFF494551),
                    )
                }
            }
            OutlinedButton(
                onClick = onSuggestClick,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("Suggest", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun sendSuggestionEmail(
    context: Context,
    categoryName: String?,
    message: String,
) {
    val body = buildString {
        appendLine("Message:")
        appendLine(message.trim())
    }
    val subject = "Exercise suggestion: ${categoryName ?: "Unknown"}"
    val uri = Uri.parse(
        "mailto:radomirklimovv@gmail.com" +
            "?subject=" + Uri.encode(subject) +
            "&body=" + Uri.encode(body)
    )
    val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf("radomirklimovv@gmail.com"))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Send email"))
    } catch (e: Exception) {
        Toast.makeText(context, "No email app available", Toast.LENGTH_SHORT).show()
    }
}

