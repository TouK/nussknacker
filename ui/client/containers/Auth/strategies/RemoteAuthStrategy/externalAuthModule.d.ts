import {ComponentType, PropsWithChildren} from "react"

interface AuthRedirectState {
  targetPath: string,
  action: string,
}

interface RedirectCallback<S extends AuthRedirectState = AuthRedirectState> {
  (state?: S): void,
}

interface AuthProviderConfig {
  audience: string,
  domain: string,
  client_id: string,
}

interface Props {
  config: AuthProviderConfig,
  onRedirect?: RedirectCallback,
  resolve?: <U>(auth: AuthClient<U>) => void,
}

interface PropsWithDefaults {
  withDefaults: true,
  config?: Partial<AuthProviderConfig>,
  onRedirect?: RedirectCallback,
  resolve?: <U>(auth: AuthClient<U>) => void,
}

type AuthProps = PropsWithChildren<Props | PropsWithDefaults>

export interface AuthClient<U = any> {
  user: U,
  isLoading: boolean,
  isAuthenticated: boolean,
  login: (options?: any) => void,
  loginWithPopup: (params?: any) => Promise<void>,
  logout: (options?: any) => void,
  getToken: () => Promise<string>,
}

export interface ExternalAuthModule {
  default: ComponentType<AuthProps>,
  useAuth: () => AuthClient,
  useLogin: () => AuthClient,
}
