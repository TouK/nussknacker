import type {
    editNode,
    injectNode,
    Layout,
    layoutChanged,
    nodeAdded,
    nodesConnected,
    nodesDisconnected,
    nodesWithEdgesAdded,
    replaceNode,
    resetSelection,
    toggleSelection,
} from "../../actions/nk";
import type { ProcessCounts } from "../../http/resultsWithCountsDto";
import type { Capabilities } from "../../reducers/selectors/other";
import type { NodeType } from "../../types";
import type { Scenario } from "../Process/types";

type ScenarioGraphProps = {
    nodesConnected: typeof nodesConnected;
    nodesDisconnected: typeof nodesDisconnected;
    layoutChanged: typeof layoutChanged;
    injectNode: typeof injectNode;
    editNode: typeof editNode;
    replaceNode: typeof replaceNode;
    nodeAdded: typeof nodeAdded;
    nodesWithEdgesAdded: typeof nodesWithEdgesAdded;
    resetSelection: typeof resetSelection;
    toggleSelection: typeof toggleSelection;

    scenario: Scenario;
    divId: string;
    nodeIdPrefixForFragmentTests?: string;
    processCounts: ProcessCounts;
    capabilities: Capabilities;
    layout: Layout;

    readonly?: boolean;
    nodeSelectionEnabled?: boolean;
    isDraggingOver?: boolean;
    isFragment?: false | null;

    connectDropTarget;
    createFragment?: (callback: (node: NodeType) => void) => void;
};

type FragmentGraphProps = {
    scenario: Scenario;
    divId: string;
    nodeIdPrefixForFragmentTests: string;
    processCounts: ProcessCounts;
    layout: Layout;
    isFragment: true;
    readonly: true;
};

export type GraphProps = ScenarioGraphProps | FragmentGraphProps;

export enum Events {
    LINK_CONNECT = "link:connect",
    LINK_DISCONNECT = "link:disconnect",
    LINK_MOUSEOVER = "link:mouseover",
    LINK_MOUSEENTER = "link:mouseenter",
    LINK_MOUSELEAVE = "link:mouseleave",
    LINK_POINTERDOWN = "link:pointerdown",
    CELL_POINTERUP = "cell:pointerup",
    CELL_POINTERDOWN = "cell:pointerdown",
    CELL_POINTERMOVE = "cell:pointermove",
    CELL_POINTERCLICK = "cell:pointerclick",
    CELL_POINTERDBLCLICK = "cell:pointerdblclick",
    CELL_MOUSEOVER = "cell:mouseover",
    CELL_MOUSEOUT = "cell:mouseout",
    CELL_MOUSEENTER = "cell:mouseenter",
    CELL_MOUSELEAVE = "cell:mouseleave",
    CELL_MOVED = "cellCustom:moved",
    CELL_RESIZED = "cellCustom:resized",
    CELL_CONTENT_UPDATED = "cellCustom:contentUpdated",
    BLANK_POINTERCLICK = "blank:pointerclick",
    BLANK_POINTERDOWN = "blank:pointerdown",
    BLANK_POINTERUP = "blank:pointerup",
    BLANK_POINTERMOVE = "blank:pointermove",
    BLANK_MOUSEOVER = "blank:mouseover",
    REMOVE = "remove",
    ADD = "add",
    CHANGE_POSITION = "change:position",
    CHANGE_DRAG_OVER = "change:draggedOver",
}
