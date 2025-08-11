import type { dia } from "jointjs";
import { g } from "jointjs";
import React, { forwardRef, useImperativeHandle, useRef } from "react";
import { useDrop } from "react-dnd";

import {
    editNode,
    fetchProcessDefinition,
    injectNode,
    layoutChanged,
    nodeAdded,
    nodesConnected,
    nodesDisconnected,
    nodesWithEdgesAdded,
    replaceNode,
    resetSelection,
    stickyNoteSetErrors,
    stickyNoteUpdated,
    toggleSelection,
} from "../../actions/nk";
import type { ThunkAction } from "../../actions/reduxTypes";
import { useUserSettings } from "../../common/userSettings";
import HttpService from "../../http/HttpService";
import { createUniqueName } from "../../reducers/graph/utils";
import { fetchScenarios, getScenariosNames } from "../../reducers/scenarios";
import { getLayout, getProcessCounts, getScenario } from "../../reducers/selectors/graph";
import type { Capabilities } from "../../reducers/selectors/other";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";
import type { Edge, NodeType } from "../../types";
import { DndTypes } from "../DndTypes";
import { RECT_HEIGHT, RECT_WIDTH } from "./EspNode/esp";
import { advancedNoteOffset } from "./EspNode/stickyNote/advancedStickyNoteConfig";
import { basicNoteOffset } from "./EspNode/stickyNote/basicStickyNoteConfig";
import type { Graph } from "./Graph";
import { getNodeData } from "./Graph";
import GraphWrapped from "./GraphWrapped";
import NodeUtils from "./NodeUtils";
import { setDraggedOver } from "./utils/dragHelpers";
import { StickyNoteType } from "./utils/stickyNotesUtils";

export type ElementDropResult = {
    item: NodeType;
    offset: g.PlainPoint;
    paper: dia.Paper;
    currentNode: NodeType | null;
    currentEdge: Edge | null;
};

export function getPaperPreviewOffset(paper: dia.Paper, offset: g.PlainPoint): g.Point {
    return new g.Point()
        .offset(RECT_WIDTH * -0.8, RECT_HEIGHT * -0.5)
        .offset(paper.clientToLocalPoint(offset))
        .snapToGrid(1, 1);
}

export function getPaperPreviewRect(paper: dia.Paper, offset: g.PlainPoint): g.Rect {
    return new g.Rect(0, 0, RECT_WIDTH, RECT_HEIGHT)
        .offset(RECT_WIDTH * -0.8, RECT_HEIGHT * -0.5)
        .offset(paper.clientToLocalPoint(offset))
        .snapToGrid(1, 1);
}

export const ProcessGraph = forwardRef<
    Graph,
    {
        capabilities: Capabilities;
    }
>(function ProcessGraph({ capabilities }, forwardedRef): JSX.Element {
    const scenario = useAppSelector(getScenario);
    const processCounts = useAppSelector(getProcessCounts);
    const layout = useAppSelector(getLayout);
    const [settings] = useUserSettings();
    const areAdvancedStickyNotesEnabled = settings["node.advancedStickyNotes"];

    const graph = useRef<Graph>();
    useImperativeHandle(forwardedRef, () => graph.current);

    const [{ isDraggingOver }, connectDropTarget] = useDrop({
        accept: DndTypes.ELEMENT,
        drop: (item: NodeType, monitor): ElementDropResult => {
            const clientOffset = monitor.getClientOffset();
            const paper = graph.current.processGraphPaper;
            const offset = getPaperPreviewOffset(paper, clientOffset);
            const cellBelow = graph.current.lastHoveredCell;
            const currentNode = getNodeData(cellBelow, scenario.scenarioGraph);
            const currentEdge = cellBelow?.isLink() ? cellBelow.get("edgeData") : null;
            setDraggedOver(graph.current.graph);
            return { paper, item, offset, currentNode, currentEdge };
        },
        hover: (item: NodeType, monitor) => {
            const node = item;
            const canInjectNode = NodeUtils.hasInputs(node) || NodeUtils.hasOutputs(node);

            if (canInjectNode) {
                const clientOffset = monitor.getClientOffset();
                const paper = graph.current.processGraphPaper;
                const rect = getPaperPreviewRect(paper, clientOffset);
                setDraggedOver(graph.current.graph, rect, null, item);
            } else {
                setDraggedOver(graph.current.graph);
            }
        },
        collect: (monitor) => ({
            isDraggingOver: monitor.isOver(),
        }),
    });

    const dispatch = useAppDispatch();

    return (
        <GraphWrapped
            ref={graph}
            connectDropTarget={connectDropTarget}
            isDraggingOver={isDraggingOver}
            capabilities={capabilities}
            divId={"nk-graph-main"}
            nodeSelectionEnabled
            scenario={scenario}
            processCounts={processCounts}
            layout={layout}
            dispatch={dispatch}
        />
    );
});
