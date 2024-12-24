import { ProcessCounts } from "../../reducers/graph";
import {
    injectNode,
    Layout,
    layoutChanged,
    nodeAdded,
    nodesConnected,
    nodesDisconnected,
    resetSelection,
    stickyNoteAdded,
    stickyNoteDeleted,
    stickyNoteUpdated,
    toggleSelection,
} from "../../actions/nk";
import { Capabilities } from "../../reducers/selectors/other";
import { Scenario } from "../Process/types";
import { StickyNote } from "../../common/StickyNote";

type ScenarioGraphProps = {
    nodesConnected: typeof nodesConnected;
    nodesDisconnected: typeof nodesDisconnected;
    layoutChanged: typeof layoutChanged;
    injectNode: typeof injectNode;
    nodeAdded: typeof nodeAdded;
    stickyNoteAdded: typeof stickyNoteAdded;
    stickyNoteUpdated: typeof stickyNoteUpdated;
    stickyNoteDeleted: typeof stickyNoteDeleted;
    resetSelection: typeof resetSelection;
    toggleSelection: typeof toggleSelection;

    stickyNotes: StickyNote[];
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
};

type FragmentGraphProps = {
    scenario: Scenario;
    stickyNotes: StickyNote[];
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
    CELL_DELETED = "cellCustom:deleted",
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
