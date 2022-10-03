import React from "react"
import {ErrorTemplate} from "./ErrorTemplate"
import {useTranslation} from "react-i18next"

export function ServerError(): JSX.Element {
  const {t} = useTranslation()

  const message = t(
    "error.ServerError.defaultMessage",
    "Internal Server Error"
  )
  const description = t(
    "error.ServerError.defaultDescription",
    "An unexpected error seems to have occurred. Please contact with system administrators."
  )

  return (
    <ErrorTemplate message={message} description={description}/>
  )
}
