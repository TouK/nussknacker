import React from "react";
import {render} from "react-dom";
import {Link} from "react-router";
import Modal from "react-modal";
import {DropdownButton, MenuItem} from "react-bootstrap";
import {connect} from "react-redux";
import _ from "lodash";
import ActionsUtils from "../actions/ActionsUtils";
import HttpService from "../http/HttpService";
import "../stylesheets/visualization.styl";

class SaveProcessDialog extends React.Component {

  constructor(props) {
    super(props);
    this.initState = {
      comment: ""
    }
    this.state = this.initState
  }

  confirm = () => {
    this.props.actions.saveProcess(this.props.processId, this.props.processToDisplay, this.state.comment).then((res) => {
      this.setState(this.initState)
    })
  }

  closeDialog = () => {
    this.setState(this.initState)
    this.props.actions.toggleSaveProcessDialog(false)
  }

  render() {
    return (
      <Modal isOpen={this.props.saveProcessDialog.isOpen} className="espModal confirmationModal" onRequestClose={this.closeDialog}>
        <div className="modalContent">
          <p>Save process {this.props.processId}</p>
          <textarea className="add-comment-on-save" placeholder="Write a comment..." value={this.state.comment} onChange={(e) => { this.setState({comment: e.target.value}) } } />
          <div className="confirmationButtons">
            <button type="button" title="Cancel" className='modalButton' onClick={this.closeDialog}>Cancel</button>
            <button type="button" title="OK" className='modalButton' onClick={this.confirm}>OK</button>
          </div>
        </div>
      </Modal>
    );
  }
}

function mapState(state) {
  return {
    saveProcessDialog: state.ui.saveProcessDialog || {},
    processId: _.get(state.graphReducer, 'fetchedProcessDetails.id'),
    processVersionId: _.get(state.graphReducer, 'fetchedProcessDetails.processVersionId'),
    processToDisplay: state.graphReducer.processToDisplay
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(SaveProcessDialog);

