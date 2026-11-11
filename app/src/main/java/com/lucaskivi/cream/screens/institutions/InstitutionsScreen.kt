package com.lucaskivi.cream.screens.institutions

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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.link.rememberInstitutionLinkLauncher
import com.lucaskivi.cream.ui.LocalAppContainer
import com.lucaskivi.cream.ui.format.MISSING_AMOUNT_PLACEHOLDER
import com.lucaskivi.cream.ui.format.formatCurrency

/**
 * Lists the user's linked institutions with their accounts and balances, and lets the user
 * link a new one, pull to refresh, and disconnect one.
 *
 * @param onNavigateBack Called when the user leaves the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstitutionsScreen(
    onNavigateBack: () -> Unit,
) {
    val appContainer = LocalAppContainer.current
    val viewModel: InstitutionsViewModel = viewModel(factory = InstitutionsViewModel.factory(appContainer))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val linkLauncher = rememberInstitutionLinkLauncher(viewModel::onLinkResult)

    LaunchedEffect(uiState.pendingLinkToken) {
        val linkToken = uiState.pendingLinkToken
        if (linkToken != null) {
            linkLauncher.launch(linkToken)
            viewModel.onLinkLaunched()
        }
    }

    LaunchedEffect(uiState.error) {
        val error = uiState.error
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Institutions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!uiState.isLinking) {
                FloatingActionButton(onClick = viewModel::linkInstitution) {
                    Icon(Icons.Default.Add, contentDescription = "Link an institution")
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
            when {
                uiState.isLoading -> LoadingState()
                uiState.institutions.isEmpty() -> EmptyState(
                    isLinking = uiState.isLinking,
                    onLinkInstitution = viewModel::linkInstitution,
                )
                else -> InstitutionList(
                    institutions = uiState.institutions,
                    onDisconnect = viewModel::requestDisconnect,
                )
            }
        }
    }

    val pendingDisconnect = uiState.pendingDisconnect
    if (pendingDisconnect != null) {
        DisconnectDialog(
            institution = pendingDisconnect,
            onConfirm = viewModel::confirmDisconnect,
            onDismiss = viewModel::dismissDisconnect,
        )
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
 * @param isLinking Whether linking is already in progress, which disables the button.
 * @param onLinkInstitution Called when the user taps "Link an institution".
 */
@Composable
private fun EmptyState(
    isLinking: Boolean,
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
            enabled = !isLinking,
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
 */
@Composable
private fun InstitutionList(
    institutions: List<LinkedInstitutionWithAccounts>,
    onDisconnect: (LinkedInstitution) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        institutions.forEach { institutionWithAccounts ->
            val institution = institutionWithAccounts.institution
            item(key = institution.itemId) {
                InstitutionHeader(
                    institution = institution,
                    onDisconnect = { onDisconnect(institution) },
                )
            }
            items(
                items = institutionWithAccounts.accounts,
                key = { account -> account.accountId },
            ) { account ->
                AccountRow(account)
            }
            item(key = "${institution.itemId}-divider") {
                HorizontalDivider()
            }
        }
    }
}

/**
 * An institution's name and an overflow menu offering Disconnect.
 *
 * @param institution The institution to show.
 * @param onDisconnect Called when the user chooses Disconnect.
 */
@Composable
private fun InstitutionHeader(
    institution: LinkedInstitution,
    onDisconnect: () -> Unit,
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = institution.institutionName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { isMenuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
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
 * One account: its name, masked number and type, and its current balance. Not clickable.
 *
 * @param account The account to show.
 */
@Composable
private fun AccountRow(
    account: FinancialAccount,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.displayName,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = account.details(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val balance = remember(account) { account.formattedBalance() }
        Text(
            text = balance,
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

/**
 * Returns the account's type, preceded by its masked number when known
 * (e.g. "•••• 1234 · Depository").
 */
private fun FinancialAccount.details(): String {
    val type = this.type.name
        .lowercase()
        .replaceFirstChar { it.titlecase() }
    return listOfNotNull(this.mask?.let { "•••• $it" }, type).joinToString(" · ")
}

/**
 * Returns the current balance formatted in the account's currency, or
 * [MISSING_AMOUNT_PLACEHOLDER] when the balance is unknown.
 */
private fun FinancialAccount.formattedBalance(): String =
    this.balanceCurrent
        ?.let { balance -> formatCurrency(balance, this.isoCurrencyCode) }
        ?: MISSING_AMOUNT_PLACEHOLDER
