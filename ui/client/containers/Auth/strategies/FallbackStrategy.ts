import SystemUtils from "../../../common/SystemUtils"
import {Strategy, StrategyConstructor} from "../Strategy"

export const FallbackStrategy: StrategyConstructor = class FallbackStrategy implements Strategy {
  async handleAuth(): Promise<void> {
    if (SystemUtils.hasAccessToken()) {
      SystemUtils.clearAuthorizationToken()
    }
  }
}
