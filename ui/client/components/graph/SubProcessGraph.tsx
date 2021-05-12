import React from "react"
import {connect} from "react-redux"
import {compose} from "redux"
import ActionsUtils from "../../actions/ActionsUtils"
import * as LayoutUtils from "../../reducers/layoutUtils"
import {getNodeId} from "../../reducers/selectors/graph"
import {commonState, Graph, subprocessParent} from "./Graph"
import GraphWrapped from "./GraphWrapped"

function mapSubprocessState(state, props) {
  return {
    ...commonState(state),
    // TODO: for process its in redux, for subprocess here. find some consistent place
    layout: LayoutUtils.fromMeta(props.processToDisplay),
    // eslint-disable-next-line i18next/no-literal-string
    divId: "esp-graph-subprocess",
    parent: subprocessParent,
    padding: 30,
    readonly: true,
    singleClickNodeDetailsEnabled: false,
    nodeIdPrefixForSubprocessTests: `${getNodeId(state)}-`, //TODO where should it be?
  }
}

export const SubProcessGraph = compose(
  connect(mapSubprocessState, ActionsUtils.mapDispatchWithEspActions),
)(GraphWrapped)
