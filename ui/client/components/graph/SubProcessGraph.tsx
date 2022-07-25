import React from "react"
import {connect} from "react-redux"
import {compose} from "redux"
import * as LayoutUtils from "../../reducers/layoutUtils"
import GraphWrapped from "./GraphWrapped"
import {
  injectNode,
  layoutChanged,
  nodeAdded,
  nodesConnected,
  nodesDisconnected,
  resetSelection,
  toggleSelection,
} from "../../actions/nk"

const mapSubprocessState = (state, props) => ({
  // TODO: for process its in redux, for subprocess here. find some consistent place
  layout: LayoutUtils.fromMeta(props.processToDisplay),
  divId: `nk-graph-subprocess`,
  readonly: true,
  isSubprocess: true,
  nodeSelectionEnabled: false,
})

export const SubProcessGraph = compose(
  connect(mapSubprocessState, {
    nodesConnected,
    nodesDisconnected,
    layoutChanged,
    injectNode,
    nodeAdded,
    resetSelection,
    toggleSelection,
  }),
)(GraphWrapped)
