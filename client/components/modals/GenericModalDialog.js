import React from "react";
import {render} from "react-dom";
import Modal from "react-modal";
import {connect} from "react-redux";
import _ from "lodash";
import ActionsUtils from "../../actions/ActionsUtils";
import "../../stylesheets/visualization.styl";

class GenericModalDialog extends React.Component {

  constructor(props) {
    super(props);
  }

  closeDialog = () => {
    this.props.init()
    this.props.actions.toggleModalDialog(null)
  }

  render() {
    return (
      <Modal isOpen={this.props.modalDialog.openDialog === this.props.type}
             shouldCloseOnOverlayClick={false}
             className="espModal confirmationModal" onRequestClose={this.closeDialog}>
        <div className="modalContent">
          {this.props.children}
          <div className="confirmationButtons">
            <button type="button" title="Cancel" className='modalButton' onClick={this.closeDialog}>Cancel</button>
            <button type="button" title="OK" className='modalButton' onClick={() => this.props.confirm(this.closeDialog)}>OK</button>
          </div>
        </div>
      </Modal>
    );
  }
}

function mapState(state) {
  return {
    modalDialog: state.ui.modalDialog || {},
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(GenericModalDialog);

