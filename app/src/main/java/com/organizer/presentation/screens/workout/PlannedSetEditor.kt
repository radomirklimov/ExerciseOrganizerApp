package com.organizer.presentation.screens.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.organizer.data.local.db.entities.PlannedSetEntity
import com.organizer.R

/**
 * Configuration editor for planned sets of one exercise in a saved workout.
 * Purely local UI state; every confirmed change goes through PlannedSetRepository.
 */
@Composable
fun PlannedSetEditor(
    sets: List<PlannedSetEntity>,
    onAdd: (reps: Int?, weight: Double?, duration: Int?) -> Unit,
    onUpdate: (setNumber: Int, reps: Int?, weight: Double?, duration: Int?) -> Unit,
    onDelete: (setNumber: Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var reps by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidLabel = stringResource(R.string.planned_sets_invalid)

    fun parsePositive(value: String): Int? = value.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()?.takeIf { it >= 0 }
    fun parseWeight(value: String): Double? = value.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it >= 0.0 }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.planned_sets_title), style = MaterialTheme.typography.titleSmall)
        if (sets.isEmpty()) {
            Text(stringResource(R.string.planned_sets_empty), style = MaterialTheme.typography.bodySmall)
        } else {
            sets.forEachIndexed { index, set ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${index + 1}.", modifier = Modifier.padding(end = 2.dp))
                    OutlinedTextField(
                        value = set.reps?.toString() ?: "",
                        onValueChange = { v -> onUpdate(set.setNumber, v.toIntOrNull()?.takeIf { it >= 0 }, set.weight, set.durationSeconds) },
                        label = { Text(stringResource(R.string.planned_sets_reps)) },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = set.weight?.toString() ?: "",
                        onValueChange = { v -> onUpdate(set.setNumber, set.reps, v.toDoubleOrNull()?.takeIf { it >= 0.0 }, set.durationSeconds) },
                        label = { Text(stringResource(R.string.planned_sets_weight)) },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = set.durationSeconds?.toString() ?: "",
                        onValueChange = { v -> onUpdate(set.setNumber, set.reps, set.weight, v.toIntOrNull()?.takeIf { it >= 0 }) },
                        label = { Text(stringResource(R.string.planned_sets_duration)) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { if (index > 0) onMove(index, index - 1) }) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.planned_sets_move_up))
                    }
                    IconButton(onClick = { if (index < sets.lastIndex) onMove(index, index + 1) }) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.planned_sets_move_down))
                    }
                    IconButton(onClick = { onDelete(set.setNumber) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.planned_sets_remove))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(value = reps, onValueChange = { reps = it }, label = { Text(stringResource(R.string.planned_sets_reps)) }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text(stringResource(R.string.planned_sets_weight)) }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text(stringResource(R.string.planned_sets_duration)) }, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val r = parsePositive(reps)
                val w = parseWeight(weight)
                val d = parsePositive(duration)
                if ((reps.isBlank() || r != null) && (weight.isBlank() || w != null) && (duration.isBlank() || d != null)) {
                    error = null
                    onAdd(r, w, d)
                    reps = ""; weight = ""; duration = ""
                } else {
                    error = invalidLabel
                }
            }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.planned_sets_add)) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = { error = null }) { Text(stringResource(R.string.planned_sets_done)) }
    }
}
