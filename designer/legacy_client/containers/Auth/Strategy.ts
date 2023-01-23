import {ComponentType} from "react"
import {AuthenticationSettings} from "../../reducers/settings"
import {AuthErrorCodes} from "./AuthErrorCodes"

export interface StrategyConstructor {
  new(
    settings: AuthenticationSettings,
    onError?: (error: AuthErrorCodes) => void,
  ): Strategy,
}

export interface Strategy {
  // add when some React work is needed for strategy init
  Wrapper?: ComponentType,

  // intercept axios errors (to handle 401)
  inteceptor?<E>(error: E): Promise<unknown>,

  // init strategy and try to authorize (show login)
  handleAuth(): Promise<unknown>,

  setOnErrorCallback?(callback: (error: AuthErrorCodes) => void): void,
}
