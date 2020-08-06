import PropTypes from "prop-types"
import React from "react"
import Modal from "react-modal"
import * as DialogMessages from "../../common/DialogMessages"
import {ButtonWithFocus} from "../withFocus"

class PreventExitDialog extends React.Component {

  static propTypes = {
    onCancel : PropTypes.func,
    onConfirm: PropTypes.func,
    visible: PropTypes.bool,
  }

  closeDialog = () => {
    this.props.onCancel()
  }

  confirm = () => {
    this.props.onConfirm()
    this.closeDialog()
  }

  render() {
    return (
      <Modal
        isOpen={this.props.visible}
        shouldCloseOnOverlayClick={false}
        onRequestClose={this.closeDialog}
      >
        <div className="draggable-container">
          <div className="espModal confirmationModal modalContentDark">
            <p>{DialogMessages.unsavedProcessChanges()}</p>
            <div className="confirmationButtons">
              <ButtonWithFocus type="button" title="NO" className="modalButton" onClick={this.closeDialog}>NO</ButtonWithFocus>
              <ButtonWithFocus type="button" title="DISCARD" className="modalButton" onClick={this.confirm}>DISCARD</ButtonWithFocus>
            </div>
          </div>
        </div>
      </Modal>
    )
  }

}

export default PreventExitDialog
