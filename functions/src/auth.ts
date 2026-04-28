/**
 * Auth guard shared by every callable.
 *
 * Firebase callable functions auto-attach the caller's auth context to
 * `request.auth` when the client is signed in. If `request.auth` is
 * missing the call is anonymous and we reject it with the `unauthenticated`
 * error code so the Android client sees a typed
 * `FirebaseFunctionsException`.
 */

import {CallableRequest, HttpsError} from "firebase-functions/v2/https";

/**
 * Returns the caller's Firebase Auth uid or throws `unauthenticated`.
 *
 * Call this as the first line of every callable.
 */
export function requireAuth(request: CallableRequest<unknown>): string {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }
  return uid;
}
