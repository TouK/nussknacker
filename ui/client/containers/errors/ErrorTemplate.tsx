import React, {PropsWithChildren} from "react"
import {useTranslation} from "react-i18next"

export interface ErrorTemplateProps {
  description?: string,
  message?: string,
}

export function ErrorTemplate({
  description = "",
  message = "",
  children,
}: PropsWithChildren<ErrorTemplateProps>): JSX.Element {
  const {t} = useTranslation()

  return (
    <div className="error-template center-block">
      <h1>{t("error.title", "Oops!")}</h1>
      <h2>{message}</h2>
      <div className="error-details">
        <p>{description}</p>
        {children}
      </div>
    </div>
  )
}
