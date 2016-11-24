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
    const headerStyles = EspModalStyles.headerStyles("#a94442", "white")
    const dialogStyles = {content: {height: "30%"}}
    return (
      <Modal isOpen={this.props.confirmDialog.isOpen} style={EspModalStyles.modalStyles(dialogStyles)} onRequestClose={this.closeDialog}>
        <div className="modalHeader" style={headerStyles}>
          <span>Are you sure?</span>
          <div className="header-buttons">
            <button type="button" title="Accept" className='modalButton' onClick={this.confirm}>
              <img src={SaveIcon}/>
            </button>
            <button type="button" title="Cancel" className='modalButton' onClick={this.closeDialog}>
              <img src={CloseIcon}/>
            </button>
          </div>
        </div>
        <div className="modalContent">
          <p>{this.props.confirmDialog.text}</p>
        </div>
        <div className="modalFooter"></div>
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

