package com.lucaskivi.cream.institution

import com.google.android.gms.tasks.Task
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.core.institution.model.LinkedInstitution
import com.lucaskivi.cream.core.institution.model.LinkedInstitutionWithAccounts
import com.lucaskivi.cream.institution.InstitutionRemoteDataSourceImpl.Companion.CREATE_LINK_TOKEN
import com.lucaskivi.cream.institution.InstitutionRemoteDataSourceImpl.Companion.DISCONNECT_INSTITUTION
import com.lucaskivi.cream.institution.InstitutionRemoteDataSourceImpl.Companion.EXCHANGE_PUBLIC_TOKEN
import com.lucaskivi.cream.institution.InstitutionRemoteDataSourceImpl.Companion.LIST_INSTITUTIONS
import com.lucaskivi.cream.institution.InstitutionRemoteDataSourceImpl.Companion.SYNC_TRANSACTIONS
import com.lucaskivi.cream.institution.model.TransactionDelta
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class InstitutionRemoteDataSourceImplTest {

    private val functions: FirebaseFunctions = mockk()
    private val transformer: InstitutionTransformer = mockk()
    private val sut = InstitutionRemoteDataSourceImpl(
        functions = functions,
        transformer = transformer,
    )

    private val response = mapOf<String, Any?>("key" to "value")

    private fun setupCallable(
        name: String,
        responseData: Any?,
    ): HttpsCallableReference {
        val callable = mockk<HttpsCallableReference>()
        val task = mockk<Task<HttpsCallableResult>>()
        val result = mockk<HttpsCallableResult>()
        every { functions.getHttpsCallable(name) } returns callable
        every { callable.call() } returns task
        every { callable.call(any()) } returns task
        every { task.isComplete } returns true
        every { task.isCanceled } returns false
        every { task.exception } returns null
        every { task.result } returns result
        every { result.getData() } returns responseData
        return callable
    }

    private fun setupFailingCallable(
        name: String,
        error: Exception,
    ): HttpsCallableReference {
        val callable = mockk<HttpsCallableReference>()
        every { functions.getHttpsCallable(name) } returns callable
        every { callable.call() } throws error
        every { callable.call(any()) } throws error
        return callable
    }

    @Nested
    inner class CreateLinkToken {

        @Test
        fun `returns the transformed link token`() = runTest {
            // Setup
            setupCallable(CREATE_LINK_TOKEN, response)
            every { transformer.toLinkToken(response) } returns "lt-abc"
            val expected = NetworkResult.Success("lt-abc")

            // Test
            val result = sut.createLinkToken()

            // Verify
            assertEquals(expected, result)
        }

        @Test
        fun `wraps exception in failure`() = runTest {
            // Setup
            setupFailingCallable(CREATE_LINK_TOKEN, RuntimeException("timeout"))

            // Test
            val result = sut.createLinkToken()

            // Verify
            assertTrue(result is NetworkResult.Failure)
        }

        @Test
        fun `returns failure when the response can't be transformed`() = runTest {
            // Setup
            setupCallable(CREATE_LINK_TOKEN, response)
            every { transformer.toLinkToken(response) } throws ClassCastException()

            // Test
            val result = sut.createLinkToken()

            // Verify
            assertTrue(result is NetworkResult.Failure)
        }
    }

    @Nested
    inner class ExchangePublicToken {

        @Test
        fun `passes correct arguments`() = runTest {
            // Setup
            val callable = setupCallable(EXCHANGE_PUBLIC_TOKEN, response)
            every { transformer.toLinkedInstitution(any(), any()) } returns
                LinkedInstitution("item-1", "ins_3", "Chase")
            val expectedRequest = mapOf(
                "publicToken" to "pt-abc",
                "institutionId" to "ins_3",
                "institutionName" to "Chase",
            )

            // Test
            sut.exchangePublicToken(
                publicToken = "pt-abc",
                institutionId = "ins_3",
                institutionName = "Chase",
            )

            // Verify
            verify { callable.call(expectedRequest) }
        }

        @Test
        fun `returns the transformed institution`() = runTest {
            // Setup
            setupCallable(EXCHANGE_PUBLIC_TOKEN, response)
            val institution = LinkedInstitution("item-1", "ins_3", "Chase")
            every { transformer.toLinkedInstitution(response, "ins_3") } returns institution
            val expected = NetworkResult.Success(institution)

            // Test
            val result = sut.exchangePublicToken(
                publicToken = "pt-abc",
                institutionId = "ins_3",
                institutionName = "Chase",
            )

            // Verify
            assertEquals(expected, result)
        }
    }

    @Nested
    inner class ListInstitutions {

        @Test
        fun `returns the transformed institution list`() = runTest {
            // Setup
            setupCallable(LIST_INSTITUTIONS, response)
            val institution = LinkedInstitutionWithAccounts(
                institution = LinkedInstitution("item-1", "ins_3", "Chase"),
                accounts = emptyList(),
            )
            val institutions = listOf(institution)
            every { transformer.toLinkedInstitutionsWithAccounts(response) } returns institutions
            val expected = NetworkResult.Success(institutions)

            // Test
            val result = sut.listInstitutions()

            // Verify
            assertEquals(expected, result)
        }
    }

    @Nested
    inner class SyncTransactions {

        private val delta = TransactionDelta(
            added = emptyList(),
            modified = emptyList(),
            removedIds = emptyList(),
            nextCursor = "cursor-next",
        )

        @Test
        fun `passes itemId and cursor`() = runTest {
            // Setup
            val callable = setupCallable(SYNC_TRANSACTIONS, response)
            every { transformer.toTransactionDelta(response) } returns delta
            val expectedRequest = mapOf("itemId" to "item-1", "cursor" to "cursor-prev")

            // Test
            sut.syncTransactions(itemId = "item-1", cursor = "cursor-prev")

            // Verify
            verify { callable.call(expectedRequest) }
        }

        @Test
        fun `passes null cursor for initial sync`() = runTest {
            // Setup
            val callable = setupCallable(SYNC_TRANSACTIONS, response)
            every { transformer.toTransactionDelta(response) } returns delta
            val expectedRequest = mapOf("itemId" to "item-1", "cursor" to null)

            // Test
            sut.syncTransactions(itemId = "item-1", cursor = null)

            // Verify
            verify { callable.call(expectedRequest) }
        }

        @Test
        fun `returns the transformed delta`() = runTest {
            // Setup
            setupCallable(SYNC_TRANSACTIONS, response)
            every { transformer.toTransactionDelta(response) } returns delta
            val expected = NetworkResult.Success(delta)

            // Test
            val result = sut.syncTransactions(itemId = "item-1", cursor = null)

            // Verify
            assertEquals(expected, result)
        }

        @Test
        fun `returns failure when the response can't be transformed`() = runTest {
            // Setup
            setupCallable(SYNC_TRANSACTIONS, response)
            every { transformer.toTransactionDelta(response) } throws NullPointerException()

            // Test
            val result = sut.syncTransactions(itemId = "item-1", cursor = null)

            // Verify
            assertTrue(result is NetworkResult.Failure)
        }
    }

    @Nested
    inner class DisconnectInstitution {

        @Test
        fun `passes itemId and returns success`() = runTest {
            // Setup
            val callable = setupCallable(DISCONNECT_INSTITUTION, null)
            val expectedRequest = mapOf("itemId" to "item-1")

            // Test
            val result = sut.disconnectInstitution(itemId = "item-1")

            // Verify
            assertTrue(result is NetworkResult.Success)
            verify { callable.call(expectedRequest) }
        }

        @Test
        fun `wraps exception in failure`() = runTest {
            // Setup
            setupFailingCallable(DISCONNECT_INSTITUTION, RuntimeException("server error"))

            // Test
            val result = sut.disconnectInstitution(itemId = "item-1")

            // Verify
            assertTrue(result is NetworkResult.Failure)
        }
    }
}
