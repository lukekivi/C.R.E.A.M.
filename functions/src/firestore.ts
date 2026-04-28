/**
 * Firestore Admin SDK setup and the single source of truth for item paths.
 *
 * `admin.initializeApp()` is guarded so re-imports (tests, hot reloads) do
 * not throw. `itemRef(uid, itemId)` is the only place the
 * `users/{uid}/items/{itemId}` path is spelled out.
 */

import * as admin from "firebase-admin";
import {
  CollectionReference,
  DocumentReference,
  Timestamp,
} from "firebase-admin/firestore";

if (admin.apps.length === 0) {
  admin.initializeApp();
}

/** Shared Firestore Admin handle. */
export const db = admin.firestore();

/**
 * Shape of a document at `users/{uid}/items/{itemId}`.
 *
 * `accessToken` is the long-lived Plaid token; never returned to clients.
 * The Plaid `/transactions/sync` cursor is not stored server-side — it is
 * owned by the caller and passed through on each `syncTransactions` call.
 */
export interface ItemDoc {
  accessToken: string;
  itemId: string;
  institutionId: string;
  institutionName: string;
  createdAt: Timestamp;
}

/**
 * Returns the `DocumentReference` at `users/{uid}/items/{itemId}`.
 *
 * The path is centralized here so a layout change is a single-line edit.
 */
export function itemRef(
  uid: string,
  itemId: string,
): DocumentReference<ItemDoc> {
  return db
    .collection("users")
    .doc(uid)
    .collection("items")
    .doc(itemId) as DocumentReference<ItemDoc>;
}

/**
 * Returns the `CollectionReference` for all items belonging to a user.
 *
 * Used by `listInstitutions` to enumerate every linked Plaid Item.
 */
export function itemsCollection(uid: string): CollectionReference<ItemDoc> {
  return db
    .collection("users")
    .doc(uid)
    .collection("items") as CollectionReference<ItemDoc>;
}
