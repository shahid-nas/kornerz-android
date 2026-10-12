@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kornerz.feature.profile.editbasicinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.core.model.EditableField

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditBasicInfoScreen(
    onBack: () -> Unit,
    onNavigateToChange: (EditableField) -> Unit,
    viewModel: EditBasicInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is EditBasicInfoNavEvent.OpenChangeScreen -> onNavigateToChange(event.field)
                EditBasicInfoNavEvent.Back -> onBack()
            }
        }
    }
    EditBasicInfoContent(
        state = state,
        onChangeField = viewModel::onChangeField,
        onBack = viewModel::onBack,
    )
}

@Composable
fun EditBasicInfoContent(
    state: EditBasicInfoState,
    onChangeField: (EditableField) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Edit Basic Info") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val user = state.user
        if (user == null) {
            Text("Loading…", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                count = state.rows.size,
                key = { idx -> state.rows[idx].name },
            ) { idx ->
                val field = state.rows[idx]
                BasicInfoRow(
                    title = fieldLabel(field),
                    value = valueForField(user, field),
                    isChangeable = field == EditableField.NAME || field == EditableField.NICKNAME,
                    isPassword = field == EditableField.PASSWORD,
                    onChange = { onChangeField(field) },
                )
            }
        }
    }
}
