import React from "react"
import {connect} from "react-redux"
import {compose} from "redux"
import ActionsUtils from "../../actions/ActionsUtils"
import * as LayoutUtils from "../../reducers/layoutUtils"
import {commonState} from "./Graph"
import GraphWrapped from "./GraphWrapped"

function mapSubprocessState(state, props) {
  return {
    ...commonState(state),
    // TODO: for process its in redux, for subprocess here. find some consistent place
    layout: LayoutUtils.fromMeta(props.processToDisplay),
    divId: `nk-graph-subprocess`,
    readonly: true,
    isSubprocess: true,
    singleClickNodeDetailsEnabled: false,
  }
}

export const SubProcessGraph = compose(
  connect(mapSubprocessState, ActionsUtils.mapDispatchWithEspActions),
)(GraphWrapped)
