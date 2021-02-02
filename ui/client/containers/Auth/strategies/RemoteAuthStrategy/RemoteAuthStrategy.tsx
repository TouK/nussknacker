import React, {FunctionComponent, PropsWithChildren} from "react"
import ErrorBoundary from "react-error-boundary"
import {PendingPromise} from "../../../../common/PendingPromise"
import SystemUtils from "../../../../common/SystemUtils"
import {AuthenticationSettings} from "../../../../reducers/settings"
import {ExternalModule, splitUrl, useExternalLib} from "../../../ExternalLib"
import {ModuleString, ModuleUrl} from "../../../ExternalLib/types"
import {AuthErrorCodes} from "../../AuthErrorCodes"
import {Strategy, StrategyConstructor} from "../../Strategy"
import {AuthClient, ExternalAuthModule} from "./externalAuthModule"

type AuthLibCallback = (a: AuthClient) => void

function AuthProvider(props: PropsWithChildren<{scope: ModuleString, onInit: AuthLibCallback}>) {
  const {scope, onInit, children} = props
  const {module: {default: Component}} = useExternalLib<ExternalAuthModule>(scope)
  return (
    <Component withDefaults onInit={onInit}>
      {children}
    </Component>
  )
}

function createAuthWrapper({url, scope}: {url: ModuleUrl, scope: ModuleString}, onInit: AuthLibCallback): FunctionComponent {
  return function Wrapper({children}) {
    return (
      <ExternalModule url={url}>
        <ErrorBoundary>
          <AuthProvider scope={scope} onInit={onInit}>
            {children}
          </AuthProvider>
        </ErrorBoundary>
      </ExternalModule>
    )
  }
}

export const RemoteAuthStrategy: StrategyConstructor = class RemoteAuthStrategy implements Strategy {
  private pendingClient = new PendingPromise<AuthClient>()
  Wrapper = createAuthWrapper(this.urlWithScope, auth => this.pendingClient.resolve(auth))

  async inteceptor(error?: {response?: {status?: AuthErrorCodes}}): Promise<unknown> {
    if (error?.response?.status === AuthErrorCodes.HTTP_UNAUTHORIZED_CODE) {
      await this.handleAuth()
    }
    return
  }

  async handleAuth(): Promise<void> {
    const auth = await this.pendingClient.promise
    if (!auth.isAuthenticated) {
      auth.login()
    }
    const token = await auth.getToken()
    SystemUtils.setAuthorizationToken(token)
  }

  setOnErrorCallback(callback: (error: AuthErrorCodes) => void): void {
    this.onError = callback
  }

  constructor(private settings: AuthenticationSettings) {}

  private get urlWithScope(): {scope: ModuleString, url: ModuleUrl} {
    const [url, scope] = splitUrl(this.settings.authorizeUrl as ModuleUrl)
    return {url, scope}
  }

  private onError?: (error: AuthErrorCodes) => void = () => {return}
}
