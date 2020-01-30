import React from "react"
import {default as ReactNotifications} from "react-notification-system-redux"
import {connect} from "react-redux"
import ActionsUtils from "../actions/ActionsUtils"
import HttpService from "../http/HttpService"

class Notifications extends React.Component {

  componentDidMount() {
    HttpService.setNotificationActions(this.props.notificationActions)
  }

  render() {
    return <ReactNotifications notifications={this.props.notifications} style={false}/>
  }
}

const mapState = state => ({notifications: state.notifications})

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Notifications)
