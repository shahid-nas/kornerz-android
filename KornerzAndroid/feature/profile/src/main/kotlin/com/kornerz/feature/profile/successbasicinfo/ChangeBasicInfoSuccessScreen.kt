package com.kornerz.feature.profile.successbasicinfo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.core.ui.theme.KornerzColors

/**
 * Modal Card that follows the change-basic-info success state
 * (P6, task 5.14). Mirrors iOS `ChangeBasicInfoSuccessVC`.
 *
 * Visual spec:
 * - Card: 20-pt corner radius
 * - Message: Satoshi Black 22-pt, `SlateLight` (`C353446`)
 * - Close button: `PurpleLight` (`C5D47ED`) bg, white text, Satoshi
 *   Bold 17-pt, **fully-rounded pill**
 */
@Composable
fun ChangeBasicInfoSuccessDialog(
    state: ChangeBasicInfoSuccessState,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Text(
                    text = state.message,
                    color = KornerzColors.SlateLight,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onClose,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KornerzColors.PurpleLight,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Close",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
fun ChangeBasicInfoSuccessScreen(
    onClose: () -> Unit,
    viewModel: ChangeBasicInfoSuccessViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { onClose() }
    }
    ChangeBasicInfoSuccessDialog(state = state, onClose = onClose)
}
