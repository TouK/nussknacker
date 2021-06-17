import React, {PropsWithChildren, useCallback, useEffect, useRef, useState} from "react"
import {useTranslation} from "react-i18next"
import api from "../../api"
import LoaderSpinner from "../../components/Spinner"
import {AuthErrorCodes} from "./AuthErrorCodes"
import InitializeError, {ErrorProps} from "../errors/InitializeError"
import {Strategy} from "./Strategy"

interface Props {
  onAuthFulfilled: () => Promise<void>,
  strategy: Strategy,
}

export function StrategyInitializer(props: PropsWithChildren<Props>): JSX.Element {
  const {children, onAuthFulfilled, strategy} = props
  const [error, setError] = useState<number>(null)
  const [initialized, setInitialized] = useState<boolean>(false)
  const boundInterceptor = useRef<number>(null)

  const initStrategy = useCallback((): Promise<unknown> => {
    if (strategy) {
      strategy.setOnErrorCallback?.(setError)

      api.interceptors.response.eject(boundInterceptor.current)
      boundInterceptor.current = api.interceptors.response.use(
        response => response,
        async error => {
          await strategy.inteceptor?.(error)
          return Promise.reject(error)
        },
      )

      return strategy.handleAuth()
    }
    return Promise.reject()
  }, [strategy])

  const authenticate = useCallback(() => {
    initStrategy()
      .then(onAuthFulfilled)
      .then(() => setInitialized(true))
      .catch(error => {
        const status = error?.response?.status
        setError(
          status && status !== AuthErrorCodes.HTTP_UNAUTHORIZED_CODE ? AuthErrorCodes.HTTP_APPLICATION_CODE : status,
        )
      })
  }, [initStrategy, onAuthFulfilled])

  useEffect(() => {
    authenticate()
  }, [authenticate])

  const {t} = useTranslation()

  const getErrorProps = useCallback((error: AuthErrorCodes): ErrorProps => {
    switch (error) {
      case AuthErrorCodes.HTTP_UNAUTHORIZED_CODE:
        return {
          message: t("auth.StrategyInitializer.errors.401.message", "Unauthorized Error"),
          description: t(
            "auth.StrategyInitializer.errors.401.description",
            "It seems you are not authenticated... Why not try to authenticate again?",
          ),
          buttonLabel: t("auth.StrategyInitializer.errors.401.buttonLabel", "Try authenticate again"),
        }
      case AuthErrorCodes.HTTP_TIMEOUT_CODE:
        return {
          message: t("auth.StrategyInitializer.errors.504.message", "504 Gateway Timeout Error"),
          description: t(
            "auth.StrategyInitializer.errors.504.description",
            "It seems server has some problems... Why not to try refreshing your page? Or you can contact with system administrators.",
          ),
        }
      case AuthErrorCodes.HTTP_APPLICATION_CODE:
        return {
          showButton: false,
        }
      case AuthErrorCodes.ACCESS_TOKEN_CODE:
        return {
          message: t("auth.StrategyInitializer.errors.accessToken.message", "Authentication Error"),
          buttonOnClick: () => {
            authenticate()
          },
          buttonLabel: t("auth.StrategyInitializer.errors.504.buttonLabel", "Go to authentication page"),
          description: t(
            "auth.StrategyInitializer.errors.1024.description",
            "It seems application has some problem with authentication. Please contact with system administrators.",
          ),
        }
    }
  }, [t, authenticate])

  return error ?
    <InitializeError {...getErrorProps(error)}/> :
    initialized ? <>{children}</> : <LoaderSpinner show={!initialized}/>
}
