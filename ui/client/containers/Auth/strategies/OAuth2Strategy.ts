import {AxiosResponse} from "axios"
import * as jwt from "jsonwebtoken"
import {nanoid} from "nanoid"
import * as queryString from "query-string"
import SystemUtils from "../../../common/SystemUtils"
import HttpService from "../../../http/HttpService"
import {OAuth2Settings} from "../../../reducers/settings"
import {AuthErrorCodes} from "../AuthErrorCodes"
import {Strategy, StrategyConstructor} from "../Strategy"
import {BASE_PATH} from "../../../config";

export const OAuth2Strategy: StrategyConstructor = class OAuth2Strategy implements Strategy {
  private onError?: (error: AuthErrorCodes) => void
  private readonly redirectUri: string | null // null means it's been configured in the BE and it's not evaluated here
  private readonly authorizeUrl: URL

  constructor(
    private settings: OAuth2Settings,
    onError?: (error: AuthErrorCodes) => void,
  ) {
    this.onError = onError
    this.authorizeUrl = new URL(this.settings.authorizeUrl)
    if (this.authorizeUrl.searchParams.has("redirect_uri")) {
      this.redirectUri = null
    } else {
      this.redirectUri = window.location.origin + BASE_PATH
      this.authorizeUrl.searchParams.append("redirect_uri", this.redirectUri)
    }
  }

  redirectToAuthorizeUrl(): void {
    SystemUtils.saveNonce(nanoid())
    window.location.replace(`${this.authorizeUrl.toString()}&nonce=${SystemUtils.getNonce()}`)
  }

  inteceptor(error?: {response?: {status?: AuthErrorCodes}}): Promise<unknown> {
    if (error?.response?.status === AuthErrorCodes.HTTP_UNAUTHORIZED_CODE) {
      this.redirectToAuthorizeUrl()
    }
    return Promise.resolve()
  }

  handleAuth(): Promise<AxiosResponse<unknown> | void> {
    const {hash, search} = window.location
    const queryParams = queryString.parse(search)
    if (queryParams.code) {
      return HttpService.fetchOAuth2AccessToken<{accessToken: string}>(queryParams.code, this.redirectUri)
        .then(response => {
          SystemUtils.setAuthorizationToken(response.data.accessToken)
          return response
        })
        .catch(error => {
          this.onError(AuthErrorCodes.ACCESS_TOKEN_CODE)
          return Promise.reject(error)
        })
        .finally(() => {
          history.replaceState(null, "", "?")
        })
    }

    const queryHashParams = queryString.parse(hash)
    if (queryHashParams.access_token && this.settings.implicitGrantEnabled) {
      if (!this.verifyTokens(queryHashParams)) {
        return Promise.reject("token not verified")
      } else {
        SystemUtils.setAuthorizationToken(queryHashParams.access_token)
        history.replaceState(null, "", "#")
      }
    }

    if (SystemUtils.hasAccessToken()) {
      return Promise.resolve()
    }

    this.redirectToAuthorizeUrl()
    return Promise.reject()
  }

  setOnErrorCallback(callback: (error: AuthErrorCodes) => void): void {
    this.onError = callback
  }

  private verifyTokens(queryHashParams): boolean {
    if (this.settings.jwtAuthServerPublicKey) {
      const verifyAccessToken = () => {
        try {
          return jwt.verify(queryHashParams.access_token, this.settings.jwtAuthServerPublicKey) !== null
        } catch (error) {
          this.handleJwtError(error)
          return false
        }
      }
      const accessTokenVerificationResult = verifyAccessToken()
      if (accessTokenVerificationResult) {
        if (queryHashParams.id_token) {
          try {
            return jwt.verify(queryHashParams.id_token, this.settings.jwtAuthServerPublicKey, {nonce: SystemUtils.getNonce()}) !== null
          } catch (error) {
            this.handleJwtError(error)
            return false
          }
        } else if (this.settings.jwtIdTokenNonceVerificationRequired === true) {
          // eslint-disable-next-line i18next/no-literal-string
          console.warn("jwt.idTokenNonceVerificationRequired=true but id_token missing in the auth server response")
          this.onError(AuthErrorCodes.ACCESS_TOKEN_CODE)
          return false
        }
      }
      return accessTokenVerificationResult
    } else {
      return true
    }
  }

  private handleJwtError(error: jwt.JsonWebTokenError) {
    console.warn(error)
    if (error.name === "TokenExpiredError") {
      this.redirectToAuthorizeUrl()
    } else {
      this.onError(AuthErrorCodes.ACCESS_TOKEN_CODE)
      return Promise.reject()
    }
  }

}
