import React from "react"
import {connect} from "react-redux"
import {compose} from "redux"
import ActionsUtils from "../../actions/ActionsUtils"
import {getNodeId} from "../../reducers/selectors/graph"
import {commonState, Graph, subprocessParent} from "./Graph"

function mapSubprocessState(state, props) {
  return {
    // eslint-disable-next-line i18next/no-literal-string
    divId: "esp-graph-subprocess",
    parent: subprocessParent,
    padding: 30,
    readonly: true,
    singleClickNodeDetailsEnabled: false,
    nodeIdPrefixForSubprocessTests: `${getNodeId(state)}-`, //TODO where should it be?
    processToDisplay: props.processToDisplay,
    processCounts: props.processCounts,
    ...commonState(state),
  }
}

export const SubProcessGraph = compose(
  connect(mapSubprocessState, ActionsUtils.mapDispatchWithEspActions),
)(props => <Graph {...props}/>)
