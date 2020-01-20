/* eslint-disable i18next/no-literal-string */
import {v4 as uuid4} from "uuid"
import api from "../api"
import _ from "lodash"
import {AxiosRequestConfig} from "axios"

const AUTHORIZATION_HEADER_NAMESPACE = "Authorization"
const ACCESS_TOKEN_NAMESPACE = "accessToken"
const BEARER_CASE = "Bearer"
const userId = "userId"

class SystemUtils {

  public authorizationToken = (): string => `${BEARER_CASE} ${this.getAccessToken()}`

  public saveAccessToken = (token): void => localStorage.setItem(ACCESS_TOKEN_NAMESPACE, token)

  public getAccessToken = (): string => localStorage.getItem(ACCESS_TOKEN_NAMESPACE)

  public hasAccessToken = (): boolean => this.getAccessToken() !== null

  public removeAccessToken = () => localStorage.removeItem(ACCESS_TOKEN_NAMESPACE)

  public clearAuthorizationToken = (): void => {
    api.interceptors.request.use((config: AxiosRequestConfig) => {
      delete config.headers[this.getAuthorizationHeader()]
      return config
    })

    return this.removeAccessToken()
  }

  public setAuthorizationToken = (token): void => {
    api.interceptors.request.use((config: AxiosRequestConfig) => {
      _.set(config.headers, this.getAuthorizationHeader(), this.authorizationToken())
      return config
    })

    return this.saveAccessToken(token)
  }

  public getUserId = (): string => {
    if (_.isEmpty(localStorage.getItem(userId))) {
      localStorage.setItem(userId, uuid4())
    }
    return localStorage.getItem(userId)
  }

  public getAuthorizationHeader = (): string => AUTHORIZATION_HEADER_NAMESPACE
}

export default new SystemUtils()