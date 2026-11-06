package com.lucaskivi.cream.screens.institutions

import android.content.Context
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.plaid.link.OpenPlaidLink
import com.plaid.link.Plaid
import com.plaid.link.PlaidSession
import com.plaid.link.configuration.linkTokenConfiguration
import com.plaid.link.result.LinkError
import com.plaid.link.result.LinkExit
import com.plaid.link.result.LinkResult
import com.plaid.link.result.LinkSuccess

/**
 * Message used for a [PlaidLinkResult.Error] when Plaid gives no usable explanation.
 */
internal const val PLAID_LINK_FALLBACK_ERROR_MESSAGE = "Couldn't link the institution. Please try again."

/**
 * Handle for opening Plaid Link, obtained from [rememberPlaidLinkLauncher].
 *
 * @param context Context used to create each Link session.
 * @param activityResultLauncher Launcher registered with Plaid's activity-result contract.
 */
class PlaidLinkLauncher internal constructor(
    private val context: Context,
    private val activityResultLauncher: ManagedActivityResultLauncher<PlaidSession, LinkResult>,
) {

    /**
     * Opens Plaid Link for [linkToken]. The outcome is delivered to the `onResult` callback
     * passed to [rememberPlaidLinkLauncher].
     *
     * @param linkToken Link token issued by the backend for this session.
     */
    fun launch(linkToken: String) {
        // Plaid sessions are single-use, so each launch gets a fresh one.
        val configuration = linkTokenConfiguration { token = linkToken }
        val session = Plaid.createPlaidLinkSession(context, configuration)
        activityResultLauncher.launch(session)
    }
}

/**
 * Remembers a [PlaidLinkLauncher] whose results are reported to [onResult] as
 * [PlaidLinkResult]s.
 *
 * @param onResult Called once per launch with the outcome of the Link session.
 */
@Composable
fun rememberPlaidLinkLauncher(onResult: (PlaidLinkResult) -> Unit): PlaidLinkLauncher {
    val context = LocalContext.current
    val activityResultLauncher = rememberLauncherForActivityResult(OpenPlaidLink()) { linkResult ->
        onResult(linkResult.toPlaidLinkResult())
    }
    return remember(context, activityResultLauncher) {
        PlaidLinkLauncher(context, activityResultLauncher)
    }
}

/**
 * Translates a Plaid [LinkResult] into an SDK-free [PlaidLinkResult].
 *
 * A success without institution metadata is a [PlaidLinkResult.Error], because linking
 * requires the institution's id and name. An exit without an error is a
 * [PlaidLinkResult.Cancelled].
 */
internal fun LinkResult.toPlaidLinkResult(): PlaidLinkResult =
    when (this) {
        is LinkSuccess -> toPlaidLinkResult()
        is LinkExit -> error?.toPlaidLinkError() ?: PlaidLinkResult.Cancelled
    }

/**
 * Translates a [LinkSuccess] into a [PlaidLinkResult.Success], or a [PlaidLinkResult.Error]
 * when the institution is missing.
 */
private fun LinkSuccess.toPlaidLinkResult(): PlaidLinkResult {
    val institution = metadata.institution
        ?: return PlaidLinkResult.Error(PLAID_LINK_FALLBACK_ERROR_MESSAGE)
    return PlaidLinkResult.Success(
        publicToken = publicToken,
        institutionId = institution.id,
        institutionName = institution.name,
    )
}

/**
 * Translates a [LinkError] into a [PlaidLinkResult.Error], preferring the user-facing message,
 * then the developer-facing one, then [PLAID_LINK_FALLBACK_ERROR_MESSAGE]. Blank messages are
 * skipped.
 */
private fun LinkError.toPlaidLinkError(): PlaidLinkResult.Error {
    val message = displayMessage?.takeIf { it.isNotBlank() }
        ?: errorMessage.takeIf { it.isNotBlank() }
        ?: PLAID_LINK_FALLBACK_ERROR_MESSAGE
    return PlaidLinkResult.Error(message)
}
