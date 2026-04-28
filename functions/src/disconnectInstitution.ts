/**
 * `disconnectInstitution` callable.
 *
 * Revokes the Plaid access token for an Item and deletes the Firestore
 * item doc. If Plaid reports `ITEM_NOT_FOUND` (already revoked elsewhere)
 * the Firestore doc is still deleted — the caller-visible state must
 * reach "gone."
 *
 * Input: `{ itemId: string }`
 * Output: `{ ok: true }`
 */

import {HttpsError, onCall} from "firebase-functions/v2/https";

import {requireAuth} from "./auth";
import {extractPlaidErrorCode, plaidError} from "./errors";
import {itemRef} from "./firestore";
import {getPlaidClient, PLAID_SECRETS} from "./plaid";

interface Input {
  itemId?: unknown;
}

export const disconnectInstitution = onCall<Input>(
  {secrets: [...PLAID_SECRETS]},
  async (request): Promise<{ok: true}> => {
    const uid = requireAuth(request);
    const itemId = request.data?.itemId;
    if (typeof itemId !== "string" || itemId.length === 0) {
      throw new HttpsError(
        "invalid-argument",
        "itemId is a required non-empty string.",
      );
    }

    const ref = itemRef(uid, itemId);
    const snap = await ref.get();
    const item = snap.data();
    if (!snap.exists || !item) {
      throw new HttpsError("not-found", "No linked item with that id.");
    }

    const plaid = getPlaidClient();
    try {
      await plaid.itemRemove({access_token: item.accessToken});
    } catch (err) {
      const code = extractPlaidErrorCode(err);
      if (code !== "ITEM_NOT_FOUND") {
        throw plaidError("disconnectInstitution", err);
      }
      // Plaid says it's already gone; still delete the Firestore doc.
    }

    await ref.delete();
    return {ok: true};
  },
);
