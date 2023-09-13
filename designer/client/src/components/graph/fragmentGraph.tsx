import { connect } from "react-redux";
import { compose } from "redux";
import * as LayoutUtils from "../../reducers/layoutUtils";
import GraphWrapped from "./GraphWrapped";
import { injectNode, layoutChanged, nodeAdded, nodesConnected, nodesDisconnected, resetSelection, toggleSelection } from "../../actions/nk";

const mapFragmentState = (state, props) => ({
    // TODO: for process its in redux, for fragment here. find some consistent place
    layout: LayoutUtils.fromMeta(props.processToDisplay),
    divId: `nk-graph-fragment`,
    readonly: true,
    isFragment: true,
    nodeSelectionEnabled: false,
});

export const fragmentGraph = compose(
    connect(mapFragmentState, {
        nodesConnected,
        nodesDisconnected,
        layoutChanged,
        injectNode,
        nodeAdded,
        resetSelection,
        toggleSelection,
    }),
)(GraphWrapped);
