import _ from "lodash"
import React from "react"
import {connect} from "react-redux"
import ActionsUtils from "../actions/ActionsUtils"
import {events} from "../analytics/TrackingEvents"
import ClipboardUtils from "../common/ClipboardUtils"
import * as JsonUtils from "../common/JsonUtils"
import ProcessUtils from "../common/ProcessUtils"
import * as VisualizationUrl from "../common/VisualizationUrl"
import Graph from "../components/graph/Graph"
import NodeUtils from "../components/graph/NodeUtils"
import UserRightPanel from "../components/right-panel/UserRightPanel"
import RouteLeavingGuard from "../components/RouteLeavingGuard"
import SpinnerWrapper from "../components/SpinnerWrapper"
import UserLeftPanel from "../components/UserLeftPanel"
import HttpService from "../http/HttpService"
import "../stylesheets/visualization.styl"

class Visualization extends React.Component {

  constructor(props) {
    super(props)
    this.state = {timeoutId: null, intervalId: null, status: {}, dataResolved: false}
    this.graphRef = React.createRef()
    this.bindShortCuts()
  }

  bindShortCuts() {
    this.windowListeners = {
      copy: this.bindCopyShortcut(),
      paste: this.bindPasteShortcut(),
      cut: this.bindCutShortcut(),
    }
  }

  bindCopyShortcut() {
    return (event) => {
      // Skip event triggered by writing selection to the clipboard.
      if (this.isNotThisCopyEvent(event, copyNodeElementId)) {
        this.props.actions.copySelection(
          () => this.copySelection(event, true),
          {category: events.categories.keyboard, action: events.actions.keyboard.copy},
        )
      }
    }
  }

  bindPasteShortcut() {
    return (event) => this.props.actions.pasteSelection(
      () => this.pasteSelection(event),
      {category: events.categories.keyboard, action: events.actions.keyboard.paste},
    )
  }

  bindCutShortcut() {
    return (event) => this.props.actions.cutSelection(
      () => this.cutSelection(event),
      {category: events.categories.keyboard, action: events.actions.keyboard.cut},
    )
  }

  componentDidMount() {
    const businessView = VisualizationUrl.extractBusinessViewParams(this.props.location.search)
    this.setBusinessView(businessView)
    this.fetchProcessDetails(businessView).then((details) => {
      this.props.actions.displayProcessActivity(this.props.match.params.processId)
      this.props.actions.fetchProcessDefinition(
        details.fetchedProcessDetails.processingType,
        _.get(details, "fetchedProcessDetails.json.properties.isSubprocess"),
        this.props.subprocessVersions,
      ).then(() => {
        this.setState({dataResolved: true})
        this.showModalDetailsIfNeeded(details.fetchedProcessDetails.json)
        this.showCountsIfNeeded(details.fetchedProcessDetails.json)
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
    const countParams = VisualizationUrl.extractCountParams(this.props.location.search)
    if (countParams) {
      const {from, to} = countParams
      this.props.actions.fetchAndDisplayProcessCounts(process.id, from, to)
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

      if (event.key === "Delete" && !_.isEmpty(this.props.selectionState) && this.props.canDelete) {
        this.props.actions.deleteSelection(
          this.props.selectionState,
          {category: events.categories.keyboard, action: events.actions.keyboard.delete},
        )
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
    return _.get(this.state.status, "isRunning", false)
  }

  undo() {
    //this `if` should be closer to reducer?
    if (this.props.undoRedoAvailable) {
      this.props.undoRedoActions.undo(
        {category: events.categories.keyboard, action: events.actions.keyboard.undo},
      )
    }
  }

  redo() {
    if (this.props.undoRedoAvailable) {
      this.props.undoRedoActions.redo(
        {category: events.categories.keyboard, action: events.actions.keyboard.redo},
      )
    }
  }

  copySelection = (event, shouldCreateNotification) => {
    // Skip event triggered by writing selection to the clipboard.
    const isNotThisCopyEvent = this.isNotThisCopyEvent(event, copyNodeElementId)

    isNotThisCopyEvent && this.canCopySelection() ? this.copyToClipboard(shouldCreateNotification) :
      this.props.notificationActions.error("Can not copy selected content. It should contain only plain nodes without groups")
  }

  copyToClipboard(shouldCreateNotification) {
    let nodeIds = this.props.selectionState
    let process = this.props.processToDisplay
    const selectedNodes = NodeUtils.getAllNodesById(nodeIds, process)
    const edgesForNodes = NodeUtils.getEdgesForConnectedNodes(nodeIds, process)
    const selection = {
      nodes: selectedNodes,
      edges: edgesForNodes,
    }
    ClipboardUtils.writeText(JSON.stringify(selection), copyNodeElementId)
    if (shouldCreateNotification) {
      this.props.notificationActions.success(this.successMessage("Copied", selectedNodes))
    }
  }

  isNotThisCopyEvent(event, copyNodeElementId) {
    return event == null || (event.target && event.target.id !== copyNodeElementId)
  }

  successMessage(action, selectedNodes) {
    return `${action} ${selectedNodes.length} ${selectedNodes.length === 1 ? "node" : "nodes"}`
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
      this.props.notificationActions.success(this.successMessage("Cut", nodeIds))
    }
  }

  canCutSelection() {
    return this.canCopySelection() && this.props.capabilities.write
  }

  pasteSelection = (event) => {
    if (!this.props.allModalsClosed) {
      return
    }
    const clipboardText = ClipboardUtils.readText(event)
    this.pasteSelectionFromText(clipboardText)
  }

  pasteSelectionFromClipboard = () => {
    const clipboard = navigator.clipboard
    if (typeof clipboard.readText !== "function") {
      this.props.notificationActions.error("Paste button is not available. Try Ctrl+V")
    } else {
      clipboard.readText().then(text => this.pasteSelectionFromText(text))
    }
  }

  pasteSelectionFromText = (text) => {
    const selection = JsonUtils.tryParseOrNull(text)
    const canPasteSelection = _.has(selection, "nodes") && _.has(selection, "edges") && selection.nodes.every(node => this.canAddNode(node))
    if (!canPasteSelection) {
      this.props.notificationActions.error("Cannot paste content from clipboard")
      return
    }

    const positions = selection.nodes.map((node, ix) => {
      return {x: 300, y: ix * 100}
    })
    const nodesWithPositions = _.zipWith(selection.nodes, positions, (node, position) => {
      return {node, position}
    })
    this.props.actions.nodesWithEdgesAdded(nodesWithPositions, selection.edges)
    this.props.notificationActions.success(this.successMessage("Pasted", selection.nodes))
  }

  canAddNode(node) {
    return this.props.capabilities.write &&
        NodeUtils.isNode(node) &&
        !NodeUtils.nodeIsGroup(node) &&
        NodeUtils.isAvailable(node, this.props.processDefinitionData, this.props.processCategory)
  }

  render() {
    const {leftPanelIsOpened, actions, loggedUser} = this.props

    //it has to be that way, because graph is redux component
    const getGraph = () => this.graphRef.current.getDecoratedComponentInstance()
    const graphLayoutFun = () => getGraph().directedLayout()
    const exportGraphFun = () => getGraph().exportGraph()
    const zoomOutFun = () => this.props.actions.zoomOut(getGraph())
    const zoomInFun = () => this.props.actions.zoomIn(getGraph())

    const graphNotReady = _.isEmpty(this.props.fetchedProcessDetails) || this.props.graphLoading

    return (
      <div className={"Page graphPage"}>
        <RouteLeavingGuard
          when={!this.props.nothingToSave}
          navigate={path => this.props.history.push(path)}
        />

        <UserLeftPanel
          isOpened={leftPanelIsOpened}
          onToggle={actions.toggleLeftPanel}
          loggedUser={loggedUser}
          capabilities={this.props.capabilities}
          isReady={this.state.dataResolved}
          processName={this.props.processToDisplay ? this.props.processToDisplay.id : ""}
        />

        <UserRightPanel
          graphLayoutFunction={graphLayoutFun}
          exportGraph={exportGraphFun}
          zoomIn={zoomInFun}
          zoomOut={zoomOutFun}
          capabilities={this.props.capabilities}
          isReady={this.state.dataResolved}
          selectionActions={{
            copy: () => this.copySelection(null, true),
            canCopy: this.canCopySelection(),
            cut: () => this.cutSelection(null),
            canCut: this.canCutSelection(),
            paste: () => this.pasteSelectionFromClipboard(null),
            canPaste: true,
          }}
          copySelection={() => this.copySelection(null, true)}
          cutSelection={() => this.cutSelection(null)}
          pasteSelection={() => this.pasteSelection(null)}
        />

        <SpinnerWrapper isReady={!graphNotReady}>
          <Graph ref={this.graphRef} capabilities={this.props.capabilities}/>
        </SpinnerWrapper>
      </div>
    )
  }
}

Visualization.path = VisualizationUrl.visualizationPath
Visualization.header = "Visualization"

function mapState(state) {
  const processCategory = _.get(state, "graphReducer.fetchedProcessDetails.processCategory")
  const canDelete = state.ui.allModalsClosed
    && !NodeUtils.nodeIsGroup(state.graphReducer.nodeToDisplay)
    && state.settings.loggedUser.canWrite(processCategory)
  const loggedUser = state.settings.loggedUser
  const isArchived = _.get(state, "graphReducer.fetchedProcessDetails.isArchived")
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
    loggedUser: loggedUser,
    capabilities: {
      write: loggedUser.canWrite(processCategory) && !isArchived,
      deploy: loggedUser.canDeploy(processCategory) && !isArchived,
    },
  }
}

const copyNodeElementId = "copy-node"

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Visualization)
