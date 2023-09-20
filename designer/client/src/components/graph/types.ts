import { ProcessCounts } from "../../reducers/graph";
import { Process } from "../../types";
import {
    injectNode,
    Layout,
    layoutChanged,
    nodeAdded,
    nodesConnected,
    nodesDisconnected,
    resetSelection,
    toggleSelection,
} from "../../actions/nk";
import { Capabilities } from "../../reducers/selectors/other";
import { ProcessType } from "../Process/types";

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

export enum Events {
    LINK_CONNECT = "link:connect",
    LINK_DISCONNECT = "link:disconnect",
    LINK_MOUSEOVER = "link:mouseover",
    LINK_MOUSEENTER = "link:mouseenter",
    LINK_MOUSELEAVE = "link:mouseleave",
    CELL_POINTERUP = "cell:pointerup",
    CELL_POINTERDOWN = "cell:pointerdown",
    CELL_POINTERCLICK = "cell:pointerclick",
    CELL_POINTERDBLCLICK = "cell:pointerdblclick",
    CELL_MOUSEOVER = "cell:mouseover",
    CELL_MOUSEOUT = "cell:mouseout",
    CELL_MOVED = "cellCustom:moved",
    BLANK_POINTERDOWN = "blank:pointerdown",
    BLANK_POINTERUP = "blank:pointerup",
    BLANK_POINTERMOVE = "blank:pointermove",
    BLANK_MOUSEOVER = "blank:mouseover",
    REMOVE = "remove",
    ADD = "add",
    CHANGE_POSITION = "change:position",
    CHANGE_DRAG_OVER = "change:draggedOver",
}
