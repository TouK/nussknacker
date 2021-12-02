import React, {useMemo} from "react"
import {useTranslation} from "react-i18next"
import {AuthErrorCodes} from "../Auth/AuthErrorCodes"
import {InitErrorComponentProps} from "../Auth/InitErrorComponent"

interface ErrorProps {
  message: string,
  description: string,
  button?: string,
}

export function InitializeError({error, retry}: InitErrorComponentProps): JSX.Element {
  const {t} = useTranslation()
  const errorProps = useMemo<ErrorProps>(() => {
    switch (error) {
      case AuthErrorCodes.HTTP_UNAUTHORIZED_CODE:
        return {
          message: t("auth.StrategyInitializer.errors.401.message", "Unauthorized Error"),
          description: t(
            "auth.StrategyInitializer.errors.401.description",
            "It seems you are not authenticated... Why not try to authenticate again?",
          ),
          button: t("auth.StrategyInitializer.errors.401.buttonLabel", "Try authenticate again"),
        }
      case AuthErrorCodes.HTTP_TIMEOUT_CODE:
        return {
          message: t("auth.StrategyInitializer.errors.504.message", "504 Gateway Timeout Error"),
          description: t(
            "auth.StrategyInitializer.errors.504.description",
            "It seems server has some problems... Why not to try refreshing your page? Or you can contact with system administrators.",
          ),
          button: t("InitializeError.buttonLabel", "Try to refresh page"),
        }
      case AuthErrorCodes.HTTP_APPLICATION_CODE:
        return {
          message: t("InitializeError.message", "Application Unexpected Error"),
          description: t(
            "InitializeError.description",
            "An unexpected error seems to have occurred. Please contact with system administrators.",
          ),
        }
      case AuthErrorCodes.ACCESS_TOKEN_CODE:
        return {
          message: t("auth.StrategyInitializer.errors.accessToken.message", "Authentication Error"),
          description: t(
            "auth.StrategyInitializer.errors.1024.description",
            "It seems application has some problem with authentication. Please contact with system administrators.",
          ),
          button: t("auth.StrategyInitializer.errors.1024.buttonLabel", "Go to authentication page"),
        }
    }
  }, [error, t])

  return (
    <div className="error-template center-block">
      <h1>{t("InitializeError.title", "Oops!")}</h1>
      <h2>{errorProps.message}</h2>
      <div className="error-details">
        <p>{errorProps.description}</p>
        {errorProps.button && (
          <button
            className="big-blue-button"
            style={{border: "none"}}
            onClick={() => retry()}
          >
            {errorProps.button}
          </button>
        )}
      </div>
    </div>
  )
}

export default InitializeError
