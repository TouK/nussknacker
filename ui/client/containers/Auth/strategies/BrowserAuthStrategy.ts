import SystemUtils from "../../../common/SystemUtils"
import {Strategy, StrategyConstructor} from "../Strategy"

// The strategy leaving authentication handling to a browser: Basic and Digest
export const BrowserAuthStrategy: StrategyConstructor = class BrowserAuthStrategy implements Strategy {
  async handleAuth(): Promise<void> {
    if (SystemUtils.hasAccessToken()) {
      // this is needed to avoid errors on stale token
      SystemUtils.clearAuthorizationToken()
    }
  }
}
