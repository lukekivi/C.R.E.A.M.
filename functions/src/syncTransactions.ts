/**
 * `syncTransactions` callable.
 *
 * Pulls Plaid `/transactions/sync` deltas for one Item, draining
 * `has_more` server-side so the caller receives a single combined delta.
 *
 * The cursor is owned by the caller: passed in on each call, the new
 * value returned on each response. The server persists nothing about
 * sync state — this callable is a stateless proxy in front of Plaid.
 *
 * Input: `{ itemId: string, cursor: string | null }`
 * Output: `{ added: Txn[], modified: Txn[], removed: { transactionId }[], cursor: string }`
 */

import {HttpsError, onCall} from "firebase-functions/v2/https";
import type {Transaction as PlaidTransaction} from "plaid";

import {requireAuth} from "./auth";
import {plaidError} from "./errors";
import {itemRef} from "./firestore";
import {getPlaidClient, PLAID_SECRETS} from "./plaid";

interface Input {
  itemId?: unknown;
  cursor?: unknown;
}

interface Txn {
  transactionId: string;
  accountId: string;
  amount: number;
  isoCurrencyCode: string | null;
  date: string;
  name: string;
  merchantName: string | null;
  pending: boolean;
  category: string | null;
}

interface Output {
  added: Txn[];
  modified: Txn[];
  removed: Array<{transactionId: string}>;
  cursor: string;
}

function shape(t: PlaidTransaction): Txn {
  return {
    transactionId: t.transaction_id,
    accountId: t.account_id,
    amount: t.amount,
    isoCurrencyCode: t.iso_currency_code ?? null,
    date: t.date,
    name: t.name,
    merchantName: t.merchant_name ?? null,
    pending: t.pending,
    category: t.category?.[0] ?? null,
  };
}

export const syncTransactions = onCall<Input>(
  {secrets: [...PLAID_SECRETS]},
  async (request): Promise<Output> => {
    const uid = requireAuth(request);

    const itemId = request.data?.itemId;
    if (typeof itemId !== "string" || itemId.length === 0) {
      throw new HttpsError(
        "invalid-argument",
        "itemId is a required non-empty string.",
      );
    }

    const cursorRaw = request.data?.cursor;
    if (
      cursorRaw !== null &&
      cursorRaw !== undefined &&
      typeof cursorRaw !== "string"
    ) {
      throw new HttpsError(
        "invalid-argument",
        "cursor must be a string or null.",
      );
    }
    let cursor: string | null =
      typeof cursorRaw === "string" && cursorRaw.length > 0 ?
        cursorRaw :
        null;

    const snap = await itemRef(uid, itemId).get();
    const item = snap.data();
    if (!snap.exists || !item) {
      throw new HttpsError("not-found", "No linked item with that id.");
    }

    const plaid = getPlaidClient();
    const added: Txn[] = [];
    const modified: Txn[] = [];
    const removed: Array<{transactionId: string}> = [];
    let hasMore = true;

    try {
      while (hasMore) {
        const response = await plaid.transactionsSync({
          access_token: item.accessToken,
          cursor: cursor ?? undefined,
        });
        const data = response.data;
        for (const t of data.added) {
          added.push(shape(t));
        }
        for (const t of data.modified) {
          modified.push(shape(t));
        }
        for (const r of data.removed) {
          if (r.transaction_id) {
            removed.push({transactionId: r.transaction_id});
          }
        }
        cursor = data.next_cursor;
        hasMore = data.has_more;
      }
    } catch (err) {
      throw plaidError("syncTransactions", err);
    }

    return {added, modified, removed, cursor: cursor ?? ""};
  },
);
