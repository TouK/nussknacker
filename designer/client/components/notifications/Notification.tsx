import {isEmpty} from "lodash"
import React, {ReactElement} from "react"
import Dotdotdot from "react-dotdotdot"
import {ReactComponent as TipsClose} from "../../assets/img/icons/tipsClose.svg"
import classnames from "./notifications.styl"

interface Props {
  icon: ReactElement,
  message?: string,
  details?: string,
}

export default function Notification({icon, message, details}: Props): JSX.Element {
  return (
    <div className={classnames.notification}>
      <div className="icon">{icon}</div>
      <div className={classnames.notificationDetails}>
        {!isEmpty(message) && <span className={classnames.notificationText}>{message}</span>}
        {!isEmpty(details) && (
          <Dotdotdot clamp={`380px`}>
            <span className={classnames.notificationText}>{details}</span>
          </Dotdotdot>
        )}
      </div>
      <TipsClose className={classnames.dismissIcon}/>
    </div>
  )
}
