package com.lucaskivi.cream.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Profile card with an initial avatar and a welcome line.
 *
 * @param data What the profile shows.
 */
@Composable
fun ProfileSection(
    data: ProfileSectionData,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(initial = data.initial)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Welcome,",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = data.name,
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
private fun Avatar(
    initial: String,
) {
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
 * What a [ProfileSection] shows.
 *
 * @property initial Single uppercase character shown in the avatar (e.g. "J").
 * @property name Name the user is welcomed by (e.g. "Jane").
 */
data class ProfileSectionData(
    val initial: String,
    val name: String,
)
