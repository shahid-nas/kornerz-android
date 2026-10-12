@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kornerz.feature.profile.privacy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.core.ui.theme.KornerzColors

/**
 * Privacy Policy viewer (P6, screen 7.5). Mirrors iOS `PrivacyPolicyVC`.
 *
 * Visual spec (per `privacy-policy-pdf` spec):
 * - "I have read and accepted" button:
 *   - `PurpleLight` (`C5D47ED`) bg, white text, Satoshi Bold 17-pt,
 *     **fully-rounded pill**
 *   - Hidden when `isFromProfile = true` (mirrors iOS line 42–44
 *     `if isComingFromUserDetail { acceptedPrivacyButton.isHidden = true }`)
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
    viewModel: PrivacyPolicyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            when (event) {
                PrivacyPolicyNavEvent.Back -> onBack()
                PrivacyPolicyNavEvent.Accepted -> onBack()
            }
        }
    }
    PrivacyPolicyContent(
        state = state,
        onBack = { viewModel.onEvent(PrivacyPolicyEvent.Back) },
        onAccept = { viewModel.onEvent(PrivacyPolicyEvent.Accept) },
    )
}

@Composable
fun PrivacyPolicyContent(
    state: PrivacyPolicyState,
    onBack: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PdfViewer(
                assetPath = state.assetPath,
                modifier = Modifier.weight(1f),
            )
            if (state.acceptButtonVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        onClick = onAccept,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KornerzColors.PurpleLight,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(
                            text = "I have read and accepted",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
