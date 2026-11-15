package com.lucaskivi.cream.screens.institutions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One account: its name, masked number and type, and its current balance. Not clickable.
 *
 * @param data What the account shows.
 */
@Composable
fun AccountItem(
    data: AccountItemData,
) {
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
                text = data.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = data.details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = data.balance,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * What an [AccountItem] shows.
 *
 * @property accountId Stable identifier of the account.
 * @property name Account name (e.g. "Total Checking").
 * @property details Masked number and type (e.g. "•••• 1234 · Depository").
 * @property balance Current balance in the account's currency, or "—" if unknown.
 */
data class AccountItemData(
    val accountId: String,
    val name: String,
    val details: String,
    val balance: String,
)
