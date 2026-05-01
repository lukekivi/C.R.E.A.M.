package com.lucaskivi.cream.institution

import com.google.firebase.functions.FirebaseFunctions
import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.catching
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.institution.model.TransactionDelta
import com.lucaskivi.cream.remote.asMap
import kotlinx.coroutines.tasks.await

/**
 * Firebase Functions implementation of [InstitutionRemoteDataSource].
 *
 * @param functions Firebase Functions instance used to invoke backend callables.
 * @param transformer Converts responses into domain models.
 */
class InstitutionRemoteDataSourceImpl internal constructor(
    private val functions: FirebaseFunctions,
    private val transformer: InstitutionTransformer,
) : InstitutionRemoteDataSource {

    override suspend fun createLinkToken(): NetworkResult<String> =
        catching {
            val response = functions
                .getHttpsCallable(CREATE_LINK_TOKEN)
                .call()
                .await()
                .getData()
                .asMap()
            transformer.toLinkToken(response)
        }

    override suspend fun exchangePublicToken(
        publicToken: String,
        institutionId: String,
        institutionName: String,
    ): NetworkResult<LinkedInstitution> =
        catching {
            val request = mapOf(
                "publicToken" to publicToken,
                "institutionId" to institutionId,
                "institutionName" to institutionName,
            )
            val response = functions
                .getHttpsCallable(EXCHANGE_PUBLIC_TOKEN)
                .call(request)
                .await()
                .getData()
                .asMap()
            transformer.toLinkedInstitution(response, institutionId)
        }

    override suspend fun listInstitutions(): NetworkResult<List<LinkedInstitutionWithAccounts>> =
        catching {
            val response = functions
                .getHttpsCallable(LIST_INSTITUTIONS)
                .call()
                .await()
                .getData()
                .asMap()
            transformer.toLinkedInstitutionsWithAccounts(response)
        }

    override suspend fun syncTransactions(
        itemId: String,
        cursor: String?,
    ): NetworkResult<TransactionDelta> =
        catching {
            val request = mapOf(
                "itemId" to itemId,
                "cursor" to cursor,
            )
            val response = functions
                .getHttpsCallable(SYNC_TRANSACTIONS)
                .call(request)
                .await()
                .getData()
                .asMap()
            transformer.toTransactionDelta(response)
        }

    override suspend fun disconnectInstitution(itemId: String): NetworkResult<Unit> =
        catching {
            val request = mapOf("itemId" to itemId)
            functions
                .getHttpsCallable(DISCONNECT_INSTITUTION)
                .call(request)
                .await()
            Unit
        }

    /**
     * Backend callable function names.
     */
    internal companion object {
        /**
         * Callable name for [InstitutionRemoteDataSource.createLinkToken].
         */
        const val CREATE_LINK_TOKEN = "createLinkToken"

        /**
         * Callable name for [InstitutionRemoteDataSource.exchangePublicToken].
         */
        const val EXCHANGE_PUBLIC_TOKEN = "exchangePublicToken"

        /**
         * Callable name for [InstitutionRemoteDataSource.listInstitutions].
         */
        const val LIST_INSTITUTIONS = "listInstitutions"

        /**
         * Callable name for [InstitutionRemoteDataSource.syncTransactions].
         */
        const val SYNC_TRANSACTIONS = "syncTransactions"

        /**
         * Callable name for [InstitutionRemoteDataSource.disconnectInstitution].
         */
        const val DISCONNECT_INSTITUTION = "disconnectInstitution"
    }
}
