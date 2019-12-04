import React from 'react'
import Dotdotdot from "react-dotdotdot";

export default class Notification extends React.Component {

  render() {
    const {icon, message, details} = this.props
    return (
      <div className={"notification"}>
        <div className="icon" dangerouslySetInnerHTML={{__html: icon}}/>
        <div className={"notification-details"}>
          {!_.isEmpty(message) && <span className={"notification-text"}>{message}</span>}
          {!_.isEmpty(details) && <Dotdotdot clamp={"380px"}>
                                    <span className={"notification-text"}>{details}</span>
                                  </Dotdotdot>}
        </div>
      </div>
    )
  }
}