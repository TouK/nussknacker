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
    const style = 'espModal ' + (this.props.style || 'confirmationModal')
    return (
      <Modal isOpen={this.props.modalDialog.openDialog === this.props.type}
             shouldCloseOnOverlayClick={false}
             className={style} onRequestClose={this.closeDialog}>
        { this.props.header ? (<div className="modalHeader" style={{color: 'white', 'backgroundColor': '#70c6ce'}}>
          <span>{this.props.header}</span>
        </div>) : null }
        <div className="modalContent">
          {this.props.children}
          <div className="confirmationButtons">
            <button type="button" title="Cancel" className='modalButton' onClick={this.closeDialog}>Cancel</button>
            {
              this.props.confirm ?
              (<button type="button" title="OK" className='modalButton' onClick={() => this.props.confirm(this.closeDialog)}>OK</button>) : null
            }
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

