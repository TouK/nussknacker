import PropTypes from "prop-types"
import React from "react"
import Dotdotdot from "react-dotdotdot"
import InlinedSvgs from "../../assets/icons/InlinedSvgs"
import HeaderIcon from "../tips/HeaderIcon"

export default function Notification(props) {
  const {icon, message, details} = props

  return (
    <div className={"notification"}>
      <div className="icon" dangerouslySetInnerHTML={{__html: icon}}/>
      <div className={"notification-details"}>
        {!_.isEmpty(message) && <span className={"notification-text"}>{message}</span>}
        {!_.isEmpty(details) &&
        <Dotdotdot clamp={"380px"}>
          <span className={"notification-text"}>{details}</span>
        </Dotdotdot>}
      </div>
      <HeaderIcon className={"dismiss-icon"} icon={InlinedSvgs.tipsClose}/>
    </div>
  )
}

Notification.propTypes = {
  icon: PropTypes.string.isRequired,
  message: PropTypes.string,
  details: PropTypes.string,
}
