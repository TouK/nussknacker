import _, {defaultsDeep, isEmpty} from "lodash"
import React from "react"
import {connect} from "react-redux"
import ActionsUtils from "../actions/ActionsUtils"
import ProcessUtils from "../common/ProcessUtils"
import * as VisualizationUrl from "../common/VisualizationUrl"
import {RECT_HEIGHT, RECT_WIDTH} from "../components/graph/EspNode/esp"
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
import {areAllModalsClosed} from "../reducers/selectors/ui"
import "../stylesheets/visualization.styl"
import {darkTheme} from "./darkTheme"
import {BindKeyboardShortcuts} from "./BindKeyboardShortcuts"
import {NkThemeProvider} from "./theme"

class Visualization extends React.Component {

  state = {
    processStateIntervalTime: 10000,
    processStateIntervalId: null,
    dataResolved: false,
  }

  constructor(props) {
    super(props)
    this.graphRef = React.createRef()
  }

  componentDidMount() {
    this.fetchProcessDetails().then(async (details) => {
      await this.props.actions.loadProcessToolbarsConfiguration(this.props.match.params.processId)
      this.props.actions.displayProcessActivity(this.props.match.params.processId)
      this.props.actions.fetchProcessDefinition(
        details.fetchedProcessDetails.processingType,
        _.get(details, "fetchedProcessDetails.json.properties.isSubprocess")
      ).then(() => {
        this.setState({dataResolved: true})
        this.showModalDetailsIfNeeded(details.fetchedProcessDetails.json)
        this.showCountsIfNeeded(details.fetchedProcessDetails.json)
      })

      //We don't need load state for subproces and archived process..
      if (this.props.fetchedProcessDetails.isSubprocess === false && this.props.fetchedProcessDetails.isArchived === false) {
        this.fetchProcessState()
        this.state.processStateIntervalId = setInterval(
          () => this.fetchProcessState(),
          this.state.processStateIntervalTime,
        )
      }
    }).catch((error) => {
      this.props.actions.handleHTTPError(error)
    })
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

  showCountsIfNeeded(process) {
    const countParams = VisualizationUrl.extractCountParams(this.props.location.search)
    if (countParams) {
      const {from, to} = countParams
      this.props.actions.fetchAndDisplayProcessCounts(process.id, from, to)
    }
  }

  componentWillUnmount() {
    clearInterval(this.state.processStateIntervalId)
    this.props.actions.clearProcess()
  }

  fetchProcessDetails = () => this.props.actions.fetchProcessToDisplay(
    this.props.match.params.processId,
    undefined,
  )

  fetchProcessState = () => this.props.actions.loadProcessState(this.props.fetchedProcessDetails?.id)

  getPastePosition = () => {
    const paper = this.getGraphInstance()?.processGraphPaper
    return paper?.getArea()?.center() || {x: 300, y: 100}
  }

  getGraphInstance = () => this.graphRef.current?.getDecoratedComponentInstance()

  render() {
    const graphNotReady = isEmpty(this.props.fetchedProcessDetails) || this.props.graphLoading
    return (
      <div className={"Page graphPage"}>
        <RouteLeavingGuard
          when={this.props.capabilities.write && !this.props.nothingToSave}
          navigate={path => this.props.history.push(path)}
        />

        <GraphProvider graph={this.getGraphInstance}>
          <SelectionContextProvider pastePosition={this.getPastePosition}>
            <BindKeyboardShortcuts disabled={!this.props.allModalsClosed}/>
            <NkThemeProvider theme={outerTheme => defaultsDeep(darkTheme, outerTheme)}>
              <Toolbars isReady={this.state.dataResolved}/>
            </NkThemeProvider>
          </SelectionContextProvider>
        </GraphProvider>

        <SpinnerWrapper isReady={!graphNotReady}>
          {!isEmpty(this.props.processDefinitionData) ? <Graph ref={this.graphRef} capabilities={this.props.capabilities}/> : null}
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
    allModalsClosed: areAllModalsClosed(state),
    nothingToSave: ProcessUtils.nothingToSave(state),
    capabilities: getCapabilities(state),
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Visualization)
