package com.organizer.presentation.screens.general

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.ui.theme.AppTheme
import com.organizer.R
import androidx.compose.ui.res.stringResource

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    scopeLabel: String? = null,
    scopeColor: Color = MaterialTheme.colorScheme.primary,
) {

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(stringResource(R.string.search_exercises_placeholder))
        },
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = AppTheme.colors.surface,
            unfocusedContainerColor = AppTheme.colors.surface,
        ),
        leadingIcon = {
            // The scope pill makes this row wider than the slot's 48dp minimum,
            // so the icon needs its own inset to keep the field's left padding.
            Row(
                modifier = Modifier.padding(
                    start = if (scopeLabel != null) 16.dp else 0.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null
                )

                if (scopeLabel != null) {
                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(scopeColor.copy(alpha = 0.10f))
                            .border(
                                width = 1.dp,
                                color = scopeColor.copy(alpha = 0.30f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.search_in_scope, scopeLabel),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = scopeColor,
                        )
                    }
                }
            }
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.search_clear)
                    )
                }
            }
        },
        singleLine = true
    )
}
