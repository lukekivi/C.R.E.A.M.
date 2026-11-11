package com.lucaskivi.cream.screens.accountdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucaskivi.cream.core.institution.model.FinancialAccount
import com.lucaskivi.cream.core.institution.model.Transaction
import com.lucaskivi.cream.screens.accountdetail.model.TransactionDateGroup
import com.lucaskivi.cream.ui.LocalAppContainer
import com.lucaskivi.cream.ui.format.formattedBalance
import com.lucaskivi.cream.ui.format.formattedDetails
import com.lucaskivi.cream.ui.format.formattedSignedAmount
import com.lucaskivi.cream.ui.format.isInflow
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
        factory = AccountDetailViewModel.factory(appContainer, accountId),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isAccountMissing) {
        if (uiState.isAccountMissing) {
            onNavigateBack()
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
                title = { Text(uiState.account?.displayName.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val account = uiState.account
            if (uiState.isLoading || account == null) {
                LoadingState()
            } else {
                AccountContent(
                    account = account,
                    transactionGroups = uiState.transactionGroups,
                )
            }
        }
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
 * The balance header followed by the transactions grouped under date headers, or an empty
 * state.
 *
 * @param account The account to show.
 * @param transactionGroups The account's transactions, grouped by date, newest first.
 */
@Composable
private fun AccountContent(
    account: FinancialAccount,
    transactionGroups: List<TransactionDateGroup>,
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "balance") {
            BalanceHeader(account)
        }
        if (transactionGroups.isEmpty()) {
            item(key = "empty") {
                EmptyTransactions()
            }
        }
        transactionGroups.forEach { group ->
            item(key = group.date.toString()) {
                DateHeader(
                    group = group,
                    formatter = dateFormatter,
                )
            }
            items(
                items = group.transactions,
                key = { transaction -> transaction.transactionId },
            ) { transaction ->
                TransactionRow(
                    transaction = transaction,
                    fallbackCurrencyCode = account.isoCurrencyCode,
                )
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
    account: FinancialAccount,
) {
    val balance = remember(account) { account.formattedBalance() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Text(
            text = balance,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = account.displayName,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = account.formattedDetails(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

/**
 * Shown when the account has no cached transactions.
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
 * @param group The date group the header introduces.
 * @param formatter Formats the group's date.
 */
@Composable
private fun DateHeader(
    group: TransactionDateGroup,
    formatter: DateTimeFormatter,
) {
    Text(
        text = formatter.format(group.date),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/**
 * One transaction: its merchant (or name), a pending marker, and its signed amount, with inflows
 * highlighted.
 *
 * @param transaction The transaction to show.
 * @param fallbackCurrencyCode Currency used when the transaction doesn't name one.
 */
@Composable
private fun TransactionRow(
    transaction: Transaction,
    fallbackCurrencyCode: String?,
) {
    val amount = remember(transaction, fallbackCurrencyCode) { transaction.formattedSignedAmount(fallbackCurrencyCode) }
    val isInflow = transaction.isInflow
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.merchantName ?: transaction.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (transaction.pending) {
                Text(
                    text = "Pending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = amount,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isInflow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isInflow) FontWeight.SemiBold else null,
        )
    }
}
