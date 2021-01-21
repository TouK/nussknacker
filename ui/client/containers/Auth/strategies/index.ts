import {AuthBackends} from "../../../reducers/settings"
import {FallbackStrategy} from "./FallbackStrategy"
import {OAuth2Strategy} from "./OAuth2Strategy"
import {RemoteAuthStrategy} from "./RemoteAuthStrategy"
import {StrategyConstructor} from "../Strategy"

export const STRATEGIES: Partial<Record<AuthBackends | "fallback", StrategyConstructor>> = {
  [AuthBackends.OAUTH2]: OAuth2Strategy,
  [AuthBackends.REMOTE]: RemoteAuthStrategy,
  fallback: FallbackStrategy,
}
