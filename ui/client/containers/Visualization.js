import {defaultsDeep, isEmpty} from "lodash"
import React from "react"
import {connect} from "react-redux"
import ActionsUtils from "../actions/ActionsUtils"
import ProcessUtils from "../common/ProcessUtils"
import * as VisualizationUrl from "../common/VisualizationUrl"
import {GraphProvider} from "../components/graph/GraphContext"
import NodeUtils from "../components/graph/NodeUtils"
import {ProcessGraph as Graph} from "../components/graph/ProcessGraph"
import SelectionContextProvider from "../components/graph/SelectionContextProvider"
import RouteLeavingGuard from "../components/RouteLeavingGuard"
import SpinnerWrapper from "../components/SpinnerWrapper"
import Toolbars from "../components/toolbars/Toolbars"
import {getFetchedProcessDetails, getProcessToDisplay} from "../reducers/selectors/graph"
import {getCapabilities} from "../reducers/selectors/other"
import {getProcessDefinitionData} from "../reducers/selectors/settings"

import "../stylesheets/visualization.styl"
import {parseWindowsQueryParams} from "../windowManager/useWindows"
import {BindKeyboardShortcuts} from "./BindKeyboardShortcuts"
import {darkTheme} from "./darkTheme"
import {NkThemeProvider} from "./theme"

const PROCESS_STATE_INTERVAL_TIME = 10000

class Visualization extends React.Component {

  graphRef = React.createRef()
  processStateIntervalId = null

  state = {
    dataResolved: false,
  }

  get processId() {
    return decodeURIComponent(this.props.match.params.processId)
  }

  componentDidMount() {
    const {actions} = this.props
    this.fetchProcessDetails().then(async ({fetchedProcessDetails: {json, processingType, isSubprocess, isArchived}}) => {
      await actions.loadProcessToolbarsConfiguration(this.processId)
      actions.displayProcessActivity(this.processId)
      await actions.fetchProcessDefinition(processingType, json.properties?.isSubprocess)
      this.setState({dataResolved: true})
      this.showModalDetailsIfNeeded(json)
      this.showCountsIfNeeded(json)

      //We don't need load state for subproces and archived process..
      if (!isSubprocess && !isArchived) {
        this.startFetchStateInterval(PROCESS_STATE_INTERVAL_TIME)
      }
    }).catch((error) => {
      actions.handleHTTPError(error)
    })
  }

  startFetchStateInterval(time) {
    this.fetchProcessState()
    this.processStateIntervalId = setInterval(() => this.fetchProcessState(), time)
  }

  showModalDetailsIfNeeded(process) {
    const {showModalEdgeDetails, showModalNodeDetails, history} = this.props
    const params = parseWindowsQueryParams({nodeId: [], edgeId: []})

    const nodes = params.nodeId.map(id => NodeUtils.getNodeById(id, process)).filter(Boolean)
    nodes.forEach(showModalNodeDetails)

    this.getGraphInstance()?.highlightNodes(nodes)

    const edges = params.edgeId.map(id => NodeUtils.getEdgeById(id, process)).filter(Boolean)
    edges.forEach(showModalEdgeDetails)

    history.replace({
      search: VisualizationUrl.setAndPreserveLocationParams({
        nodeId: nodes.map(node => node.id).map(encodeURIComponent),
        edgeId: edges.map(NodeUtils.edgeId).map(encodeURIComponent),
      }),
    })
  }

  showCountsIfNeeded(process) {
    const countParams = VisualizationUrl.extractCountParams(this.props.location.search)
    if (countParams) {
      const {from, to} = countParams
      this.props.actions.fetchAndDisplayProcessCounts(process.id, from, to)
    }
  }

  componentWillUnmount() {
    clearInterval(this.processStateIntervalId)
    this.props.closeModals()
    this.props.actions.clearProcess()
  }

  fetchProcessDetails = () => this.props.actions.fetchProcessToDisplay(
    this.processId,
    undefined,
  )

  fetchProcessState = () => this.props.actions.loadProcessState(this.props.fetchedProcessDetails?.id)

  getPastePosition = () => {
    const paper = this.getGraphInstance()?.processGraphPaper
    const {x, y} = paper?.getArea()?.center() || {x: 300, y: 100}
    return {x: Math.floor(x), y: Math.floor(y)}
  }

  getGraphInstance = () => this.graphRef.current?.getDecoratedComponentInstance()

  render() {
    const graphNotReady = isEmpty(this.props.fetchedProcessDetails) || this.props.graphLoading
    return (
      <div className={"Page graphPage"}>
        <RouteLeavingGuard
          when={this.props.capabilities.editFrontend && !this.props.nothingToSave}
          navigate={path => this.props.history.push(path)}
        />

        <GraphProvider graph={this.getGraphInstance}>
          <SelectionContextProvider pastePosition={this.getPastePosition}>
            <BindKeyboardShortcuts/>
            <NkThemeProvider theme={outerTheme => defaultsDeep(darkTheme, outerTheme)}>
              <Toolbars isReady={this.state.dataResolved}/>
            </NkThemeProvider>
          </SelectionContextProvider>
        </GraphProvider>

        <SpinnerWrapper isReady={!graphNotReady}>
          {!isEmpty(this.props.processDefinitionData) ?
            (
              <Graph
                ref={this.graphRef}
                capabilities={this.props.capabilities}
                showModalEdgeDetails={this.props.showModalEdgeDetails}
              />
            ) :
            null}
        </SpinnerWrapper>
      </div>
    )
  }
}

Visualization.path = VisualizationUrl.visualizationPath
Visualization.header = "Visualization"

function mapState(state) {
  const processToDisplay = getProcessToDisplay(state)
  return {
    processToDisplay,
    processDefinitionData: getProcessDefinitionData(state),
    fetchedProcessDetails: getFetchedProcessDetails(state),
    graphLoading: state.graphReducer.graphLoading,
    nothingToSave: ProcessUtils.nothingToSave(state),
    capabilities: getCapabilities(state),
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Visualization)
