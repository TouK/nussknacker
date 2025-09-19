import type { Layout } from "../../actions/nk/ui/layout";
import type { ProcessCounts } from "../../http/resultsWithCountsDto";
import type { Capabilities } from "../../reducers/selectors/other";
import type { AppDispatch } from "../../store/storeHelpers";
import type { Scenario } from "../Process/types";

type ScenarioGraphProps = {
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
    dispatch: AppDispatch;
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
    BLANK_POINTERDBLCLICK = "blank:pointerdblclick",
    BLANK_POINTERDOWN = "blank:pointerdown",
    BLANK_POINTERUP = "blank:pointerup",
    BLANK_POINTERMOVE = "blank:pointermove",
    BLANK_MOUSEOVER = "blank:mouseover",
    REMOVE = "remove",
    ADD = "add",
    CHANGE_POSITION = "change:position",
    CHANGE_DRAG_OVER = "change:draggedOver",
}
