import type { dia } from "jointjs";
import { g } from "jointjs";
import React, { forwardRef, useCallback, useImperativeHandle, useRef } from "react";
import { useDrop } from "react-dnd";

import { useUserSettings } from "../../common/useUserSettings";
import { getLayout, getProcessCounts, getScenario } from "../../reducers/selectors/graph";
import type { Capabilities } from "../../reducers/selectors/other";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";
import type { Edge } from "../../types/edge";
import type { NodeType } from "../../types/node";
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

function usePreviewOffset() {
    const [areAdvancedStickyNotesEnabled] = useUserSettings("node.advancedStickyNotes");

    const getPaperPreviewOffset = useCallback(
        function getPaperPreviewOffset(paper: dia.Paper, offset: g.PlainPoint, item: NodeType): g.Point {
            const nodeRectOffset = { x: RECT_WIDTH * -0.8, y: RECT_HEIGHT * -0.5 };
            return new g.Point(
                item.type === StickyNoteType ? (areAdvancedStickyNotesEnabled ? advancedNoteOffset : basicNoteOffset) : nodeRectOffset,
            )
                .offset(paper.clientToLocalPoint(offset))
                .snapToGrid(1, 1);
        },
        [areAdvancedStickyNotesEnabled],
    );

    const getPaperPreviewRect = useCallback(function getPaperPreviewRect(paper: dia.Paper, offset: g.PlainPoint): g.Rect {
        return new g.Rect(0, 0, RECT_WIDTH, RECT_HEIGHT)
            .offset(RECT_WIDTH * -0.8, RECT_HEIGHT * -0.5)
            .offset(paper.clientToLocalPoint(offset))
            .snapToGrid(1, 1);
    }, []);

    return { getPaperPreviewOffset, getPaperPreviewRect };
}

export const ProcessGraph = forwardRef<
    Graph,
    {
        capabilities: Capabilities;
    }
>(function ProcessGraph({ capabilities }, forwardedRef): React.JSX.Element {
    const scenario = useAppSelector(getScenario);
    const processCounts = useAppSelector(getProcessCounts);
    const layout = useAppSelector(getLayout);
    const { getPaperPreviewRect, getPaperPreviewOffset } = usePreviewOffset();

    const graph = useRef<Graph>(null);
    useImperativeHandle(forwardedRef, () => graph.current);

    const [{ isDraggingOver }, connectDropTarget] = useDrop<NodeType, ElementDropResult, { isDraggingOver: boolean }>(
        () => ({
            accept: DndTypes.ELEMENT,
            drop: (item, monitor) => {
                const clientOffset = monitor.getClientOffset();
                const paper = graph.current.processGraphPaper;
                const offset = getPaperPreviewOffset(paper, clientOffset, item);
                const cellBelow = graph.current.lastHoveredCell;
                const currentNode = getNodeData(cellBelow, scenario.scenarioGraph);
                const currentEdge = cellBelow?.isLink() ? cellBelow.get("edgeData") : null;
                setDraggedOver(graph.current.graph);
                return { paper, item, offset, currentNode, currentEdge };
            },
            hover: (item, monitor) => {
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
        }),
        [getPaperPreviewOffset, getPaperPreviewRect, scenario.scenarioGraph],
    );

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
