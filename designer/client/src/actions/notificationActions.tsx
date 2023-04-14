import React from "react"
import Notifications from "react-notification-system-redux"
import {v4 as uuid4} from "uuid"
import {ReactComponent as TipsError} from "../assets/img/icons/tipsError.svg"
import {ReactComponent as TipsInfo} from "../assets/img/icons/tipsInfo.svg"
import {ReactComponent as TipsSuccess} from "../assets/img/icons/tipsWarning.svg"
import Notification from "../components/notifications/Notification"
import {Action} from "./reduxTypes"

export function success(message: string): Action {
  return Notifications.success({
    autoDismiss: 10,
    children: [
      <Notification icon={<TipsSuccess/>} message={message} key={uuid4()}/>,
    ],
  })
}

export function error(message: string, error?: string, showErrorText?: boolean): Action {
  const details = showErrorText && error ? error : null
  return Notifications.error({
    autoDismiss: 10,
    children: [
      <Notification icon={<TipsError/>} message={message} details={details} key={uuid4()}/>,
    ],
  })
}

export function info(message: string): Action {
  return Notifications.info({
    autoDismiss: 10,
    children: [
      <Notification icon={<TipsInfo/>} message={message} key={uuid4()}/>,
    ],
  })
}
