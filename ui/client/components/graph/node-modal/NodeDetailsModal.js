import React from 'react';
import {connect} from 'react-redux';
import Modal from 'react-modal';
import _ from 'lodash';
import LaddaButton from "react-ladda"
import "ladda/dist/ladda.min.css"
import ActionsUtils from '../../../actions/ActionsUtils';
import NodeUtils from '../NodeUtils';
import NodeDetailsContent from './NodeDetailsContent';
import NodeDetailsModalHeader from './NodeDetailsModalHeader'
import TestResultUtils from '../../../common/TestResultUtils'
import {Scrollbars} from "react-custom-scrollbars";
import cssVariables from "../../../stylesheets/_variables.styl";
import {BareGraph} from "../Graph";
import HttpService from "../../../http/HttpService";
import ProcessUtils from "../../../common/ProcessUtils";
import PropTypes from 'prop-types';
import Draggable from "react-draggable";
import {preventFromMoveSelectors} from "../../modals/GenericModalDialog";

class NodeDetailsModal extends React.Component {

  static propTypes = {
    nodeToDisplay: PropTypes.object.isRequired,
    testResults: PropTypes.object,
    processId: PropTypes.string.isRequired,
    nodeErrors: PropTypes.array.isRequired,
    readOnly: PropTypes.bool.isRequired
  }

  constructor(props) {
    super(props);
    this.state = {
      pendingRequest: false,
      shouldCloseOnEsc: true,
    };
  }

  componentWillReceiveProps(props) {
    const isChromium = !!window.chrome;

    const newState = {
      editedNode: props.nodeToDisplay,
      currentNodeId: props.nodeToDisplay.id,
      subprocessContent: null
    }

    //TODO more smooth subprocess loading in UI
    if (props.nodeToDisplay && props.showNodeDetailsModal && (NodeUtils.nodeType(props.nodeToDisplay) === "SubprocessInput")) {
      if (isChromium) { //Subprocesses work only in Chromium, there is problem with jonint and SVG
        const subprocessVersion = props.subprocessVersions[props.nodeToDisplay.ref.id]
        HttpService.fetchProcessDetails(props.nodeToDisplay.ref.id, subprocessVersion, this.props.businessView).then((response) =>
          this.setState({...newState, subprocessContent: response.data.json})
        )
      } else {
        console.warn("Displaying subprocesses is available only in Chromium based browser.")
      }
    } else {
      this.setState(newState)
    }
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
    this.setState( { pendingRequest: true})

    const actionResult = this.isGroup() ?
      this.props.actions.editGroup(this.props.processToDisplay, this.props.nodeToDisplay.id, this.state.editedNode)
      : this.props.actions.editNode(this.props.processToDisplay, this.props.nodeToDisplay, this.state.editedNode)

    actionResult.then (() => {
        this.setState( { pendingRequest: false})
        this.closeModal()
      }, () => this.setState( { pendingRequest: false})
    )
  }

  updateNodeState = (newNodeState) => {
    this.setState( { editedNode: newNodeState})
  }

  renderModalButtons() {
    return ([
      this.isGroup() ? this.renderGroupUngroup() : null,
      <button key="2" type="button" title="Cancel node details" className='modalButton' onClick={this.closeModal}>
        Cancel
      </button>,
      !this.props.readOnly ?
          <LaddaButton
              key="1"
              title="Apply node details"
              className='modalButton pull-right modalConfirmButton'
              loading={this.state.pendingRequest}
              data-style='zoom-in'
              onClick={this.performNodeEdit}
          >
            Apply
          </LaddaButton>
          :
          null
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
                                    <NodeDetailsContent isEditMode={false}
                                                        showValidation={true}
                                                        showSwitch={true}
                                                        node={node}
                                                        nodeErrors={this.props.nodeErrors}
                                                        onChange={() => {}}
                                                        testResults={testResults(node.id)}/><hr/></div>))}
          </div>
        </div>
      </div>
    )
  }

  isGroup() {
    return NodeUtils.nodeIsGroup(this.state.editedNode)
  }

  renderSubprocess() {
    //we don't use _.get here, because currentNodeId can contain spaces etc...
    const subprocessCounts = (this.props.processCounts[this.state.currentNodeId] || {}).subprocessCounts || {};
    return (<BareGraph processCounts={subprocessCounts} processToDisplay={this.state.subprocessContent}/>)
  }

  toogleCloseModalOnEsc = () => {
    this.setState({
      shouldCloseOnEsc: !this.state.shouldCloseOnEsc,
    })
  }

  render() {
    const {nodeErrors, nodeToDisplay, nodeSetting, readOnly, showNodeDetailsModal, testResults} = this.props
    const isOpen = !_.isEmpty(nodeToDisplay) && showNodeDetailsModal
    const nodeTestResults = (id) => TestResultUtils.resultsForNode(testResults, id)

    return (
      <div className="objectModal">
        <Modal shouldCloseOnOverlayClick={false}
               shouldCloseOnEsc={this.state.shouldCloseOnEsc}
               isOpen={isOpen}
               onRequestClose={this.closeModal}>
          <div className="draggable-container">
            <Draggable bounds="parent" cancel={preventFromMoveSelectors}>
              <div className="espModal">
                <NodeDetailsModalHeader node={nodeToDisplay} docsUrl={nodeSetting.docsUrl}/>
                <div className="modalContentDark" id="modal-content">
                  <Scrollbars hideTracksWhenNotNeeded={true} autoHeight
                              autoHeightMax={cssVariables.modalContentMaxHeight}
                              renderThumbVertical={props => <div {...props} className="thumbVertical"/>}>
                    {
                      this.isGroup() ? this.renderGroup(nodeTestResults)
                        : (<NodeDetailsContent isEditMode={!readOnly}
                                               showValidation={true}
                                               showSwitch={true}
                                               node={this.state.editedNode}
                                               nodeErrors={nodeErrors}
                                               onChange={this.updateNodeState}
                                               toogleCloseOnEsc={this.toogleCloseModalOnEsc}
                                               testResults={nodeTestResults(this.state.currentNodeId)}/>)
                    }
                    {
                      //FIXME: adjust height of modal with subprocess in some reasonable way :|
                      this.state.subprocessContent ? this.renderSubprocess() : null
                    }
                  </Scrollbars>
                </div>
                <div className="modalFooter">
                  <div className="footerButtons">
                    {this.renderModalButtons()}
                  </div>
                </div>
              </div>
            </Draggable>
          </div>
        </Modal>
      </div>
    );
  }
}

function mapState(state) {
  const nodeId = state.graphReducer.nodeToDisplay.id
  const errors = nodeId ? _.get(state.graphReducer.processToDisplay, `validationResult.errors.invalidNodes[${state.graphReducer.nodeToDisplay.id}]`, [])
    : _.get(state.graphReducer.processToDisplay, "validationResult.errors.processPropertiesErrors", [])
  const nodeToDisplay = state.graphReducer.nodeToDisplay
  const processCategory = state.graphReducer.fetchedProcessDetails.processCategory
  const processDefinitionData = state.settings.processDefinitionData || {}

  return {
    nodeToDisplay: nodeToDisplay,
    nodeSetting: _.get(processDefinitionData.nodesConfig, ProcessUtils.findNodeConfigName(nodeToDisplay)) || {},
    processId: state.graphReducer.processToDisplay.id,
    subprocessVersions: state.graphReducer.processToDisplay.properties.subprocessVersions,
    nodeErrors: errors,
    processToDisplay: state.graphReducer.processToDisplay,
    readOnly: !state.settings.loggedUser.canWrite(processCategory)
      || state.graphReducer.businessView
      || state.graphReducer.nodeToDisplayReadonly
      || _.get(state, 'graphReducer.fetchedProcessDetails.isArchived')
      || false,
    showNodeDetailsModal: state.ui.showNodeDetailsModal,
    testResults: state.graphReducer.testResults,
    processDefinitionData: processDefinitionData,
    expandedGroups: state.ui.expandedGroups,
    processCounts: state.graphReducer.processCounts || {},
    businessView: state.graphReducer.businessView

  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(NodeDetailsModal);
