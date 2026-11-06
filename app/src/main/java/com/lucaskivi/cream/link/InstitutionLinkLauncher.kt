package com.lucaskivi.cream.link

import android.content.Context
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.lucaskivi.cream.link.model.InstitutionLinkResult
import com.plaid.link.OpenPlaidLink
import com.plaid.link.Plaid
import com.plaid.link.PlaidSession
import com.plaid.link.configuration.linkTokenConfiguration
import com.plaid.link.result.LinkError
import com.plaid.link.result.LinkExit
import com.plaid.link.result.LinkResult
import com.plaid.link.result.LinkSuccess

/**
 * Message used for an [InstitutionLinkResult.Error] when the link flow gives no usable explanation.
 */
internal const val INSTITUTION_LINK_FALLBACK_ERROR_MESSAGE = "Couldn't link the institution. Please try again."

/**
 * Handle for starting the institution-linking flow, obtained from
 * [rememberInstitutionLinkLauncher].
 *
 * @param context Context used to start each link flow.
 * @param activityResultLauncher Launcher that runs the link flow and delivers its outcome.
 */
class InstitutionLinkLauncher internal constructor(
    private val context: Context,
    private val activityResultLauncher: ManagedActivityResultLauncher<PlaidSession, LinkResult>,
) {

    /**
     * Opens the institution-linking flow for [linkToken]. The outcome is delivered to the
     * `onResult` callback passed to [rememberInstitutionLinkLauncher].
     *
     * @param linkToken Link token issued by the backend for this session.
     */
    fun launch(linkToken: String) {
        // Plaid Link sessions are single-use, so each launch creates a new session.
        val configuration = linkTokenConfiguration { token = linkToken }
        val session = Plaid.createPlaidLinkSession(context, configuration)
        activityResultLauncher.launch(session)
    }
}

/**
 * Remembers an [InstitutionLinkLauncher] whose results are reported to [onResult] as
 * [InstitutionLinkResult]s.
 *
 * @param onResult Called once per launch with the outcome of the link flow.
 */
@Composable
fun rememberInstitutionLinkLauncher(onResult: (InstitutionLinkResult) -> Unit): InstitutionLinkLauncher {
    val context = LocalContext.current
    val activityResultLauncher = rememberLauncherForActivityResult(OpenPlaidLink()) { linkResult ->
        onResult(linkResult.toInstitutionLinkResult())
    }
    return remember(context, activityResultLauncher) {
        InstitutionLinkLauncher(context, activityResultLauncher)
    }
}

/**
 * Translates a Plaid [LinkResult] into an SDK-free [InstitutionLinkResult].
 *
 * A success without institution metadata is an [InstitutionLinkResult.Error], because linking
 * requires the institution's id and name. An exit without an error is a
 * [InstitutionLinkResult.Cancelled].
 */
internal fun LinkResult.toInstitutionLinkResult(): InstitutionLinkResult =
    when (this) {
        is LinkSuccess -> this.toInstitutionLinkResult()
        is LinkExit -> {
            this.error
                ?.toInstitutionLinkError()
                ?: InstitutionLinkResult.Cancelled
        }
    }

/**
 * Translates a [LinkSuccess] into an [InstitutionLinkResult.Success], or an
 * [InstitutionLinkResult.Error] when the institution is missing.
 */
private fun LinkSuccess.toInstitutionLinkResult(): InstitutionLinkResult {
    val institution = this.metadata.institution
    return if (institution == null) {
        InstitutionLinkResult.Error(INSTITUTION_LINK_FALLBACK_ERROR_MESSAGE)
    } else {
        InstitutionLinkResult.Success(
            publicToken = this.publicToken,
            institutionId = institution.id,
            institutionName = institution.name,
        )
    }
}

/**
 * Translates a [LinkError] into an [InstitutionLinkResult.Error], preferring the user-facing
 * message, then the developer-facing one, then [INSTITUTION_LINK_FALLBACK_ERROR_MESSAGE]. Blank
 * messages are skipped.
 */
private fun LinkError.toInstitutionLinkError(): InstitutionLinkResult.Error {
    val message = this.displayMessage?.takeIf { it.isNotBlank() }
        ?: this.errorMessage.takeIf { it.isNotBlank() }
        ?: INSTITUTION_LINK_FALLBACK_ERROR_MESSAGE
    return InstitutionLinkResult.Error(message)
}
