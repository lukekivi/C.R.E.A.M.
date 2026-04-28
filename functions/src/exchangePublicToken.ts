/**
 * `exchangePublicToken` callable.
 *
 * Trades the short-lived `public_token` returned by Plaid Link for a
 * long-lived `access_token` and writes it to Firestore at
 * `users/{uid}/items/{itemId}`. The access token never crosses back to
 * the client.
 *
 * Input: `{ publicToken: string, institutionId: string, institutionName: string }`
 * Output: `{ itemId: string, institutionName: string }`
 */

import {FieldValue} from "firebase-admin/firestore";
import {HttpsError, onCall} from "firebase-functions/v2/https";

import {requireAuth} from "./auth";
import {plaidError} from "./errors";
import {itemRef} from "./firestore";
import {getPlaidClient, PLAID_SECRETS} from "./plaid";

interface Input {
  publicToken?: unknown;
  institutionId?: unknown;
  institutionName?: unknown;
}

export const exchangePublicToken = onCall<Input>(
  {secrets: [...PLAID_SECRETS]},
  async (request): Promise<{itemId: string; institutionName: string}> => {
    const uid = requireAuth(request);
    const {publicToken, institutionId, institutionName} = request.data ?? {};
    if (
      typeof publicToken !== "string" || publicToken.length === 0 ||
      typeof institutionId !== "string" || institutionId.length === 0 ||
      typeof institutionName !== "string" || institutionName.length === 0
    ) {
      throw new HttpsError(
        "invalid-argument",
        "publicToken, institutionId, and institutionName are required " +
          "non-empty strings.",
      );
    }

    const plaid = getPlaidClient();
    let accessToken: string;
    let itemId: string;
    try {
      const response = await plaid.itemPublicTokenExchange({
        public_token: publicToken,
      });
      accessToken = response.data.access_token;
      itemId = response.data.item_id;
    } catch (err) {
      throw plaidError("exchangePublicToken", err);
    }

    await itemRef(uid, itemId).set({
      accessToken,
      itemId,
      institutionId,
      institutionName,
      createdAt: FieldValue.serverTimestamp(),
    });

    return {itemId, institutionName};
  },
);
