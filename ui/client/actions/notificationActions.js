import React from "react"
import Notifications from 'react-notification-system-redux'
import InlinedSvgs from "../assets/icons/InlinedSvgs"
import Notification from "../components/notifications/Notification";

export function success(message) {
  return Notifications.success({
    autoDismiss: 10000,
    children: ([
      <Notification icon={InlinedSvgs.tipsSuccess} message={message}/>
    ]),
  })
}

export function error(message, error, showErrorText) {
  const details = showErrorText && error ? error : null
  return Notifications.error({
    autoDismiss: 10000,
    children: ([
      <Notification icon={InlinedSvgs.tipsError} message={message} details={details}/>
    ])
  })
}