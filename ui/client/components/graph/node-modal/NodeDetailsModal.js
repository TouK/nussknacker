import "ladda/dist/ladda.min.css"
import _ from "lodash"
import PropTypes from "prop-types"
import React from "react"
import {Scrollbars} from "react-custom-scrollbars"
import Draggable from "react-draggable"
import LaddaButton from "react-ladda"
import Modal from "react-modal"
import {connect} from "react-redux"
import ActionsUtils from "../../../actions/ActionsUtils"
import ProcessUtils from "../../../common/ProcessUtils"
import TestResultUtils from "../../../common/TestResultUtils"
import HttpService from "../../../http/HttpService"
import {getProcessCounts, isBusinessView} from "../../../reducers/selectors/graph"
import {getExpandedGroups} from "../../../reducers/selectors/groups"
import cssVariables from "../../../stylesheets/_variables.styl"
import {ButtonWithFocus} from "../../withFocus"
import NodeUtils from "../NodeUtils"
import {SubProcessGraph as BareGraph} from "../SubProcessGraph"
import NodeDetailsContent from "./NodeDetailsContent"
import NodeDetailsModalHeader from "./NodeDetailsModalHeader"
import NodeGroupDetailsContent from "./NodeGroupDetailsContent"

class NodeDetailsModal extends React.Component {

  static propTypes = {
    nodeToDisplay: PropTypes.object.isRequired,
    testResults: PropTypes.object,
    processId: PropTypes.string.isRequired,
    nodeErrors: PropTypes.array.isRequired,
    readOnly: PropTypes.bool.isRequired,
  }

  constructor(props) {
    super(props)
    this.state = {
      pendingRequest: false,
      shouldCloseOnEsc: true,
      subprocessContent: null,
      editedNode: this.props.nodeToDisplay,
      currentNodeId: this.props.nodeToDisplay.id,
    }
  }

  componentDidMount() {
    const {nodeToDisplay, showNodeDetailsModal, businessView, subprocessVersions} = this.props
    const isChromium = !!window.chrome
    if (nodeToDisplay && this.state.subprocessContent === null && showNodeDetailsModal && NodeUtils.nodeType(nodeToDisplay) === "SubprocessInput") {
      if (isChromium) { //Subprocesses work only in Chromium, there is problem with jonint and SVG
        const subprocessVersion = subprocessVersions[nodeToDisplay.ref.id]
        HttpService.fetchProcessDetails(nodeToDisplay.ref.id, subprocessVersion, businessView).then((response) => {
          this.setState({...this.state, subprocessContent: response.data.json})
        })
      } else {
        console.warn("Displaying subprocesses is available only in Chromium based browser.")
      }
    }
  }

  componentDidUpdate(prevProps, prevState) {
    if (!_.isEqual(prevProps.nodeToDisplay, this.props.nodeToDisplay)) {
      this.setState({editedNode: this.props.nodeToDisplay})
    }
  }

  closeModal = () => {
    this.props.actions.closeModals()
  }

  performNodeEdit = () => {
    this.setState({pendingRequest: true})

    const actionResult = this.isGroup() ?
      //TODO: try to get rid of this.state.editedNode, passing state of NodeDetailsContent via onChange is not nice...
      this.props.actions.editGroup(this.props.processToDisplay, this.props.nodeToDisplay.id, this.state.editedNode) :
      this.props.actions.editNode(this.props.processToDisplay, this.props.nodeToDisplay, this.state.editedNode)

    actionResult.then(() => {
      this.setState({pendingRequest: false})
      this.closeModal()
    }, () => this.setState({pendingRequest: false}))
  }

  updateNodeState = (newNodeState) => {
    this.setState({editedNode: newNodeState})
  }

  onNodeGroupChange = (event) => {
    const newId = event.target.value
    this.setState((prevState) => ({editedNode: {...prevState.editedNode, id: newId}}))
  }

  renderModalButtons() {
    return [
      this.isGroup() ? this.renderGroupUngroup() : null,
      <ButtonWithFocus key="2" type="button" title="Cancel node details" className="modalButton" onClick={this.closeModal}>
        Cancel
      </ButtonWithFocus>,
      !this.props.readOnly ? (
        <LaddaButton
          key="1"
          title="Apply node details"
          className="modalButton pull-right modalConfirmButton"
          loading={this.state.pendingRequest}
          data-style="zoom-in"
          onClick={this.performNodeEdit}
        >
          Apply
        </LaddaButton>
      ) :
        null,
    ]
  }

  renderGroupUngroup() {
    const expand = () => {
      this.props.actions.expandGroup(id)
      this.closeModal()
    }
    const collapse = () => {
      this.props.actions.collapseGroup(id)
      this.closeModal()
    }

    const id = this.state.editedNode.id
    const expanded = _.includes(this.props.expandedGroups, id)
    return expanded ? (
      <ButtonWithFocus
        type="button"
        key="0"
        title="Collapse group"
        className="modalButton"
        onClick={collapse}
      >Collapse</ButtonWithFocus>
    ) :
      (<ButtonWithFocus type="button" title="Expand group" key="0" className="modalButton" onClick={expand}>Expand</ButtonWithFocus>)
  }

  isGroup() {
    return NodeUtils.nodeIsGroup(this.state.editedNode)
  }

  renderSubprocess() {
    //FIXME: adjust height of graph in some more reasonable way :|
    //we don't use _.get here, because currentNodeId can contain spaces etc...
    const subprocessCounts = (this.props.processCounts[this.state.currentNodeId] || {}).subprocessCounts || {}
    return (
      <BareGraph
        processCounts={subprocessCounts}
        processToDisplay={this.state.subprocessContent}
        height={`${parseInt(cssVariables.modalContentMaxHeight) / 3}px`}
      />
    )
  }

  toogleCloseModalOnEsc = () => {
    this.setState({
      shouldCloseOnEsc: !this.state.shouldCloseOnEsc,
    })
  }

  render() {
    const {nodeErrors, nodeToDisplay, nodeSettings, readOnly, showNodeDetailsModal, testResults} = this.props
    const isOpen = !_.isEmpty(nodeToDisplay) && showNodeDetailsModal
    const nodeTestResults = (id) => TestResultUtils.resultsForNode(testResults, id)

    return (
      <div className="objectModal">
        <Modal
          shouldCloseOnOverlayClick={false}
          shouldCloseOnEsc={this.state.shouldCloseOnEsc}
          isOpen={isOpen}
          onRequestClose={this.closeModal}
        >
          <div className="draggable-container">
            <Draggable bounds="parent" handle=".modal-draggable-handle">
              <div className="espModal">
                <NodeDetailsModalHeader node={nodeToDisplay} nodeSettings={nodeSettings}/>
                <div className="modalContentDark" id="modal-content">
                  <Scrollbars
                    hideTracksWhenNotNeeded={true}
                    autoHeight
                    autoHeightMax={cssVariables.modalContentMaxHeight}
                    renderThumbVertical={props => <div {...props} className="thumbVertical"/>}
                  >
                    {
                      this.isGroup() ? (
                        <NodeGroupDetailsContent
                          testResults={nodeTestResults}
                          node={this.state.editedNode}
                          onChange={this.onNodeGroupChange}
                          readOnly={readOnly}
                        />
                      ) : (
                        <NodeDetailsContent
                          isEditMode={!readOnly}
                          showValidation={true}
                          showSwitch={true}
                          node={this.state.editedNode}
                          originalNodeId={this.state.currentNodeId}
                          nodeErrors={nodeErrors}
                          onChange={this.updateNodeState}
                          toogleCloseOnEsc={this.toogleCloseModalOnEsc}
                          testResults={nodeTestResults(this.state.currentNodeId)}
                        />
                      )
                    }
                    {
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
    )
  }
}

function mapState(state) {
  const nodeId = state.graphReducer.nodeToDisplay.id
  const errors = nodeId ?
    state.graphReducer.processToDisplay?.validationResult?.errors?.invalidNodes[state.graphReducer.nodeToDisplay.id] || [] :
    _.get(state.graphReducer.processToDisplay, "validationResult.errors.processPropertiesErrors", [])
  const nodeToDisplay = state.graphReducer.nodeToDisplay
  const processCategory = state.graphReducer.fetchedProcessDetails.processCategory
  const processDefinitionData = state.settings.processDefinitionData || {}

  return {
    nodeToDisplay: nodeToDisplay,
    nodeSettings: _.get(processDefinitionData.nodesConfig, ProcessUtils.findNodeConfigName(nodeToDisplay)) || {},
    processId: state.graphReducer.processToDisplay.id,
    subprocessVersions: state.graphReducer.processToDisplay.properties.subprocessVersions,
    nodeErrors: errors,
    processToDisplay: state.graphReducer.processToDisplay,
    readOnly: !state.settings.loggedUser.canWrite(processCategory) ||
      isBusinessView(state) ||
      state.graphReducer.nodeToDisplayReadonly ||
      _.get(state, "graphReducer.fetchedProcessDetails.isArchived") ||
      false,
    showNodeDetailsModal: state.ui.showNodeDetailsModal,
    testResults: state.graphReducer.testResults,
    processDefinitionData: processDefinitionData,
    expandedGroups: getExpandedGroups(state),
    processCounts: getProcessCounts(state),
    businessView: isBusinessView(state),

  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(NodeDetailsModal)
