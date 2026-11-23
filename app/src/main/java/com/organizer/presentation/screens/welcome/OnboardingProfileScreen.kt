package com.organizer.presentation.screens.welcome

import android.app.Activity
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.organizer.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.organizer.data.preferences.LanguageOption
import com.organizer.presentation.account.AccountViewModel
import com.organizer.auth.UserState
import com.organizer.ui.theme.AppTheme

/**
 * Onboarding step that picks the app language (only when it cannot be
 * detected from the device) and offers an optional Google sign-in, with a
 * clear guest-mode alternative.
 */
@Composable
fun OnboardingProfileScreen(
    onComplete: (LanguageOption, Boolean) -> Unit,
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val activity = context as? Activity
    val accountViewModel: AccountViewModel = hiltViewModel()
    val userState by accountViewModel.userState.collectAsState()

    // Detect the device language once. If it is supported, use it silently;
    // otherwise let the user pick.
    val detectedLanguage = remember { LanguageOption.fromDeviceLocale() }
    var selectedLanguage by remember {
        mutableStateOf(detectedLanguage ?: LanguageOption.ENGLISH)
    }
    val askLanguage = detectedLanguage == null

    var attemptedSignIn by remember { mutableStateOf(false) }

    // Advance as soon as Google sign-in succeeds.
    LaunchedEffect(userState) {
        if (attemptedSignIn && userState is UserState.Authenticated) {
            onComplete(selectedLanguage, true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onboarding_welcome),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_setup_subtitle),
            fontSize = 14.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        if (askLanguage) {
            Text(
                text = stringResource(R.string.onboarding_language_question),
                fontSize = 18.sp,
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

            Spacer(Modifier.height(24.dp))
        }

        // Google sign-in (optional).
        if (userState == UserState.Authenticating) {
            CircularProgressIndicator(color = colors.primary)
            Spacer(Modifier.height(12.dp))
        } else {
            Button(
                onClick = {
                    attemptedSignIn = true
                    activity?.let { accountViewModel.signInWithGoogle(it) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                Text(
                    text = stringResource(R.string.profile_continue_google),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (attemptedSignIn && userState == UserState.Guest) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_signin_failed),
                fontSize = 12.sp,
                color = colors.danger,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = { onComplete(selectedLanguage, false) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_continue_without_account),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Guest mode explanation.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_guest_note_lead),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary,
            )
            Text("• " + stringResource(R.string.onboarding_guest_note_storage), fontSize = 12.sp, color = colors.textSecondary)
            Text("• " + stringResource(R.string.onboarding_guest_note_sync), fontSize = 12.sp, color = colors.textSecondary)
            Text("• " + stringResource(R.string.onboarding_guest_note_uninstall), fontSize = 12.sp, color = colors.textSecondary)
            Text("• " + stringResource(R.string.onboarding_guest_note_transfer), fontSize = 12.sp, color = colors.textSecondary)
        }
    }
}
