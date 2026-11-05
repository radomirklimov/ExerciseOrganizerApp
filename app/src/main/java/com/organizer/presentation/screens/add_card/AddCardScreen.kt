package com.organizer.presentation.screens.add_card

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.screens.general.SaveButton

@Composable
fun AddCardScreen(
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    viewModel: OrganizerViewModel,
) {
    var exerciseName by remember { mutableStateOf("") }
    val instructions = remember { mutableStateListOf<String>() }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showImageError by remember { mutableStateOf(false) }
    var showDuplicateNameError by remember { mutableStateOf(false) }
    val customExercises by viewModel.customExercisesUiState.collectAsState()
    val trimmedName = exerciseName.trim()
    val hasBlankInstruction = instructions.any { it.trim().isBlank() }
    val canSave = trimmedName.isNotBlank() &&
            imageUri != null &&
            !showDuplicateNameError &&
            !hasBlankInstruction
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Create Your Exercise",
                fontSize = 26.sp
            )

            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Exercise Name
        OutlinedTextField(
            value = exerciseName,
            onValueChange = {
                exerciseName = it
                showDuplicateNameError = false
            },
            isError = showDuplicateNameError,
            placeholder = {
                Text("Name Of Exercise")
            },
            supportingText = {
                if (showDuplicateNameError) {
                    Text("An exercise with this name already exists. Please choose a different name.")
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Exercise instructions
        InstructionEditor(
            instructions = instructions
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Exercise Image
        AddImageField(
            imageUri = imageUri,
            onImageSelected = { uri ->
                imageUri = uri
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Save Button
        SaveButton(
            enabled = canSave && !isSaving,
            onClick = {
                isSaving = true

                if (customExercises.any {
                        it.name.equals(trimmedName, ignoreCase = true)
                    }
                ) {
                    showDuplicateNameError = true
                    isSaving = false
                    return@SaveButton
                }

                val imageName = viewModel.saveCustomImage(imageUri!!)
                if (imageName == null) {
                    isSaving = false
                    showImageError = true
                    return@SaveButton
                }
                viewModel.createCustomExercise(
                    name = trimmedName,
                    instructions = instructions.map { it.trim() }.filter { it.isNotBlank() },
                    imageName = imageName
                )
                onSaveClick()
            }
        )

        if (showImageError) {
            AlertDialog(
                onDismissRequest = { showImageError = false },
                confirmButton = {
                    TextButton(
                        onClick = { showImageError = false }
                    ) {
                        Text("OK")
                    }
                },
                title = { Text("Error") },
                text = { Text("Image could not be saved") }
            )
        }
    }
}
