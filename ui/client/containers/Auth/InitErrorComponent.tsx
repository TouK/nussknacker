import {AuthErrorCodes} from "./AuthErrorCodes"
import React from "react"
import {I18nextProvider} from "react-i18next"
import InitializeError from "../errors/InitializeError"
import i18n from "../../i18n"

export interface InitErrorComponentProps {
  error: AuthErrorCodes,
  retry: () => void,
}

export function InitErrorComponent(props: InitErrorComponentProps): JSX.Element {
  return (
    <I18nextProvider i18n={i18n}>
      <InitializeError {...props}/>
    </I18nextProvider>
  )
}

