package com.lucaskivi.cream.screens.institutions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.link.rememberInstitutionLinkLauncher
import com.lucaskivi.cream.ui.LocalAppContainer
import com.lucaskivi.cream.ui.component.ErrorDialog
import com.lucaskivi.cream.ui.event.EventsEffect

/**
 * Lists the user's linked institutions with their accounts and balances, and lets the user
 * link a new one, pull to refresh, disconnect one, and open an account.
 *
 * @param onNavigateBack Called when the user leaves the screen.
 * @param onAccountClick Called with an account's ID when the user taps that account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstitutionsScreen(
    onNavigateBack: () -> Unit,
    onAccountClick: (String) -> Unit,
) {
    val appContainer = LocalAppContainer.current
    val viewModel: InstitutionsViewModel = viewModel(factory = InstitutionsViewModel.factory(appContainer))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val linkLauncher = rememberInstitutionLinkLauncher(viewModel::onLinkResult)

    EventsEffect(viewModel.events) { event ->
        when (event) {
            is InstitutionsEvent.OpenLink -> {
                linkLauncher.launch(event.linkToken)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Institutions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState.isLinkEnabled) {
                FloatingActionButton(onClick = viewModel::linkInstitution) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Link an institution",
                    )
                }
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val content = uiState.content) {
                InstitutionsContent.Loading -> LoadingState()
                InstitutionsContent.Empty -> {
                    EmptyState(
                        isLinkEnabled = uiState.isLinkEnabled,
                        onLinkInstitution = viewModel::linkInstitution,
                    )
                }
                is InstitutionsContent.Loaded -> {
                    InstitutionList(
                        institutions = content.institutions,
                        onDisconnect = viewModel::requestDisconnect,
                        onAccountClick = onAccountClick,
                    )
                }
            }
        }
    }

    when (val dialog = uiState.dialog) {
        is InstitutionsDialog.ConfirmDisconnect -> {
            DisconnectDialog(
                institution = dialog.institution,
                onConfirm = viewModel::confirmDisconnect,
                onDismiss = viewModel::dismissDialog,
            )
        }
        is InstitutionsDialog.Error -> {
            ErrorDialog(
                message = dialog.message,
                onDismiss = viewModel::dismissDialog,
            )
        }
        null -> Unit
    }
}

/**
 * Centered progress indicator shown until the first list of institutions loads.
 */
@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Shown when no institutions are linked; offers linking one. Scrollable so pull to refresh
 * still works on it.
 *
 * @param isLinkEnabled Whether the user can start linking; the button is disabled otherwise.
 * @param onLinkInstitution Called when the user taps "Link an institution".
 */
@Composable
private fun EmptyState(
    isLinkEnabled: Boolean,
    onLinkInstitution: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No linked institutions yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Link a bank or card to see your accounts and balances.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onLinkInstitution,
            enabled = isLinkEnabled,
        ) {
            Text("Link an institution")
        }
    }
}

/**
 * Every linked institution as a header followed by its account rows.
 *
 * @param institutions The institutions to list.
 * @param onDisconnect Called when the user chooses to disconnect an institution.
 * @param onAccountClick Called with an account's ID when the user taps that account.
 */
@Composable
private fun InstitutionList(
    institutions: List<InstitutionItem>,
    onDisconnect: (LinkedInstitution) -> Unit,
    onAccountClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        institutions.forEach { institutionItem ->
            val institution = institutionItem.institution
            item(key = institution.itemId) {
                InstitutionHeader(
                    institution = institution,
                    syncStatus = institutionItem.syncStatus,
                    onDisconnect = { onDisconnect(institution) },
                )
            }
            items(
                items = institutionItem.accounts,
                key = { account -> account.accountId },
            ) { account ->
                AccountRow(
                    account = account,
                    onClick = { onAccountClick(account.accountId) },
                )
            }
            item(key = "${institution.itemId}-divider") {
                HorizontalDivider()
            }
        }
    }
}

/**
 * An institution's name, its transaction sync status, and an overflow menu offering Disconnect.
 *
 * @param institution The institution to show.
 * @param syncStatus Where the institution's transaction sync stands.
 * @param onDisconnect Called when the user chooses Disconnect.
 */
@Composable
private fun InstitutionHeader(
    institution: LinkedInstitution,
    syncStatus: InstitutionSyncStatus,
    onDisconnect: () -> Unit,
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                top = 16.dp,
                bottom = 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = institution.institutionName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            SyncStatusLine(syncStatus)
        }
        Box {
            IconButton(onClick = { isMenuOpen = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                )
            }
            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = { isMenuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Disconnect") },
                    onClick = {
                        isMenuOpen = false
                        onDisconnect()
                    },
                )
            }
        }
    }
}

/**
 * A small status line for an institution's transaction sync: a spinner while syncing, a quiet
 * error line after a failed sync, and nothing when idle.
 *
 * @param syncStatus Where the institution's transaction sync stands.
 */
@Composable
private fun SyncStatusLine(
    syncStatus: InstitutionSyncStatus,
) {
    when (syncStatus) {
        InstitutionSyncStatus.Idle -> Unit
        InstitutionSyncStatus.Syncing -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Syncing transactions…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        InstitutionSyncStatus.Failed -> {
            Text(
                text = "Couldn't sync transactions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * One account: its name, masked number and type, and its current balance.
 *
 * @param account The account to show.
 * @param onClick Called when the user taps the account.
 */
@Composable
private fun AccountRow(
    account: AccountItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = account.details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = account.balance,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * Confirms disconnecting [institution].
 *
 * @param institution The institution to disconnect.
 * @param onConfirm Called when the user confirms.
 * @param onDismiss Called when the user cancels or dismisses the dialog.
 */
@Composable
private fun DisconnectDialog(
    institution: LinkedInstitution,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disconnect ${institution.institutionName}?") },
        text = { Text("Its accounts and transactions will be removed from CREAM.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Disconnect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
