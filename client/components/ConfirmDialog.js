import React from "react";
import {render} from "react-dom";
import {Link} from "react-router";
import Modal from "react-modal";
import {DropdownButton, MenuItem} from "react-bootstrap";
import {connect} from "react-redux";
import ActionsUtils from "../actions/ActionsUtils";
import EspModalStyles from '../common/EspModalStyles'
import ProcessUtils from '../common/ProcessUtils';
import "../stylesheets/visualization.styl";
import SaveIcon from '../assets/img/save-icon.svg';
import CloseIcon from '../assets/img/close-icon.svg';

class ConfirmDialog extends React.Component {

  componentDidMount = () => {
    //gdzie to powinno byc?
    window.onbeforeunload = (e) => {
      if (!this.props.nothingToSave) {
        return "" // powoduje przegladarkowy alert przy reloadzie i zamknieciu taba, z defaultowa wiadomoscia, ktorej nie da sie zmienic
      }
    }
  }

  closeDialog = () => {
    this.props.actions.toggleConfirmDialog(false)
  }

  confirm = () => {
    this.props.confirmDialog.onConfirmCallback()
    this.closeDialog()
  }

  render() {
    return (
      <Modal isOpen={this.props.confirmDialog.isOpen} className="espModal confirmationModal" onRequestClose={this.closeDialog}>
        <div className="modalContent">
          <p>{this.props.confirmDialog.text}</p>
          <div className="confirmationButtons">
            <button type="button" title="Cancel" className='modalButton' onClick={this.closeDialog}>No</button>
            <button type="button" title="Yes" className='modalButton' onClick={this.confirm}>Yes</button>
          </div>
        </div>
      </Modal>
    );
  }
}

function mapState(state) {
  return {
    confirmDialog: state.ui.confirmDialog,
    nothingToSave: ProcessUtils.nothingToSave(state)
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ConfirmDialog);

