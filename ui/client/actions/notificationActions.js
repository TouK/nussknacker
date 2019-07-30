import React from "react";
import InlinedSvgs from "../assets/icons/InlinedSvgs";
import Notifications from 'react-notification-system-redux';

export function info(message) {
  return Notifications.show({
    message: message,
    level: 'success',
    children: (<div className="icon" title="" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsInfo}}/>),
    autoDismiss: 5
  })
}

export function error(message, error, showErrorText) {
  const details = showErrorText && error ? (<div key="details" className="details">{error}</div>) : null
  return Notifications.show({
    message: message,
    level: 'error',
    autoDismiss: 10,
    children: [(<div className="icon" key="icon" title=""
                     dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsWarning}}/>), details]
  })
}