import React, {useCallback} from "react"
import {useTranslation} from "react-i18next"

export type ErrorProps = {
  message?: string,
  description?: string,
  buttonLabel?: string,
  showButton?: boolean,
  buttonOnClick?: () => void,
}

function InitializeError(props: ErrorProps): JSX.Element {
  const defaultOnClick = useCallback(() => window.location.reload(), [])
  const {t} = useTranslation()
  const title = t("InitializeError.title", "Oops!")
  const {
    message = t("InitializeError.message", "Application Unexpected Error"),
    description = t(
      "InitializeError.description",
      "An unexpected error seems to have occurred. Please contact with system administrators.",
    ),
    buttonLabel = t("InitializeError.buttonLabel", "Try to refresh page"),
    buttonOnClick = defaultOnClick,
    showButton = true,
  } = props
  return (
    <div className="error-template center-block">
      <h1>{title}</h1>
      <h2>{message}</h2>
      <div className="error-details">
        <br/>
        <p>{description}</p>
        {showButton ?
          (
            <div
              className="big-blue-button"
              role="button"
              style={{margin: "0 auto"}}
              onClick={buttonOnClick}
            >
              {buttonLabel}
            </div>
          ) :
          null
        }
      </div>
    </div>
  )
}

export default InitializeError
