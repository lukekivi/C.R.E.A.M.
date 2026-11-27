package com.lucaskivi.cream.screens.institutions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucaskivi.cream.core.institution.model.LinkedInstitution

/**
 * One linked institution as list entries: a header with its name and a Disconnect action, an
 * [AccountItem] per account, then a divider. Each account is its own lazy entry.
 *
 * @param data What the institution shows.
 * @param onDisconnect Called with [InstitutionItemData.institution] when the user chooses
 * Disconnect.
 */
fun LazyListScope.institutionItem(
    data: InstitutionItemData,
    onDisconnect: (LinkedInstitution) -> Unit,
) {
    item(key = data.itemId) {
        InstitutionHeader(
            name = data.name,
            onDisconnect = { onDisconnect(data.institution) },
        )
    }
    items(
        items = data.accounts,
        key = { account -> account.accountId },
    ) { account ->
        AccountItem(account)
    }
    item(key = "${data.itemId}-divider") {
        HorizontalDivider()
    }
}

/**
 * An institution's name and an overflow menu offering Disconnect.
 *
 * @param name Name of the institution (e.g. "Chase").
 * @param onDisconnect Called when the user chooses Disconnect.
 */
@Composable
private fun InstitutionHeader(
    name: String,
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
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
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
 * What an [institutionItem] shows.
 *
 * @property itemId Stable identifier of the institution.
 * @property name Name of the institution (e.g. "Chase").
 * @property accounts What each of the institution's accounts shows.
 * @property institution Payload handed back through `onDisconnect`; never read for display.
 */
data class InstitutionItemData(
    val itemId: String,
    val name: String,
    val accounts: List<AccountItemData>,
    val institution: LinkedInstitution,
)
