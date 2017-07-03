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
import {Scrollbars} from "react-custom-scrollbars";
import cssVariables from "../../stylesheets/_variables.styl"

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
    this.props.actions.closeModals()
  }

  performNodeEdit = () => {
    if (this.isGroup()) {
      //FIXME: a czy tutaj nie chcemy walidowac???
      this.props.actions.editGroup(this.props.nodeToDisplay.id, this.state.editedNode)
      this.closeModal()
    } else {
      this.setState( { pendingRequest: true})
      this.props.actions.editNode(this.props.processToDisplay, this.props.nodeToDisplay, this.state.editedNode).then (() => {
          this.setState( { pendingRequest: false})
          this.closeModal()
        }, () => this.setState( { pendingRequest: false})
      )
    }
  }

  nodeAttributes = () => {
    var nodeAttributes = require('json!../../assets/json/nodeAttributes.json');
    return nodeAttributes[NodeUtils.nodeType(this.props.nodeToDisplay)];
  }

  updateNodeState = (newNodeState) => {
    this.setState( { editedNode: newNodeState})
  }

  renderModalButtons() {
    return ([
      this.isGroup() ? this.renderGroupUngroup() : null,
      !this.props.readOnly ? <LaddaButton key="1" title="Save node details" className='modalButton pull-right modalConfirmButton'
                    loading={this.state.pendingRequest}
                    buttonStyle='zoom-in' onClick={this.performNodeEdit}>Save</LaddaButton>: null,
      <button key="2" type="button" title="Close node details" className='modalButton' onClick={this.closeModal}>
        Close
      </button>
    ] );
  }

  renderGroupUngroup() {
    const expand = () => { this.props.actions.expandGroup(id); this.closeModal() }
    const collapse = () => { this.props.actions.collapseGroup(id); this.closeModal() }

    const id = this.state.editedNode.id
    const expanded = _.includes(this.props.expandedGroups, id)
    return  expanded ? (<button type="button" key="0" title="Collapse group" className='modalButton' onClick={collapse}>Collapse</button>)
         : (<button type="button" title="Expand group" key="0" className='modalButton' onClick={expand}>Expand</button>)
  }

  renderGroup(testResults) {
    return (
      <div>
        <div className="node-table">
          <div className="node-table-body">

            <div className="node-row">
              <div className="node-label">Group id</div>
              <div className="node-value">
                <input type="text" className="node-input" value={this.state.editedNode.id}
                       onChange={(e) => { const newId = e.target.value; this.setState((prevState) => ({ editedNode: { ...prevState.editedNode, id: newId}}))}} />
              </div>
            </div>
            {this.state.editedNode.nodes.map((node, idx) => (<div key={idx}>
                                    <NodeDetailsContent isEditMode={false} node={node} processDefinitionData={this.props.processDefinitionData}
                                         nodeErrors={this.props.nodeErrors} onChange={() => {}} testResults={testResults(node.id)}/><hr/></div>))}
          </div>
        </div>
      </div>
    )
  }

  isGroup() {
    return NodeUtils.nodeIsGroup(this.state.editedNode)
  }


  render() {
    var isOpen = !_.isEmpty(this.props.nodeToDisplay) && this.props.showNodeDetailsModal
    var headerStyles = EspModalStyles.headerStyles(this.nodeAttributes().styles.fill, this.nodeAttributes().styles.color)
    var testResults = (id) => TestResultUtils.resultsForNode(this.props.testResults, id)
    return (
      <div className="objectModal">
        <Modal shouldCloseOnOverlayClick={false} isOpen={isOpen} className="espModal" onRequestClose={this.closeModal}>
          <div className="modalHeader" style={headerStyles}>
            <span>{this.nodeAttributes().name}</span>
          </div>
          <div className="modalContent">
            <Scrollbars hideTracksWhenNotNeeded={true} autoHeight autoHeightMax={cssVariables.modalContentMaxHeight} renderThumbVertical={props => <div {...props} className="thumbVertical"/>}>
              {
                this.isGroup() ? this.renderGroup(testResults)
                  : (<NodeDetailsContent isEditMode={true} node={this.state.editedNode} processDefinitionData={this.props.processDefinitionData}
                         nodeErrors={this.props.nodeErrors} onChange={this.updateNodeState} testResults={testResults(this.state.currentNodeId)}/>)
              }
            </Scrollbars>
          </div>
          <div className="modalFooter">
            <div className="footerButtons">
              {this.renderModalButtons()}
            </div>
          </div>
        </Modal>
      </div>
    );
  }
}


function mapState(state) {
  var nodeId = state.graphReducer.nodeToDisplay.id
  var errors = nodeId ? _.get(state.graphReducer.processToDisplay, `validationResult.errors.invalidNodes[${state.graphReducer.nodeToDisplay.id}]`, [])
    : _.get(state.graphReducer.processToDisplay, "validationResult.errors.processPropertiesErrors", [])
  return {
    nodeToDisplay: state.graphReducer.nodeToDisplay,
    processId: state.graphReducer.processToDisplay.id,
    nodeErrors: errors,
    processToDisplay: state.graphReducer.processToDisplay,
    readOnly: !state.settings.loggedUser.canWrite,
    showNodeDetailsModal: state.ui.showNodeDetailsModal,
    testResults: state.graphReducer.testResults,
    processDefinitionData: state.settings.processDefinitionData,
    expandedGroups: state.ui.expandedGroups
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(NodeDetailsModal);
