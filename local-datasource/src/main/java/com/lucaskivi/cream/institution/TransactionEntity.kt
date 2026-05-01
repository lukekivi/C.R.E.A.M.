package com.lucaskivi.cream.institution

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lucaskivi.cream.core.institution.model.Transaction
import java.time.LocalDate

/**
 * Room entity representing a [Transaction] stored in the `transaction` table.
 *
 * Cascades deletes from the owning [FinancialAccountEntity] so that removing an account
 * automatically removes all of its transactions.
 *
 * The [date] field is stored as an ISO-8601 string and converted to/from [LocalDate] by
 * [LocalDateConverter].
 *
 * @param transactionId Primary key. Matches [Transaction.transactionId].
 * @param accountId Foreign key referencing [FinancialAccountEntity.accountId].
 * @param amount Transaction amount. Positive = debit; negative = credit.
 * @param isoCurrencyCode ISO 4217 currency code, or null if not returned.
 * @param date Date the transaction posted or is expected to post.
 * @param name Raw transaction name as provided by the institution.
 * @param merchantName Cleaned merchant name, or null if unavailable.
 * @param pending True if the transaction has not yet settled.
 * @param category Primary category label, or null if not returned.
 */
@Entity(
    tableName = "transaction",
    foreignKeys = [
        ForeignKey(
            entity = FinancialAccountEntity::class,
            parentColumns = ["accountId"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId")],
)
data class TransactionEntity(
    @PrimaryKey val transactionId: String,
    val accountId: String,
    val amount: Double,
    val isoCurrencyCode: String?,
    val date: LocalDate,
    val name: String,
    val merchantName: String?,
    val pending: Boolean,
    val category: String?,
)

/**
 * Maps this entity to its [Transaction] domain equivalent.
 */
internal fun TransactionEntity.toDomain(): Transaction =
    Transaction(
        transactionId = transactionId,
        accountId = accountId,
        amount = amount,
        isoCurrencyCode = isoCurrencyCode,
        date = date,
        name = name,
        merchantName = merchantName,
        pending = pending,
        category = category,
    )

/**
 * Maps this [Transaction] to its [TransactionEntity] database equivalent.
 */
internal fun Transaction.toEntity(): TransactionEntity =
    TransactionEntity(
        transactionId = transactionId,
        accountId = accountId,
        amount = amount,
        isoCurrencyCode = isoCurrencyCode,
        date = date,
        name = name,
        merchantName = merchantName,
        pending = pending,
        category = category,
    )
