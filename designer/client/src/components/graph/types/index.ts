import { ProcessCounts } from "../../../reducers/graph";
import { Process } from "../../../types";
import {
    injectNode,
    Layout,
    layoutChanged,
    nodeAdded,
    nodesConnected,
    nodesDisconnected,
    resetSelection,
    toggleSelection,
} from "../../../actions/nk";
import { Capabilities } from "../../../reducers/selectors/other";
import { ProcessType } from "../../Process/types";

export interface GraphProps {
    nodesConnected: typeof nodesConnected;
    nodesDisconnected: typeof nodesDisconnected;
    layoutChanged: typeof layoutChanged;
    injectNode: typeof injectNode;
    nodeAdded: typeof nodeAdded;
    resetSelection: typeof resetSelection;
    toggleSelection: typeof toggleSelection;

    processToDisplay: Process;
    divId: string;
    nodeIdPrefixForFragmentTests?: string;
    processCounts: ProcessCounts;
    capabilities: Capabilities;
    fetchedProcessDetails: ProcessType;
    layout: Layout;

    readonly?: boolean;
    nodeSelectionEnabled?: boolean;
    isDraggingOver?: boolean;
    isFragment?: boolean;

    connectDropTarget;
}
