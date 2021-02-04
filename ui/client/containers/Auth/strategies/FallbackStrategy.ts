import SystemUtils from "../../../common/SystemUtils"
import {Strategy, StrategyConstructor} from "../Strategy"

// "do nothing" strategy - just cleanup. default. used for basic auth

export const FallbackStrategy: StrategyConstructor = class FallbackStrategy implements Strategy {
  async handleAuth(): Promise<void> {
    if (SystemUtils.hasAccessToken()) {
      SystemUtils.clearAuthorizationToken()
    }
  }
}
