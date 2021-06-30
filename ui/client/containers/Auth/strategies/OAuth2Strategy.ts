import {AxiosResponse} from "axios"
import * as jwt from "jsonwebtoken"
import {nanoid} from "nanoid"
import * as queryString from "query-string"
import SystemUtils from "../../../common/SystemUtils"
import HttpService from "../../../http/HttpService"
import {OAuth2Settings} from "../../../reducers/settings"
import {AuthErrorCodes} from "../AuthErrorCodes"
import {Strategy, StrategyConstructor} from "../Strategy"

export const OAuth2Strategy: StrategyConstructor = class OAuth2Strategy implements Strategy {
  private onError?: (error: AuthErrorCodes) => void

  constructor(
    private settings: OAuth2Settings,
    onError?: (error: AuthErrorCodes) => void,
  ) { this.onError = onError }

  redirectToAuthorizeUrl(authorizeUrl: string): void {
    SystemUtils.saveNonce(nanoid())
    window.location.replace(`${authorizeUrl}&nonce=${SystemUtils.getNonce()}`)
  }

  inteceptor(error?: {response?: {status?: AuthErrorCodes}}): Promise<unknown> {
    if (error?.response?.status === AuthErrorCodes.HTTP_UNAUTHORIZED_CODE) {
      this.redirectToAuthorizeUrl(this.settings.authorizeUrl)
    }
    return Promise.resolve()
  }

  handleAuth(): Promise<AxiosResponse<unknown> | void> {
    const {hash, search} = window.location
    const queryParams = queryString.parse(search)
    if (queryParams.code) {
      return HttpService.fetchOAuth2AccessToken<{accessToken: string}>(queryParams.code)
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
      if (!this.verifyTokens(this.settings, queryHashParams)) {
        return Promise.reject("token not verified")
      } else {
        SystemUtils.setAuthorizationToken(queryHashParams.access_token)
        history.replaceState(null, "", "#")
      }
    }

    if (SystemUtils.hasAccessToken()) {
      return Promise.resolve()
    }

    this.redirectToAuthorizeUrl(this.settings.authorizeUrl)
    return Promise.reject()
  }

  setOnErrorCallback(callback: (error: AuthErrorCodes) => void): void {
    this.onError = callback
  }

  private verifyTokens(settings: OAuth2Settings, queryHashParams): boolean {
    if (settings.jwtAuthServerPublicKey) {
      const verifyAccessToken = () => {
        try {
          return jwt.verify(queryHashParams.access_token, settings.jwtAuthServerPublicKey) !== null
        } catch (error) {
          this.handleJwtError(error, settings)
          return false
        }
      }
      const accessTokenVerificationResult = verifyAccessToken()
      if (accessTokenVerificationResult) {
        if (queryHashParams.id_token) {
          try {
            return jwt.verify(queryHashParams.id_token, settings.jwtAuthServerPublicKey, {nonce: SystemUtils.getNonce()}) !== null
          } catch (error) {
            this.handleJwtError(error, settings)
            return false
          }
        } else if (settings.jwtIdTokenNonceVerificationRequired === true) {
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

  private handleJwtError(error: jwt.JsonWebTokenError, settings: OAuth2Settings) {
    console.warn(error)
    if (error.name === "TokenExpiredError") {
      this.redirectToAuthorizeUrl(settings.authorizeUrl)
    } else {
      this.onError(AuthErrorCodes.ACCESS_TOKEN_CODE)
      return Promise.reject()
    }
  }

}
