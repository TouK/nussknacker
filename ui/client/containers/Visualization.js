import React from 'react';
import {Prompt} from 'react-router-dom';
import Graph from '../components/graph/Graph';
import UserRightPanel from '../components/right-panel/UserRightPanel';
import UserLeftPanel from '../components/UserLeftPanel';
import HttpService from '../http/HttpService'
import _ from 'lodash';
import {connect} from 'react-redux';
import ActionsUtils from '../actions/ActionsUtils';
import ProcessUtils from '../common/ProcessUtils';
import DialogMessages from '../common/DialogMessages';
import '../stylesheets/visualization.styl';
import NodeUtils from '../components/graph/NodeUtils';
import * as VisualizationUrl from '../common/VisualizationUrl'
import SpinnerWrapper from "../components/SpinnerWrapper";
import * as JsonUtils from "../common/JsonUtils";

class Visualization extends React.Component {

  constructor(props) {
    super(props);
    this.state = {timeoutId: null, intervalId: null, status: {}, isArchived: null, dataResolved: false};
    this.graphRef = React.createRef();

    const {loggedUser, processCategory} = this.props;
    this.capabilities = {
      write: loggedUser.canWrite(processCategory) && !this.state.isArchived,
      deploy: loggedUser.canDeploy(processCategory) && !this.state.isArchived,
    };
    this.bindShortCuts();
  }

  bindShortCuts() {
    this.windowListeners = {
      copy: (event) => this.copySelection(event, true),
      paste: (event) => this.pasteSelection(event),
      cut: (event) => this.cutSelection(event)
    }
  }

  componentDidMount() {
    const businessView = VisualizationUrl.extractBusinessViewParams(this.props.location.search)
    this.setBusinessView(businessView)
    this.fetchProcessDetails(businessView).then((details) => {
      this.props.actions.displayProcessActivity(this.props.match.params.processId)
      this.props.actions.fetchProcessDefinition(
        details.fetchedProcessDetails.processingType,
        _.get(details, "fetchedProcessDetails.json.properties.isSubprocess"),
        this.props.subprocessVersions
      ).then(() => {
        this.setState({isArchived: _.get(details, "fetchedProcessDetails.isArchived"), dataResolved: true})
        this.showModalDetailsIfNeeded(details.fetchedProcessDetails.json);
        this.showCountsIfNeeded(details.fetchedProcessDetails.json);
      })

      this.fetchProcessStatus()
    }).catch((error) => {
      this.props.actions.handleHTTPError(error)
    })

    this.bindKeyboardActions()
  }

  showModalDetailsIfNeeded(process) {
    const {nodeId, edgeId} = VisualizationUrl.extractVisualizationParams(this.props.location.search)
    if (nodeId) {
      const node = NodeUtils.getNodeById(nodeId, process)

      if (node) {
        this.props.actions.displayModalNodeDetails(node)
      } else {
        this.props.history.replace({search: VisualizationUrl.setAndPreserveLocationParams({nodeId: null})})
      }
    }

    if (edgeId) {
      const edge = NodeUtils.getEdgeById(edgeId, process)
      if (edge) {
        this.props.actions.displayModalEdgeDetails(edge)
      } else {
        this.props.history.replace({search: VisualizationUrl.setAndPreserveLocationParams({edgeId: null})})
      }
    }
  }

  setBusinessView(businessView) {
    if (businessView != null) {
      this.props.actions.businessViewChanged(businessView)
    }
  }

  showCountsIfNeeded(process) {
    const countParams = VisualizationUrl.extractCountParams(this.props.location.search);
    if (countParams) {
      const {from, to} = countParams;
      this.props.actions.fetchAndDisplayProcessCounts(process.id, from, to);
    }
  }

  bindKeyboardActions() {
    window.onkeydown = (event) => {
      if (event.ctrlKey && !event.shiftKey && event.key.toLowerCase() == "z") {
        this.undo()
      }
      if (event.ctrlKey && event.shiftKey && event.key.toLowerCase() == "z") {
        this.redo()
      }

      if (event.key === 'Delete' && !_.isEmpty(this.props.selectionState) && this.props.canDelete) {
        this.deleteSelection()
      }
    }
    _.forOwn(this.windowListeners, (listener, type) => window.addEventListener(type, listener))
  }

  componentWillUnmount() {
    clearTimeout(this.state.timeoutId)
    clearInterval(this.state.intervalId)
    this.props.actions.clearProcess()
    _.forOwn(this.windowListeners, (listener, type) => window.removeEventListener(type, listener))
  }

  fetchProcessDetails(businessView) {
    const details = this.props.actions.fetchProcessToDisplay(this.props.match.params.processId, undefined, businessView)
    return details
  }

  fetchProcessStatus() {
    HttpService.fetchSingleProcessStatus(this.props.match.params.processId).then((response) => {
      this.setState({status: response})
    })
  }

  isRunning() {
    return _.get(this.state.status, 'isRunning', false)
  }

  undo() {
    //this `if` should be closer to reducer?
    if (this.props.undoRedoAvailable) {
      this.props.undoRedoActions.undo()
    }
  }

  redo() {
    if (this.props.undoRedoAvailable) {
      this.props.undoRedoActions.redo()
    }
  }

  deleteSelection() {
    this.props.actions.deleteNodes(this.props.selectionState)
  }

  copySelection = (event, shouldCreateNotification) => {
    const copyNodeElementId = 'copy-node'
    if (event.target && event.target.id !== copyNodeElementId && this.canCopySelection()) {
      let nodeIds = this.props.selectionState;
      let process = this.props.processToDisplay;
      const selectedNodes = NodeUtils.getAllNodesById(nodeIds, process)
      const edgesForNodes = NodeUtils.getEdgesForConnectedNodes(nodeIds, process)
      const selection = {
        nodes: selectedNodes,
        edges: edgesForNodes
      }
      this.props.actions.copySelection(JSON.stringify(selection));
      if (shouldCreateNotification) {
        this.props.notificationActions.success(this.successMessage('Copied', selectedNodes))
      }
    }
  }

  successMessage(action, selectedNodes) {
    return `${action} ${selectedNodes.length} ${selectedNodes.length === 1 ? 'node' : 'nodes'}`;
  }

  canCopySelection() {
    return this.props.allModalsClosed &&
        !_.isEmpty(this.props.selectionState) &&
        NodeUtils.containsOnlyPlainNodesWithoutGroups(this.props.selectionState, this.props.processToDisplay)
  }

  cutSelection = (event) => {
    if (this.canCutSelection() ) {
      this.copySelection(event, false)
      const nodeIds = NodeUtils.getAllNodesById(this.props.selectionState, this.props.processToDisplay)
          .map(node => node.id)
      this.props.actions.deleteNodes(nodeIds)
      this.props.notificationActions.success(this.successMessage('Cut', nodeIds))
    }
  }

  canCutSelection() {
    return this.canCopySelection() && this.capabilities.write
  }

  pasteSelection = (event) => {
    if (!this.props.allModalsClosed) {
      return
    }
    const selection = JsonUtils.tryParseOrNull(this.props.clipboard)
    const canPasteSelection = _.has(selection, 'nodes') && _.has(selection, 'edges') && selection.nodes.every(node => this.canAddNode(node))
    if (!canPasteSelection) {
      this.props.notificationActions.error("Cannot paste invalid nodes")
      return
    }

    const positions = selection.nodes.map((node, ix) => {
      return {x: 300, y: ix * 100}
    })
    const nodesWithPositions = _.zipWith(selection.nodes, positions, (node, position) => {
      return {node, position}
    })
    this.props.actions.nodesWithEdgesAdded(nodesWithPositions, selection.edges)
    this.props.notificationActions.success(this.successMessage('Pasted', selection.nodes))
  }

  canAddNode(node) {
    return this.capabilities.write &&
        NodeUtils.isNode(node) &&
        !NodeUtils.nodeIsGroup(node) &&
        NodeUtils.isAvailable(node, this.props.processDefinitionData, this.props.processCategory)
  }

  render() {
    const {leftPanelIsOpened, actions, loggedUser} = this.props;

    //it has to be that way, because graph is redux component
    const getGraph = () => this.graphRef.current.getDecoratedComponentInstance()
    const graphLayoutFun = () => getGraph().directedLayout()
    const exportGraphFun = () => getGraph().exportGraph()
    const zoomOutFun = () => getGraph().zoomOut()
    const zoomInFun = () => getGraph().zoomIn()

    const graphNotReady = _.isEmpty(this.props.fetchedProcessDetails) || this.props.graphLoading;

    return (
      <div className="Page">
        <Prompt
          when={!this.props.nothingToSave}
          message={(location, action) => action === 'PUSH' ? DialogMessages.unsavedProcessChanges() : true}
        />

        <UserLeftPanel
          isOpened={leftPanelIsOpened}
          onToggle={actions.toggleLeftPanel}
          loggedUser={loggedUser}
          capabilities={this.capabilities}
          isReady={this.state.dataResolved}
        />

        <UserRightPanel
          graphLayoutFunction={graphLayoutFun}
          exportGraph={exportGraphFun}
          zoomIn={zoomInFun}
          zoomOut={zoomOutFun}
          capabilities={this.capabilities}
          isReady={this.state.dataResolved}
          copySelection={(event) => this.copySelection(event, true)}
          cutSelection={(event) => this.cutSelection(event)}
          pasteSelection={(event) => this.pasteSelection(event)}
        />

        <SpinnerWrapper isReady={!graphNotReady}>
          <Graph ref={this.graphRef} capabilities={this.capabilities}/>
        </SpinnerWrapper>
      </div>
    );
  }
}

Visualization.path = VisualizationUrl.visualizationPath
Visualization.header = 'Visualization'

function mapState(state) {
  const processCategory = _.get(state, 'graphReducer.fetchedProcessDetails.processCategory');
  const canDelete = state.ui.allModalsClosed
    && !NodeUtils.nodeIsGroup(state.graphReducer.nodeToDisplay)
    && state.settings.loggedUser.canWrite(processCategory);
  return {
    processCategory: processCategory,
    selectionState: state.graphReducer.selectionState,
    processToDisplay: state.graphReducer.processToDisplay,
    processDefinitionData: state.settings.processDefinitionData || {},
    canDelete: canDelete,
    fetchedProcessDetails: state.graphReducer.fetchedProcessDetails,
    subprocessVersions: _.get(state.graphReducer.processToDisplay, "properties.subprocessVersions"),
    currentNodeId: (state.graphReducer.nodeToDisplay || {}).id,
    graphLoading: state.graphReducer.graphLoading,
    leftPanelIsOpened: state.ui.leftPanelIsOpened,
    undoRedoAvailable: state.ui.allModalsClosed,
    allModalsClosed: state.ui.allModalsClosed,
    nothingToSave: ProcessUtils.nothingToSave(state),
    loggedUser: state.settings.loggedUser,
    clipboard: state.graphReducer.clipboard
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Visualization);
