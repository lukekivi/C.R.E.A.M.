package com.lucaskivi.cream.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucaskivi.cream.ui.LocalAppContainer

/**
 * Home screen showing the signed-in user's profile, a way to their linked institutions, and a
 * sign-out action.
 *
 * @param onNavigateToInstitutions Called when the user opens their linked institutions.
 */
@Composable
fun HomeScreen(
    onNavigateToInstitutions: () -> Unit,
) {
    val appContainer = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(appContainer))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        ProfileSection(data = uiState.profile)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNavigateToInstitutions,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Institutions")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = viewModel::signOut,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign Out")
        }
    }
}
