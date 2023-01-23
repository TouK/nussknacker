import React, {PropsWithChildren, useCallback, useEffect, useRef, useState} from "react"
import api from "../../api"
import LoaderSpinner from "../../components/Spinner"
import {AuthErrorCodes} from "./AuthErrorCodes"
import {Strategy} from "./Strategy"
import {InitErrorComponent, InitErrorComponentProps} from "./InitErrorComponent"

interface Props {
  onAuthFulfilled: () => Promise<void>,
  strategy: Strategy,
  errorComponent: React.ComponentType<InitErrorComponentProps>,
}

export function StrategyInitializer(props: PropsWithChildren<Props>): JSX.Element {
  const {children, onAuthFulfilled, strategy, errorComponent: ErrorHandleComponent} = props
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

  const retry = useCallback(
    () => error === AuthErrorCodes.ACCESS_TOKEN_CODE ? authenticate() : window.location.reload(),
    [authenticate, error]
  )

  return error ?
    <ErrorHandleComponent error={error} retry={retry}>
      <InitErrorComponent error={error} retry={retry}/>
    </ErrorHandleComponent> :
    initialized ? <>{children}</> : <LoaderSpinner show={!initialized}/>
}

