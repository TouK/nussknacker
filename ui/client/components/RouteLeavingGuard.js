import React from "react"
import {Prompt} from "react-router"
import PreventExitDialog from "./modals/PreventExitDialog"

export class RouteLeavingGuard extends React.Component {

  state = {
    modalVisible: false,
    lastLocation: null,
    confirmedNavigation: false,
  }

  showModal = (location) => this.setState({
    modalVisible: true,
    lastLocation: location,
  })

  closeModal = (callback) => this.setState({
    modalVisible: false,
  }, callback)

  handleBlockedNavigation = (nextLocation, action) => {
    const {confirmedNavigation} = this.state
    if (!confirmedNavigation && action === "PUSH") {
      this.showModal(nextLocation)
      return false
    }

    return true
  }

  handleConfirmNavigationClick = () => this.closeModal(() => {
    const {navigate} = this.props
    const {lastLocation} = this.state
    if (lastLocation) {
      this.setState({
        confirmedNavigation: true,
      }, () => {
        // Navigate to the previous blocked location with your navigate function
        navigate(lastLocation.pathname)
      })
    }
  })

  render() {
    const {when} = this.props
    const {modalVisible, lastLocation} = this.state
    return (
      <>
        <Prompt
          when={when}
          message={(location, action) => this.handleBlockedNavigation(location, action)}
        />
        <PreventExitDialog
          visible={modalVisible}
          onCancel={this.closeModal}
          onConfirm={this.handleConfirmNavigationClick}
        />
      </>
    )
  }
}

export default RouteLeavingGuard
