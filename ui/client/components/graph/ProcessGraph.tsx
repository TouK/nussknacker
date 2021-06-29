import {g} from "jointjs"
import {DropTarget} from "react-dnd"
import {connect} from "react-redux"
import {compose} from "redux"
import ActionsUtils from "../../actions/ActionsUtils"
import {
  getEdgeToDisplay,
  getFetchedProcessDetails,
  getLayout,
  getNodeToDisplay,
  getProcessCounts,
  getProcessToDisplay,
  isBusinessView,
} from "../../reducers/selectors/graph"
import {getExpandedGroups} from "../../reducers/selectors/groups"
import {isNodeDetailsModalVisible} from "../../reducers/selectors/ui"
import {setLinksHovered} from "./dragHelpers"
import {commonState, Graph} from "./Graph"

const spec = {
  drop: (props, monitor, component: Graph) => {
    const clientOffset = monitor.getClientOffset()
    const relOffset = component.processGraphPaper.clientToLocalPoint(clientOffset)
    component.addNode(monitor.getItem(), relOffset)
    setLinksHovered(component.graph)
  },
  hover: (props, monitor, component: Graph) => {
    const clientOffset = monitor.getClientOffset()
    const point = component.processGraphPaper.clientToLocalPoint(clientOffset)
    setLinksHovered(component.graph, new g.Rect(point).inflate(30, 10))
  },
}

function mapState(state) {
  return {
    ...commonState(state),
    // eslint-disable-next-line i18next/no-literal-string
    divId: "esp-graph",
    // eslint-disable-next-line i18next/no-literal-string
    parent: "working-area",
    padding: 0,
    singleClickNodeDetailsEnabled: true,
    nodeIdPrefixForSubprocessTests: "",
    readonly: isBusinessView(state),
    processToDisplay: getProcessToDisplay(state),
    fetchedProcessDetails: getFetchedProcessDetails(state),
    nodeToDisplay: getNodeToDisplay(state),
    processCounts: getProcessCounts(state),
    edgeToDisplay: getEdgeToDisplay(state),
    layout: getLayout(state),
    expandedGroups: getExpandedGroups(state),
    showNodeDetailsModal: isNodeDetailsModalVisible(state),
  }
}

export const ProcessGraph = compose(
  // eslint-disable-next-line i18next/no-literal-string
  DropTarget("element", spec, (connect) => ({connectDropTarget: connect.dropTarget()})),
  //withRef is here so that parent can access methods in graph
  connect(mapState, ActionsUtils.mapDispatchWithEspActions, null, {forwardRef: true}),
)(Graph)
