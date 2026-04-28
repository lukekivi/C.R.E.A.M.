/**
 * `listInstitutions` callable.
 *
 * Returns every institution the caller has linked, with current account
 * balances fetched live from Plaid. The Plaid `accountsGet` calls fan out
 * via `Promise.all` so total latency is bounded by the slowest
 * institution.
 *
 * Input: `{}`
 * Output: `{ institutions: InstitutionView[] }` (see types below).
 */

import {onCall} from "firebase-functions/v2/https";

import {requireAuth} from "./auth";
import {plaidError} from "./errors";
import {itemsCollection} from "./firestore";
import {getPlaidClient, PLAID_SECRETS} from "./plaid";

interface AccountView {
  accountId: string;
  name: string;
  mask: string | null;
  type: string;
  subtype: string | null;
  balance: {
    available: number | null;
    current: number | null;
    isoCurrencyCode: string | null;
  };
}

interface InstitutionView {
  itemId: string;
  institutionId: string;
  institutionName: string;
  accounts: AccountView[];
}

export const listInstitutions = onCall(
  {secrets: [...PLAID_SECRETS]},
  async (request): Promise<{institutions: InstitutionView[]}> => {
    const uid = requireAuth(request);
    const plaid = getPlaidClient();
    const snapshot = await itemsCollection(uid).get();

    const institutions = await Promise.all(
      snapshot.docs.map(async (doc): Promise<InstitutionView> => {
        const item = doc.data();
        try {
          const response = await plaid.accountsGet({
            access_token: item.accessToken,
          });
          const accounts: AccountView[] = response.data.accounts.map((a) => ({
            accountId: a.account_id,
            name: a.name,
            mask: a.mask ?? null,
            type: a.type,
            subtype: a.subtype ?? null,
            balance: {
              available: a.balances.available ?? null,
              current: a.balances.current ?? null,
              isoCurrencyCode: a.balances.iso_currency_code ?? null,
            },
          }));
          return {
            itemId: item.itemId,
            institutionId: item.institutionId,
            institutionName: item.institutionName,
            accounts,
          };
        } catch (err) {
          throw plaidError("listInstitutions:accountsGet", err);
        }
      }),
    );

    return {institutions};
  },
);
