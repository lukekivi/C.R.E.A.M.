package com.lucaskivi.cream.screens.accountdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucaskivi.cream.ui.LocalAppContainer
import com.lucaskivi.cream.ui.component.ErrorDialog
import com.lucaskivi.cream.ui.event.EventsEffect

/**
 * Shows one account's current balance and its cached transactions grouped by date, and lets the
 * user pull to refresh them. Closes itself if the account stops existing.
 *
 * @param accountId The account to show.
 * @param onNavigateBack Called when the user leaves the screen, or when the account is gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    accountId: String,
    onNavigateBack: () -> Unit,
) {
    val appContainer = LocalAppContainer.current
    // ViewModels are scoped to the Activity, so the key keeps each account's ViewModel apart.
    val viewModel: AccountDetailViewModel = viewModel(
        key = "AccountDetail:$accountId",
        factory = AccountDetailViewModel.factory(
            appContainer,
            accountId,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    EventsEffect(viewModel.events) { event ->
        when (event) {
            AccountDetailEvent.Close -> onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val content = uiState.content
                    if (content is AccountDetailContent.Loaded) {
                        Text(content.account.name)
                    }
                },
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
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val content = uiState.content) {
                AccountDetailContent.Loading -> LoadingState()
                is AccountDetailContent.Loaded -> {
                    AccountContent(
                        account = content.account,
                        transactionGroups = content.transactionGroups,
                        notice = content.notice,
                    )
                }
            }
        }
    }

    uiState.errorMessage?.let { errorMessage ->
        ErrorDialog(
            message = errorMessage,
            onDismiss = viewModel::dismissError,
        )
    }
}

/**
 * Centered progress indicator shown until the account loads.
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
 * The balance header, then a notice if there is one, then the transactions grouped under date
 * headers.
 *
 * @param account The account to show.
 * @param transactionGroups The account's transactions, grouped by date, newest first.
 * @param notice Shown above the transactions, or null if none.
 */
@Composable
private fun AccountContent(
    account: AccountSummary,
    transactionGroups: List<TransactionDateGroup>,
    notice: TransactionsNotice?,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "balance") {
            BalanceHeader(account)
        }
        if (notice != null) {
            item(key = "notice") {
                when (notice) {
                    TransactionsNotice.Syncing -> SyncingRow()
                    TransactionsNotice.SyncFailed -> SyncFailedRow()
                    TransactionsNotice.Empty -> EmptyTransactions()
                }
            }
        }
        transactionGroups.forEach { group ->
            item(key = group.key) {
                DateHeader(group.date)
            }
            items(
                items = group.transactions,
                key = { transaction -> transaction.transactionId },
            ) { transaction ->
                TransactionRow(transaction)
            }
        }
    }
}

/**
 * The account's current balance, large, above its name, masked number and type.
 *
 * @param account The account to show.
 */
@Composable
private fun BalanceHeader(
    account: AccountSummary,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 24.dp,
            ),
    ) {
        Text(
            text = account.balance,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = account.name,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = account.details,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

/**
 * A small status row shown while the account's transactions are syncing.
 */
@Composable
private fun SyncingRow() {
    Row(
        modifier = Modifier.padding(
            horizontal = 16.dp,
            vertical = 12.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
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

/**
 * A small inline error shown after a background transaction sync fails.
 */
@Composable
private fun SyncFailedRow() {
    Text(
        text = "Couldn't load transactions. Pull to refresh.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(
            horizontal = 16.dp,
            vertical = 12.dp,
        ),
    )
}

/**
 * Shown when the account has no cached transactions and none are syncing.
 */
@Composable
private fun EmptyTransactions() {
    Text(
        text = "No transactions yet",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

/**
 * The header above one date's transactions.
 *
 * @param date The date, formatted for display.
 */
@Composable
private fun DateHeader(
    date: String,
) {
    Text(
        text = date,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 4.dp,
        ),
    )
}

/**
 * One transaction: its name, a pending marker, and its signed amount, with inflows highlighted.
 *
 * @param transaction The transaction to show.
 */
@Composable
private fun TransactionRow(
    transaction: TransactionItem,
) {
    val amountColor = if (transaction.isInflow) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val amountWeight = if (transaction.isInflow) FontWeight.SemiBold else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (transaction.isPending) {
                Text(
                    text = "Pending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = transaction.amount,
            style = MaterialTheme.typography.bodyLarge,
            color = amountColor,
            fontWeight = amountWeight,
        )
    }
}
