import React from "react"
import Notifications from 'react-notification-system-redux'
import InlinedSvgs from "../assets/icons/InlinedSvgs"

export function success(message) {
  return Notifications.show({
    message: message,
    level: 'success',
    children: (<div className="icon" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsInfo}}/>),
    autoDismiss: 5
  })
}

export function error(message, error, showErrorText) {
  const details = showErrorText && error ? (<div key="details" className="details">{error}</div>) : null
  return Notifications.show({
    message: message,
    level: 'error',
    autoDismiss: 10,
    children: [(<div className="icon" key="icon" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsWarning}}/>), details]
  })
}