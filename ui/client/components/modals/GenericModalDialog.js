import React from "react";
import {render} from "react-dom";
import Modal from "react-modal";
import {connect} from "react-redux";
import ActionsUtils from "../../actions/ActionsUtils";
import "../../stylesheets/visualization.styl";
import LaddaButton from 'react-ladda';
import PropTypes from 'prop-types';


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
      <LaddaButton key="1" title="OK" className="modalButton modalConfirmButton" buttonStyle="zoom-in"
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
             className={style} onRequestClose={this.closeDialog}>
        { this.props.header ? (<div className="modalHeader" style={{color: 'white', 'backgroundColor': '#70c6ce'}}>
          <span>{this.props.header}</span>
        </div>) : null }
        <div className="modalContentDark">
          {this.props.children}
          <div className="confirmationButtons">
            <button type="button" title="Cancel" className='modalButton' onClick={this.closeDialog}>Cancel</button>
            { this.props.confirm ? this.renderOkBtn() : null }
          </div>
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

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(GenericModalDialog);

