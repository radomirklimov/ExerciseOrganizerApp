package com.organizer.presentation.screens.profile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.R
import com.organizer.data.preferences.FontSizeOption
import com.organizer.data.preferences.LanguageOption
import com.organizer.data.preferences.ThemeMode
import com.organizer.data.preferences.UnitSystem
import com.organizer.data.preferences.UserSettings
import com.organizer.presentation.screens.general.ProfileAvatar
import com.organizer.presentation.settings.SettingsViewModel
import com.organizer.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Profile and app-settings screen, opened from the avatar on the home page. */
@Composable
fun ProfileScreen(
    settings: UserSettings,
    onBackClick: () -> Unit,
    onAccountClosed: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showEditProfile by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showLogOutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf<InfoDialogContent?>(null) }

    val version = remember(context) { appVersion(context) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) { copyAvatar(context, uri) }
            if (path != null) viewModel.updateAvatar(path)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        ProfileHeader(onBackClick = onBackClick)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HeaderCard(
                    settings = settings,
                    onEditProfile = { showEditProfile = true },
                    onAvatarClick = { avatarPicker.launch("image/*") },
                    onChangePassword = {
                        infoDialog = InfoDialogContent(
                            title = context.getString(R.string.profile_password_title),
                            message = context.getString(R.string.profile_password_message),
                        )
                    },
                    onViewAccount = {
                        infoDialog = InfoDialogContent(
                            title = context.getString(R.string.profile_account_title),
                            message = "${settings.displayName}\n${settings.email}",
                        )
                    },
                )
            }

            item {
                AppearanceCard(
                    settings = settings,
                    onThemeSelected = viewModel::setThemeMode,
                    onFontSizeSelected = viewModel::setFontSize,
                )
            }

            item {
                LanguageAndUnitsCard(
                    settings = settings,
                    languageMenuExpanded = showLanguageMenu,
                    onLanguageMenuChange = { showLanguageMenu = it },
                    onLanguageSelected = { language ->
                        viewModel.setLanguage(language)
                        showLanguageMenu = false
                    },
                    onUnitSelected = viewModel::setUnitSystem,
                )
            }

            item {
                PreferencesCard(
                    settings = settings,
                    onReminderChange = viewModel::setWorkoutReminderEnabled,
                    onNotificationsChange = viewModel::setNotificationsEnabled,
                )
            }

            item {
                AboutCard(
                    version = version,
                    onPrivacyClick = {
                        infoDialog = InfoDialogContent(
                            title = context.getString(R.string.profile_privacy_title),
                            message = context.getString(R.string.profile_privacy_message),
                        )
                    },
                    onTermsClick = {
                        infoDialog = InfoDialogContent(
                            title = context.getString(R.string.profile_terms_title),
                            message = context.getString(R.string.profile_terms_message),
                        )
                    },
                    onVersionClick = {
                        infoDialog = InfoDialogContent(
                            title = context.getString(R.string.profile_about_title),
                            message = context.getString(
                                R.string.profile_version,
                                version,
                            ),
                        )
                    },
                )
            }

            item {
                AccountActions(
                    onLogOutClick = { showLogOutDialog = true },
                    onDeleteClick = { showDeleteDialog = true },
                )
            }
        }
    }

    if (showEditProfile) {
        EditProfileDialog(
            settings = settings,
            onDismiss = { showEditProfile = false },
            onSave = { name, email ->
                viewModel.updateProfile(name, email)
                showEditProfile = false
            },
        )
    }

    if (showLogOutDialog) {
        ConfirmDialog(
            title = stringResource(R.string.profile_log_out_title),
            message = stringResource(R.string.profile_log_out_message),
            confirmLabel = stringResource(R.string.profile_log_out),
            destructive = false,
            onDismiss = { showLogOutDialog = false },
            onConfirm = {
                showLogOutDialog = false
                onAccountClosed()
            },
        )
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.profile_delete_title),
            message = stringResource(R.string.profile_delete_message),
            confirmLabel = stringResource(R.string.profile_delete_confirm),
            destructive = true,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                viewModel.resetProfile()
                onAccountClosed()
            },
        )
    }

    infoDialog?.let { dialog ->
        InfoDialog(content = dialog, onDismiss = { infoDialog = null })
    }
}

@Composable
private fun ProfileHeader(onBackClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.profile_back),
                tint = colors.textPrimary,
            )
        }
        Text(
            text = stringResource(R.string.profile_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun HeaderCard(
    settings: UserSettings,
    onEditProfile: () -> Unit,
    onAvatarClick: () -> Unit,
    onChangePassword: () -> Unit,
    onViewAccount: () -> Unit,
) {
    val colors = AppTheme.colors
    ProfileCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                ProfileAvatar(
                    avatarPath = settings.avatarPath,
                    initials = settings.initials,
                    size = 96.dp,
                )
                Surface(
                    onClick = onAvatarClick,
                    shape = CircleShape,
                    color = colors.primary,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = stringResource(R.string.profile_change_photo),
                            tint = Color.White,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = settings.displayName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = stringResource(R.string.profile_verified),
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = settings.email,
                fontSize = 13.sp,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                onClick = onEditProfile,
                shape = CircleShape,
                color = colors.primarySoft,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.profile_edit),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceMuted)
                    .padding(8.dp),
            ) {
                QuickLinkRow(
                    icon = Icons.Filled.LockReset,
                    label = stringResource(R.string.profile_change_password),
                    onClick = onChangePassword,
                )
                QuickLinkRow(
                    icon = Icons.Filled.Badge,
                    label = stringResource(R.string.profile_view_account_details),
                    onClick = onViewAccount,
                )
            }
        }
    }
}

@Composable
private fun QuickLinkRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun AppearanceCard(
    settings: UserSettings,
    onThemeSelected: (ThemeMode) -> Unit,
    onFontSizeSelected: (FontSizeOption) -> Unit,
) {
    val colors = AppTheme.colors
    ProfileCard {
        SectionTitle(icon = Icons.Outlined.Palette, title = stringResource(R.string.profile_appearance))

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.profile_theme),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                ThemeOption(
                    mode = mode,
                    selected = settings.themeMode == mode,
                    modifier = Modifier.weight(1f),
                    onClick = { onThemeSelected(mode) },
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.profile_font_size),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceMuted)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FontSizeOption.entries.forEach { option ->
                FontOption(
                    option = option,
                    selected = settings.fontSize == option,
                    modifier = Modifier.weight(1f),
                    onClick = { onFontSizeSelected(option) },
                )
            }
        }
    }
}

@Composable
private fun ThemeOption(
    mode: ThemeMode,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val icon = when (mode) {
        ThemeMode.LIGHT -> Icons.Outlined.LightMode
        ThemeMode.DARK -> Icons.Outlined.DarkMode
        ThemeMode.SYSTEM -> Icons.Outlined.SettingsBrightness
    }
    val label = when (mode) {
        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
        ThemeMode.DARK -> stringResource(R.string.theme_dark)
        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.primary else colors.surfaceMuted,
        border = if (selected) null else BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) Color.White else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun FontOption(
    option: FontSizeOption,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val label = when (option) {
        FontSizeOption.SMALL -> stringResource(R.string.font_small)
        FontSizeOption.MEDIUM -> stringResource(R.string.font_medium)
        FontSizeOption.LARGE -> stringResource(R.string.font_large)
        FontSizeOption.XL -> stringResource(R.string.font_xl)
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.surface else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.primary else colors.textSecondary,
        )
    }
}

@Composable
private fun LanguageAndUnitsCard(
    settings: UserSettings,
    languageMenuExpanded: Boolean,
    onLanguageMenuChange: (Boolean) -> Unit,
    onLanguageSelected: (LanguageOption) -> Unit,
    onUnitSelected: (UnitSystem) -> Unit,
) {
    val colors = AppTheme.colors
    ProfileCard {
        SectionTitle(
            icon = Icons.Outlined.Language,
            title = stringResource(R.string.profile_language_units),
        )

        Spacer(modifier = Modifier.height(14.dp))

        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceMuted)
                    .clickable { onLanguageMenuChange(true) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Translate,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.profile_app_language),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = settings.language.nativeName,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = colors.primarySoft,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.profile_change),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary,
                        )
                        Icon(
                            imageVector = Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = languageMenuExpanded,
                onDismissRequest = { onLanguageMenuChange(false) },
            ) {
                LanguageOption.entries.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.nativeName) },
                        onClick = { onLanguageSelected(language) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceMuted)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.profile_unit_system),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.profile_weight_distance),
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
            }

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.surfaceStrong)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                UnitSystem.entries.forEach { unit ->
                    val selected = settings.unitSystem == unit
                    Surface(
                        onClick = { onUnitSelected(unit) },
                        shape = CircleShape,
                        color = if (selected) colors.primary else Color.Transparent,
                    ) {
                        Text(
                            text = unit.label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) Color.White else colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferencesCard(
    settings: UserSettings,
    onReminderChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
) {
    val colors = AppTheme.colors
    ProfileCard {
        SectionTitle(
            icon = Icons.Outlined.FitnessCenter,
            title = stringResource(R.string.profile_preferences),
        )

        Spacer(modifier = Modifier.height(12.dp))

        PreferenceSwitchRow(
            icon = Icons.Outlined.FitnessCenter,
            title = stringResource(R.string.profile_workout_reminder),
            subtitle = stringResource(R.string.profile_daily_at, settings.reminderTime),
            checked = settings.workoutReminderEnabled,
            onCheckedChange = onReminderChange,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.divider),
        )

        Spacer(modifier = Modifier.height(8.dp))

        PreferenceSwitchRow(
            icon = Icons.Outlined.NotificationsActive,
            title = stringResource(R.string.profile_notification_alerts),
            subtitle = stringResource(R.string.profile_streaks),
            checked = settings.notificationsEnabled,
            onCheckedChange = onNotificationsChange,
        )
    }
}

@Composable
private fun PreferenceSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = colors.textSecondary,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.success,
                checkedBorderColor = colors.success,
                uncheckedThumbColor = colors.surface,
                uncheckedTrackColor = colors.surfaceStrong,
                uncheckedBorderColor = colors.borderStrong,
            ),
        )
    }
}

@Composable
private fun AboutCard(
    version: String,
    onPrivacyClick: () -> Unit,
    onTermsClick: () -> Unit,
    onVersionClick: () -> Unit,
) {
    val colors = AppTheme.colors
    ProfileCard {
        SectionTitle(
            icon = Icons.Outlined.Info,
            title = stringResource(R.string.profile_about_legal),
        )

        Spacer(modifier = Modifier.height(8.dp))

        AboutRow(
            label = stringResource(R.string.profile_privacy_policy),
            onClick = onPrivacyClick,
        )
        AboutDivider()
        AboutRow(
            label = stringResource(R.string.profile_terms_of_service),
            onClick = onTermsClick,
        )
        AboutDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.profile_about_version),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = CircleShape,
                color = colors.surfaceMuted,
                modifier = Modifier.clickable(onClick = onVersionClick),
            ) {
                Text(
                    text = "v$version",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun AboutDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AppTheme.colors.divider),
    )
}

@Composable
private fun AccountActions(
    onLogOutClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = onLogOutClick,
            shape = CircleShape,
            color = colors.surfaceMuted,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Logout,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.profile_log_out),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onDeleteClick)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.DeleteForever,
                contentDescription = null,
                tint = colors.danger,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.profile_delete_account),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.danger,
            )
        }
    }
}

@Composable
private fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = AppTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            content = content,
        )
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun EditProfileDialog(
    settings: UserSettings,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(settings.displayName) }
    var email by remember { mutableStateOf(settings.email) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.profile_email)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, email) },
                enabled = name.isNotBlank() && email.isNotBlank(),
            ) {
                Text(stringResource(R.string.profile_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_cancel))
            }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) colors.danger else colors.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_cancel))
            }
        },
    )
}

private data class InfoDialogContent(val title: String, val message: String)

@Composable
private fun InfoDialog(content: InfoDialogContent, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(content.title) },
        text = { Text(content.message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_close))
            }
        },
    )
}

private fun appVersion(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "1.0"

private fun copyAvatar(context: Context, uri: Uri): String? = runCatching {
    val target = File(context.filesDir, "profile/avatar.jpg")
    target.parentFile?.mkdirs()
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    }
    target.absolutePath
}.getOrNull()
