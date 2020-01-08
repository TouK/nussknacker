import React from "react";
import Modal from "react-modal";
import {connect} from "react-redux";
import ActionsUtils from "../../actions/ActionsUtils";
import "../../stylesheets/visualization.styl";
import LaddaButton from "react-ladda"
import "ladda/dist/ladda.min.css"
import PropTypes from 'prop-types';
import Draggable from "react-draggable";

class GenericModalDialog extends React.Component {

  constructor(props) {
    super(props);
    this.state = {
      pendingRequest: false
    };
  }

  closeDialog = () => {
    this.props.init()
    this.removePending()
    this.props.actions.toggleModalDialog(null)
  }

  removePending = () => {
    this.setState({pendingRequest: false})
  }

  onOk = () => {
    this.setState({pendingRequest: true})
    this.props.confirm(this.closeDialog)
      .then(this.closeDialog, this.removePending)
  }

  renderOkBtn = () => {
    return(
      <LaddaButton
        key="1"
        title="OK"
        className="modalButton modalConfirmButton"
        data-style="zoom-in"
        loading={this.state.pendingRequest}
        onClick={() => this.onOk()}
        {...this.props.okBtnConfig}
      >
        OK
      </LaddaButton>
    )
  }

  render() {
    const style = 'espModal ' + (this.props.style || 'confirmationModal')
    return (
      <Modal isOpen={this.props.modalDialog.openDialog === this.props.type}
             shouldCloseOnOverlayClick={false}
             onRequestClose={this.closeDialog}>
        <div className="draggable-container">
          <Draggable bounds="parent" cancel={preventFromMoveSelectors}>
            <div className={style}>
              {this.props.header ? (<div className="modal-title" style={{color: 'white', 'backgroundColor': '#70c6ce'}}>
                <span>{this.props.header}</span>
              </div>) : null}
              <div className="modalContentDark">
                {this.props.children}
                <div className="confirmationButtons">
                  <button type="button" title="CANCEL" className='modalButton' onClick={this.closeDialog}>CANCEL
                  </button>
                  {this.props.confirm ? this.renderOkBtn() : null}
                </div>
              </div>
            </div>
          </Draggable>
        </div>
      </Modal>
    );
  }
}

GenericModalDialog.propTypes = {
  okBtnConfig: PropTypes.object
}

function mapState(state) {
  return {
    modalDialog: state.ui.modalDialog || {},
  }
}

export const preventFromMoveSelectors = "input, textarea, #brace-editor, .datePickerContainer, svg, img, .node-value-select, #ace-editor, .row-ace-editor"

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(GenericModalDialog);

