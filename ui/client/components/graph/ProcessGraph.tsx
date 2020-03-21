import {DropTarget} from "react-dnd"
import {connect} from "react-redux"
import {compose} from "redux"
import ActionsUtils from "../../actions/ActionsUtils"
import {
  getEdgeToDisplay,
  getFetchedProcessDetails,
  getGroupingState,
  getLayout,
  getNodeToDisplay,
  getProcessCounts,
  getProcessToDisplay,
  isBusinessView,
} from "../../reducers/selectors/graph"
import {getExpandedGroups, getShowNodeDetailsModal} from "../../reducers/selectors/ui"
import {commonState, Graph} from "./Graph"

const spec = {
  drop: (props, monitor, component) => {
    const relOffset = component.computeRelOffset(monitor.getClientOffset())
    component.addNode(monitor.getItem(), relOffset)
  },
}

function mapState(state) {
  return {
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
    groupingState: getGroupingState(state),
    processCounts: getProcessCounts(state),
    edgeToDisplay: getEdgeToDisplay(state),
    layout: getLayout(state),
    expandedGroups: getExpandedGroups(state),
    showNodeDetailsModal: getShowNodeDetailsModal(state),
    ...commonState(state),
  }
}

export const ProcessGraph = compose(
  // eslint-disable-next-line i18next/no-literal-string
  DropTarget("element", spec, (connect) => ({connectDropTarget: connect.dropTarget()})),
  //withRef is here so that parent can access methods in graph
  connect(mapState, ActionsUtils.mapDispatchWithEspActions, null, {forwardRef: true}),
)(Graph)
