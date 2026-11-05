package com.kornerz.feature.profile.view

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.ui.components.rememberKornerzImagePicker
import com.kornerz.core.ui.theme.Satoshi
import com.kornerz.feature.profile.accountdeletion.AccountDeletionConfirmDialog
import com.kornerz.feature.profile.accountdeletion.AccountDeletionViewModel
import com.kornerz.feature.profile.components.KornerzErrorState
import com.kornerz.feature.profile.components.KornerzLoadingView
import com.kornerz.feature.profile.components.ProfileMenuRow
import com.kornerz.feature.profile.components.UserInfoHeader

/**
 * The bottom-nav Profile tab root (P6, screen 7.1).
 *
 * P7 audit revision: the menu list now mirrors iOS
 * `UserProfileVC.setupMenuList()` (lines 75–110) row-for-row in this
 * phase's scope — Notifications / My Data / [conditional My
 * Communities] / Privacy Policy / Logout / Delete Account. The
 * always-visible "Create Community" row that the previous P7 draft
 * added (and that iOS does not surface) was removed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit = {},
    onNavigateToPremium: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToEditBasicInfo: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToMyCommunities: () -> Unit,
    onNavigateToLogout: () -> Unit,
    onNavigateToDeleteAccount: () -> Unit,
    onShowDeleteAccountError: (String) -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            when (event) {
                ProfileNavEvent.OpenPremium -> onNavigateToPremium()
                ProfileNavEvent.OpenNotifications -> onNavigateToNotifications()
                ProfileNavEvent.OpenEditBasicInfo -> onNavigateToEditBasicInfo()
                ProfileNavEvent.OpenPrivacyPolicy -> onNavigateToPrivacyPolicy()
                ProfileNavEvent.OpenMyCommunities -> onNavigateToMyCommunities()
                ProfileNavEvent.OpenLogout -> onNavigateToLogout()
                ProfileNavEvent.OpenDeleteAccount -> {
                    // Account deletion handled inline below
                }
            }
        }
    }
    val currentUser = (state.user as? AsyncValue.Success)?.data
    val onAvatarPicked: (Bitmap) -> Unit = { bitmap ->
        currentUser?.let { viewModel.uploadAvatar(it.id, bitmap) }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Profile",
                        fontFamily = Satoshi,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF353446),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF353446),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        ProfileContent(
            state = state,
            premiumTrailingText = viewModel.premiumTrailingText(),
            onPremiumTap = viewModel::onPremiumTap,
            onNotificationsTap = viewModel::onNotificationsTap,
            onEditBasicInfoTap = viewModel::onEditBasicInfoTap,
            onPrivacyPolicyTap = viewModel::onPrivacyPolicyTap,
            onMyCommunitiesTap = viewModel::onMyCommunitiesTap,
            onLogoutTap = viewModel::onLogoutTap,
            onDeleteAccountTap = viewModel::onDeleteAccountTap,
            onAvatarPicked = onAvatarPicked,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 360)
@Composable
fun ProfileContentPreview() {
    val state = ProfileState(
        user = com.kornerz.core.model.AsyncValue.Success(
            com.kornerz.core.model.User(
                id = "u1",
                email = "satoshi@example.com",
                displayName = "Satoshi Black",
                nickname = "satoshi",
                firebaseImageUrl = null,
            ),
        ),
        adminCommunitiesCount = 3,
    )
    ProfileContent(
        state = state,
        premiumTrailingText = "Yearly",
        onPremiumTap = {},
        onNotificationsTap = {},
        onEditBasicInfoTap = {},
        onPrivacyPolicyTap = {},
        onMyCommunitiesTap = {},
        onLogoutTap = {},
        onDeleteAccountTap = {},
        onAvatarPicked = {},
    )
}

@Composable
fun ProfileContent(
    state: ProfileState,
    onPremiumTap: () -> Unit,
    onNotificationsTap: () -> Unit,
    onEditBasicInfoTap: () -> Unit,
    onPrivacyPolicyTap: () -> Unit,
    onMyCommunitiesTap: () -> Unit,
    onLogoutTap: () -> Unit,
    onDeleteAccountTap: () -> Unit,
    onAvatarPicked: (Bitmap) -> Unit,
    premiumTrailingText: String = "Premium",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val picker = rememberKornerzImagePicker(
        onPhotoPicked = { uri ->
            val bitmap = decodeBitmap(context, uri)
            if (bitmap != null) onAvatarPicked(bitmap)
        },
    )
    when (val user = state.user) {
        AsyncValue.Loading -> KornerzLoadingView(modifier = modifier.fillMaxSize())
        is AsyncValue.Error -> KornerzErrorState(
            message = user.throwable.message ?: "Failed to load profile",
            onRetry = { /* session flow re-emits on retry */ },
            modifier = modifier.fillMaxSize(),
        )
        is AsyncValue.Success -> {
            val current = user.data
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .clickable { picker.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            ) },
                        contentAlignment = Alignment.Center,
                    ) {
                        UserInfoHeader(
                            user = current,
                            adminCommunitiesCount = state.adminCommunitiesCount,
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    ProfileMenuRow(
                        iconRes = android.R.drawable.ic_dialog_info,
                        title = "Notifications",
                        onClick = onNotificationsTap,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item {
                    // Per the iOS source (`UserProfileVC.setupMenuList` line
                    // 87), the row is "My Data" not "Edit Basic Info". The
                    // destination is still `profile/edit-basic-info`.
                    ProfileMenuRow(
                        iconRes = android.R.drawable.ic_menu_edit,
                        title = "My Data",
                        onClick = onEditBasicInfoTap,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (state.adminCommunitiesCount > 0) {
                    item {
                        ProfileMenuRow(
                            iconRes = android.R.drawable.ic_menu_myplaces,
                            title = "My Communities",
                            onClick = onMyCommunitiesTap,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                item {
                    ProfileMenuRow(
                        iconRes = android.R.drawable.ic_menu_help,
                        title = "Privacy Policy",
                        onClick = onPrivacyPolicyTap,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                // P7 audit revision: add the iOS `logout` (line 100)
                // and `deleteAccount` (line 105) rows that the
                // Android port was previously missing. Both reuse
                // the existing `ShellViewModel.onLogout()` +
                // `AccountDeletionViewModel` plumbing.
                item {
                    ProfileMenuRow(
                        iconRes = android.R.drawable.ic_lock_power_off,
                        title = "Logout",
                        onClick = onLogoutTap,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item {
                    ProfileMenuRow(
                        iconRes = android.R.drawable.ic_menu_delete,
                        title = "Delete Account",
                        onClick = onDeleteAccountTap,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (state.isUploadingImage) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

private fun decodeBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        null
    }
}
