/**
 * Functions entry point.
 *
 * Re-exports every callable. Firebase reads each top-level export name and
 * deploys it as a callable with that name. No logic lives here.
 */

export {createLinkToken} from "./createLinkToken";
export {disconnectInstitution} from "./disconnectInstitution";
export {exchangePublicToken} from "./exchangePublicToken";
export {listInstitutions} from "./listInstitutions";
export {syncTransactions} from "./syncTransactions";
