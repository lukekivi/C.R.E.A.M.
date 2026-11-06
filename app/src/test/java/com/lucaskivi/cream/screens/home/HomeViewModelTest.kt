package com.lucaskivi.cream.screens.home

import com.lucaskivi.cream.core.NetworkResult
import com.lucaskivi.cream.data.repository.auth.AuthRepository
import com.lucaskivi.cream.data.repository.institution.InstitutionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val authRepository: AuthRepository = mockk()
    private val institutionRepository: InstitutionRepository = mockk()
    private lateinit var viewModel: HomeViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { authRepository.authStateFlow } returns emptyFlow()
        justRun { authRepository.signOut() }
        viewModel = createViewModel()
    }

    @AfterEach
    fun tearDown() {
        confirmVerified(authRepository, institutionRepository)
        Dispatchers.resetMain()
    }

    @Nested
    inner class SignOut {

        @Test
        fun `clears the local cache and signs out`() {
            // Setup
            coEvery { institutionRepository.clearLocalCache() } returns NetworkResult.Success(Unit)

            // Test
            viewModel.signOut()

            // Verify
            coVerify {
                authRepository.authStateFlow
                institutionRepository.clearLocalCache()
                authRepository.signOut()
            }
        }

        @Test
        fun `signs out even when clearing the local cache fails`() {
            // Setup
            val failure = NetworkResult.Failure(RuntimeException("disk error"))
            coEvery { institutionRepository.clearLocalCache() } returns failure

            // Test
            viewModel.signOut()

            // Verify
            coVerify {
                authRepository.authStateFlow
                institutionRepository.clearLocalCache()
                authRepository.signOut()
            }
        }
    }

    private fun createViewModel() =
        HomeViewModel(
            authRepository = authRepository,
            institutionRepository = institutionRepository,
        )
}
