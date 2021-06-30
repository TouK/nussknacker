import {AuthBackends} from "../../../reducers/settings"
import {BasicAuthStrategy} from "./BasicAuthStrategy"
import {OAuth2Strategy} from "./OAuth2Strategy"
import {StrategyConstructor} from "../Strategy"

export const STRATEGIES: Partial<Record<AuthBackends, StrategyConstructor>> = {
  [AuthBackends.OAUTH2]: OAuth2Strategy,
  [AuthBackends.BASIC]: BasicAuthStrategy,
  [AuthBackends.OTHER]: BasicAuthStrategy,
}
