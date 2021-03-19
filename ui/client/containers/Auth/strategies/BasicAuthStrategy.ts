import SystemUtils from "../../../common/SystemUtils"
import {Strategy, StrategyConstructor} from "../Strategy"

export const BasicAuthStrategy: StrategyConstructor = class FallbackStrategy implements Strategy {
  async handleAuth(): Promise<void> {
    if (SystemUtils.hasAccessToken()) {
      // this in needed to avoid errors on stale token
      SystemUtils.clearAuthorizationToken()
    }
  }
}
