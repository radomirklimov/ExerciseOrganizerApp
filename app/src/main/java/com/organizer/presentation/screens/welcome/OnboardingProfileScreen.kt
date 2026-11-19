package com.organizer.presentation.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.data.preferences.LanguageOption
import com.organizer.ui.theme.AppTheme

/**
 * Single onboarding step that collects the user's display name and preferred
 * language before entering the app.
 */
@Composable
fun OnboardingProfileScreen(
    onComplete: (String, LanguageOption) -> Unit,
) {
    val colors = AppTheme.colors

    var name by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf(LanguageOption.ENGLISH) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "What should we call you?",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("Your name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Which language would you like to use?",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LanguageOption.entries.forEach { language ->
                val selected = selectedLanguage == language
                Surface(
                    onClick = { selectedLanguage = language },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) colors.primary else colors.surfaceMuted,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = language.nativeName,
                            color = if (selected) colors.onPrimary else colors.textPrimary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Text(
                                text = "✓",
                                color = colors.onPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { onComplete(name, selectedLanguage) },
            enabled = name.trim().isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) {
            Text(
                text = "Continue",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
