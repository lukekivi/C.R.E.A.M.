package com.lucaskivi.cream.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucaskivi.cream.data.model.User
import com.lucaskivi.cream.ui.LocalAppContainer

/**
 * Home screen showing the signed-in user's profile and a sign-out action.
 */
@Composable
fun HomeScreen() {
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
        ProfileSection(user = uiState.currentUser)
        Spacer(Modifier.height(32.dp))
        OutlinedButton(
            onClick = viewModel::signOut,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign Out")
        }
    }
}

/**
 * Profile card with an initials avatar and a welcome line for [user].
 *
 * @param user The signed-in user, or null while auth state is initializing.
 */
@Composable
private fun ProfileSection(user: User?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(initial = user.welcomeInitial())
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Welcome,",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = user.welcomeName(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Circular avatar rendering a single character [initial] over a tinted background.
 *
 * @param initial The character to render at the center of the avatar.
 */
@Composable
private fun Avatar(initial: String) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Returns an uppercase initial drawn from the user's display name, falling back
 * to email, and then to "?" when neither is set.
 */
private fun User?.welcomeInitial(): String =
    this?.displayName?.firstOrNull()?.uppercaseChar()?.toString()
        ?: this?.email?.firstOrNull()?.uppercaseChar()?.toString()
        ?: "?"

/**
 * Returns the user's display name, falling back to email, and then to "there".
 */
private fun User?.welcomeName(): String =
    this?.displayName
        ?: this?.email
        ?: "there"
