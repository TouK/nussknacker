import React, {Component} from 'react';
import {render} from 'react-dom';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import classNames from 'classnames';
import Modal from 'react-modal';
import _ from 'lodash';
import LaddaButton from 'react-ladda';
import laddaCss from 'ladda/dist/ladda.min.css'
import ActionsUtils from '../../actions/ActionsUtils';
import { ListGroupItem } from 'react-bootstrap';
import NodeUtils from './NodeUtils';
import NodeDetailsContent from './NodeDetailsContent';
import EspModalStyles from '../../common/EspModalStyles'
import TestResultUtils from '../../common/TestResultUtils'

class NodeDetailsModal extends React.Component {

  static propTypes = {
    nodeToDisplay: React.PropTypes.object.isRequired,
    testResults: React.PropTypes.object,
    processId: React.PropTypes.string.isRequired,
    nodeErrors: React.PropTypes.array.isRequired,
    readOnly: React.PropTypes.bool.isRequired
  }

  constructor(props) {
    super(props);
    this.state = {
      isEditMode: false,
      pendingRequest: false
    };
  }

  componentWillReceiveProps(props) {
    this.setState({
      editedNode: props.nodeToDisplay,
      currentNodeId: props.nodeToDisplay.id
    })
  }

  componentDidUpdate(prevProps, prevState){
    if (!_.isEqual(prevProps.nodeToDisplay, this.props.nodeToDisplay)) {
      this.setState({editedNode: this.props.nodeToDisplay})
    }
  }

  closeModal = () => {
    this.setState({isEditMode: false})
    this.props.actions.closeNodeDetails()
  }

  toggleEdition = () => {
    this.setState({isEditMode: !this.state.isEditMode});
  }

  editNodeData = () => {
    this.toggleEdition();
  }

  performNodeEdit = () => {
    this.setState( { pendingRequest: true})
    this.props.actions.editNode(this.props.processToDisplay, this.props.nodeToDisplay, this.state.editedNode).then (() =>
      this.setState( { pendingRequest: false, isEditMode: false})
    )
  }

  nodeAttributes = () => {
    var nodeAttributes = require('json!../../assets/json/nodeAttributes.json');
    return nodeAttributes[NodeUtils.nodeType(this.props.nodeToDisplay)];
  }

  updateNodeState = (newNodeState) => {
    this.setState( { editedNode: newNodeState})
  }

  renderModalButtons() {
    const EditIcon = require('../../assets/img/edit-icon.svg');
    const SaveIcon = require('../../assets/img/save-icon.svg');
    const CloseIcon = require('../../assets/img/close-icon.svg');

    var buttonClasses = classNames('modalButton')
    var editButtonClasses = classNames(buttonClasses, 'pull-left', {'hidden': this.state.isEditMode})
    var saveButtonClasses = classNames(buttonClasses, 'pull-left', {'hidden': !this.state.isEditMode})

    if (!this.props.readOnly) {
      return ([
        <LaddaButton key="1" title="Save node details" className={saveButtonClasses}
                      loading={this.state.pendingRequest}
                      buttonStyle='zoom-in' onClick={this.performNodeEdit}><img src={SaveIcon}/></LaddaButton>,
        <button key="2" type="button" title="Edit node details" className={editButtonClasses} onClick={this.editNodeData}>
          <img src={EditIcon}/>
        </button>,
        <button key="3" type="button" title="Close node details" className={buttonClasses} onClick={this.closeModal}>
          <img src={CloseIcon}/>
        </button>
      ] );
    } else {
      return null;
    }
  }

  render() {
    var isOpen = !_.isEmpty(this.props.nodeToDisplay) && this.props.showNodeDetailsModal
    var headerStyles = EspModalStyles.headerStyles(this.nodeAttributes().styles.fill, this.nodeAttributes().styles.color)

    var testResults = TestResultUtils.resultsForNode(this.props.testResults, this.state.currentNodeId)
    return (
      <div className="objectModal">
        <Modal isOpen={isOpen} style={EspModalStyles.modalStyles()} onRequestClose={this.closeModal}>
          <div className="modalHeader" style={headerStyles}>
            <span>{NodeUtils.nodeType(this.props.nodeToDisplay)}</span>
            <div className="header-buttons">
              {this.renderModalButtons()}
            </div>
          </div>
          <div className="modalContent">
            <NodeDetailsContent isEditMode={this.state.isEditMode} node={this.state.editedNode}
                                nodeErrors={this.props.nodeErrors} onChange={this.updateNodeState} testResults={testResults}/>
          </div>
          <div className="modalFooter"></div>
        </Modal>
      </div>
    );
  }
}


function mapState(state) {
  return {
    nodeToDisplay: state.graphReducer.nodeToDisplay,
    processId: state.graphReducer.processToDisplay.id,
    nodeErrors: _.get(state.graphReducer.processToDisplay, `validationResult.invalidNodes[${state.graphReducer.nodeToDisplay.id}]`, []),
    processToDisplay: state.graphReducer.processToDisplay,
    readOnly: !state.settings.loggedUser.canWrite,
    showNodeDetailsModal: state.ui.showNodeDetailsModal,
    testResults: state.graphReducer.testResults
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(NodeDetailsModal);
