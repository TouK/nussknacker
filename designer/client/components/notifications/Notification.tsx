import {isEmpty} from "lodash"
import React from "react"
import Dotdotdot from "react-dotdotdot"
import InlinedSvgs from "../../assets/icons/InlinedSvgs"
import HeaderIcon from "../tips/HeaderIcon"
import classnames from "./notifications.styl"

interface Props {
  icon: string,
  message?: string,
  details?: string,
}

export default function Notification({icon, message, details}: Props): JSX.Element {
  return (
    <div className={classnames.notification}>
      <div className="icon" dangerouslySetInnerHTML={{__html: icon}}/>
      <div className={classnames.notificationDetails}>
        {!isEmpty(message) && <span className={classnames.notificationText}>{message}</span>}
        {!isEmpty(details) && (
          <Dotdotdot clamp={`380px`}>
            <span className={classnames.notificationText}>{details}</span>
          </Dotdotdot>
        )}
      </div>
      <HeaderIcon className={classnames.dismissIcon} icon={InlinedSvgs.tipsClose}/>
    </div>
  )
}
