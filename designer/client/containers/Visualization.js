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
import {isEdgeEditable} from "../common/EdgeUtils"
import {GraphPage} from "./Page"

const PROCESS_STATE_INTERVAL_TIME = 10000

class Visualization extends React.Component {

  graphRef = React.createRef()
  processStateIntervalId = null

  state = {
    dataResolved: false,
  }

  componentDidMount() {
    const {processId, actions} = this.props
    actions.fetchProcessToDisplay(processId).then(async ({
      fetchedProcessDetails: {
        json,
        processingType,
        isSubprocess,
        isArchived,
      },
    }) => {
      await actions.loadProcessToolbarsConfiguration(processId)
      actions.displayProcessActivity(processId)
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
    const {showModalNodeDetails, navigate} = this.props
    const params = parseWindowsQueryParams({nodeId: [], edgeId: []})

    const edges = params.edgeId.map(id => NodeUtils.getEdgeById(id, process)).filter(isEdgeEditable)
    const nodes = params.nodeId
      .concat(edges.map(e => e.from))
      .map(id => NodeUtils.getNodeById(id, process) ?? (process.id === id && NodeUtils.getProcessProperties(process)))
      .filter(Boolean)
    nodes.forEach(node => showModalNodeDetails(node, process))

    this.getGraphInstance()?.highlightNodes(nodes)

    navigate(
      {
        search: VisualizationUrl.setAndPreserveLocationParams({
          nodeId: nodes.map(node => node.id).map(encodeURIComponent),
          edgeId: [],
        }),
      },
      {replace: true}
    )
  }

  showCountsIfNeeded(process) {
    const {actions, location} = this.props
    const countParams = VisualizationUrl.extractCountParams(location.search)
    if (countParams) {
      const {from, to} = countParams
      actions.fetchAndDisplayProcessCounts(process.id, from, to)
    }
  }

  async componentWillUnmount() {
    clearInterval(this.processStateIntervalId)
  }

  fetchProcessState = () => this.props.actions.loadProcessState(this.props.fetchedProcessDetails?.id)

  getPastePosition = () => {
    const paper = this.getGraphInstance()?.processGraphPaper
    const {x, y} = paper?.getArea()?.center() || {x: 300, y: 100}
    return {x: Math.floor(x), y: Math.floor(y)}
  }

  getGraphInstance = () => this.graphRef.current

  render() {
    const {
      nothingToSave,
      fetchedProcessDetails,
      capabilities,
      graphLoading,
      processDefinitionData,
    } = this.props

    const graphNotReady = isEmpty(fetchedProcessDetails) || graphLoading
    return (
      <GraphPage data-testid="graphPage">
        <RouteLeavingGuard when={capabilities.editFrontend && !nothingToSave}/>

        <GraphProvider graph={this.getGraphInstance}>
          <SelectionContextProvider pastePosition={this.getPastePosition}>
            <BindKeyboardShortcuts/>
            <NkThemeProvider theme={outerTheme => defaultsDeep(darkTheme, outerTheme)}>
              <Toolbars isReady={this.state.dataResolved}/>
            </NkThemeProvider>
          </SelectionContextProvider>
        </GraphProvider>

        <SpinnerWrapper isReady={!graphNotReady}>
          {!isEmpty(processDefinitionData) ?
            (
              <Graph
                ref={this.graphRef}
                capabilities={capabilities}
              />
            ) :
            null}
        </SpinnerWrapper>
      </GraphPage>
    )
  }
}

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
