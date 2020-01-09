import React from "react";
import Modal from "react-modal";
import DialogMessages from "../../common/DialogMessages";
import PropTypes from "prop-types";
import Draggable from "react-draggable";
import {preventFromMoveSelectors} from "./GenericModalDialog";

class PreventExitDialog extends React.Component {

  static propTypes = {
    onCancel : PropTypes.func,
    onConfirm: PropTypes.func,
    visible: PropTypes.bool
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
      <Modal isOpen={this.props.visible}
             shouldCloseOnOverlayClick={false}
             onRequestClose={this.closeDialog}>
        <div className="draggable-container">
          <Draggable bounds="parent" cancel={preventFromMoveSelectors}>
            <div className="espModal confirmationModal modalContentDark">
              <p>{DialogMessages.unsavedProcessChanges()}</p>
              <div className="confirmationButtons">
                <button type="button" title="NO" className="modalButton" onClick={this.closeDialog}>NO</button>
                <button type="button" title="DISCARD" className="modalButton" onClick={this.confirm}>DISCARD</button>
              </div>
            </div>
          </Draggable>
        </div>
      </Modal>
    );
  }




}

export default PreventExitDialog