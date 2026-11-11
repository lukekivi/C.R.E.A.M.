package com.lucaskivi.cream.screens.accountdetail.model

import com.lucaskivi.cream.core.institution.model.Transaction
import java.time.LocalDate

/**
 * The transactions of one account that fall on a single date.
 *
 * @property date The date shared by every transaction in the group.
 * @property transactions The transactions on [date], newest first.
 */
data class TransactionDateGroup(
    val date: LocalDate,
    val transactions: List<Transaction>,
)
