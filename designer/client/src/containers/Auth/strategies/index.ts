import {BrowserAuthStrategy} from "./BrowserAuthStrategy"
import {OAuth2Strategy} from "./OAuth2Strategy"
import {RemoteAuthStrategy} from "./RemoteAuthStrategy";
import {StrategyConstructor} from "../Strategy"
import {AuthStrategy} from "../../../reducers/settings";

export const STRATEGIES: Partial<Record<AuthStrategy, StrategyConstructor>> = {
  [AuthStrategy.OAUTH2]: OAuth2Strategy,
  [AuthStrategy.BROWSER]: BrowserAuthStrategy,
  [AuthStrategy.REMOTE]: RemoteAuthStrategy,
}
