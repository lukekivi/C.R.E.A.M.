/**
 * `createLinkToken` callable.
 *
 * Mints a short-lived Plaid Link token the Android client uses to open
 * Plaid Link. The link token is one-shot and ties the resulting Item to
 * the caller's Firebase Auth uid via `client_user_id`.
 *
 * Input: `{}`
 * Output: `{ linkToken: string }`
 */

import {onCall} from "firebase-functions/v2/https";
import {CountryCode, Products} from "plaid";

import {requireAuth} from "./auth";
import {plaidError} from "./errors";
import {getPlaidClient, PLAID_SECRETS} from "./plaid";

export const createLinkToken = onCall(
  {secrets: [...PLAID_SECRETS]},
  async (request): Promise<{linkToken: string}> => {
    const uid = requireAuth(request);
    const plaid = getPlaidClient();
    try {
      const response = await plaid.linkTokenCreate({
        user: {client_user_id: uid},
        client_name: "CREAM",
        products: [Products.Transactions],
        country_codes: [CountryCode.Us],
        language: "en",
      });
      return {linkToken: response.data.link_token};
    } catch (err) {
      throw plaidError("createLinkToken", err);
    }
  },
);
