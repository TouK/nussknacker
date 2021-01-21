import {PropsWithChildren} from "react"
import {AuthenticationSettings} from "../../reducers/settings"
import {AuthErrorCodes} from "./AuthErrorCodes"

export interface StrategyConstructor {
  new(
    settings: AuthenticationSettings,
    onError?: (error: AuthErrorCodes) => void,
  ): Strategy,
}

export interface Strategy {
  Wrapper?: (props: PropsWithChildren<unknown>) => JSX.Element,

  inteceptor?<E>(error: E): Promise<unknown>,

  handleAuth(): Promise<unknown>,

  setOnErrorCallback?(callback: (error: AuthErrorCodes) => void): void,
}
