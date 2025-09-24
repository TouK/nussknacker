import { AuthStrategy } from "../../../reducers/settings";
import type { StrategyConstructor } from "../Strategy";
import { BrowserAuthStrategy } from "./BrowserAuthStrategy";
import { OAuth2Strategy } from "./OAuth2Strategy";
import { RemoteAuthStrategy } from "./RemoteAuthStrategy/RemoteAuthStrategy";

export const STRATEGIES: Partial<Record<AuthStrategy, StrategyConstructor>> = {
    [AuthStrategy.OAUTH2]: OAuth2Strategy,
    [AuthStrategy.BROWSER]: BrowserAuthStrategy,
    [AuthStrategy.REMOTE]: RemoteAuthStrategy,
};
