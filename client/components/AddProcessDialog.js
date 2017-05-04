import React from "react";
import {render} from "react-dom";
import {browserHistory} from "react-router";
import Modal from "react-modal";
import {connect} from "react-redux";
import _ from 'lodash';
import ActionsUtils from "../actions/ActionsUtils";
import EspModalStyles from "../common/EspModalStyles";
import "../stylesheets/visualization.styl";
import SaveIcon from "../assets/img/save-icon.svg";
import CloseIcon from "../assets/img/close-icon.svg";
import HttpService from "../http/HttpService";

//TODO: uwspolnic z modals
class AddProcessDialog extends React.Component {

  static propTypes = {
    categories: React.PropTypes.array.isRequired,
    isOpen: React.PropTypes.bool.isRequired,
    onClose: React.PropTypes.func.isRequired
  }

  initialState(props) {
    return {processId: '', processCategory: _.head(props.categories) || ''}
  }

  constructor(props) {
    super(props)
    this.state = this.initialState(props)
  }

  componentWillReceiveProps(props) {
    this.setState(this.initialState(props))
  }

  closeDialog = () => {
    this.props.onClose()
  }

  confirm = () => {
    var processId = this.state.processId
    HttpService.createProcess(this.state.processId, this.state.processCategory, () => {
      this.closeDialog()
      browserHistory.push('/visualization/' + processId)
    })
  }

  render() {
    const headerStyles = EspModalStyles.headerStyles("#2d8e54", "white")
    return (
      <Modal isOpen={this.props.isOpen} className="espModal" shouldCloseOnOverlayClick={false} onRequestClose={this.closeDialog}>
        <div className="modalHeader" style={headerStyles}>
          <span>Create new process</span>
        </div>
        <div className="modalContent">
          <div className="node-table">
            <div className="node-table-body">
              <div className="node-row">
                <div className="node-label">Process id</div>
                <div className="node-value"><input type="text" id="newProcessId" className="node-input" value={this.state.processId}
                                                   onChange={(e) => this.setState({processId: e.target.value})}/></div>
              </div>
              <div className="node-row">
                <div className="node-label">Process category</div>
                <div className="node-value">
                  <select id="processCategory" className="node-input"  onChange={(e) => this.setState({processCategory: e.target.value})}>
                    {this.props.categories.map((cat, index) => (<option key={index} value={cat}>{cat}</option>))}
                  </select>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div className="modalFooter">
          <div className="footerButtons">
            <button type="button" title="Cancel" className='modalButton' onClick={this.closeDialog}>Cancel</button>
            <button type="button" title="Create" className='modalButton' onClick={this.confirm}>Create</button>
          </div>
        </div>
      </Modal>
    );
  }
}

function mapState(state) {
  return {
    categories: state.settings.loggedUser.categories || []
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(AddProcessDialog);

