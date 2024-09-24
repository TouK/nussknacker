import { g } from "jointjs";
import { mapValues } from "lodash";
import React, { forwardRef, useImperativeHandle, useMemo, useRef } from "react";
import { useDrop } from "react-dnd";
import { useDispatch, useSelector } from "react-redux";
import { bindActionCreators } from "redux";
import { injectNode, layoutChanged, nodeAdded, nodesConnected, nodesDisconnected, resetSelection, toggleSelection } from "../../actions/nk";
import { getLayout, getProcessCounts, getScenario } from "../../reducers/selectors/graph";
import { Capabilities } from "../../reducers/selectors/other";
import { NodeType } from "../../types";
import { DndTypes } from "../toolbars/creator/Tool";
import { RECT_HEIGHT, RECT_WIDTH } from "./EspNode/esp";
import { Graph } from "./Graph";
import GraphWrapped from "./GraphWrapped";
import NodeUtils from "./NodeUtils";
import { setLinksHovered } from "./utils/dragHelpers";

export const ProcessGraph = forwardRef<Graph, { capabilities: Capabilities }>(function ProcessGraph(
    { capabilities },
    forwardedRef,
): JSX.Element {
    const scenario = useSelector(getScenario);
    const processCounts = useSelector(getProcessCounts);
    const layout = useSelector(getLayout);

    const graph = useRef<Graph>();
    useImperativeHandle(forwardedRef, () => graph.current);

    const [{ isDraggingOver }, connectDropTarget] = useDrop({
        accept: DndTypes.ELEMENT,
        drop: (item: NodeType, monitor) => {
            const clientOffset = monitor.getClientOffset();
            const relOffset = graph.current.processGraphPaper.clientToLocalPoint(clientOffset);
            // to make node horizontally aligned
            const nodeInputRelOffset = relOffset.offset(RECT_WIDTH * -0.8, RECT_HEIGHT * -0.5);
            graph.current.addNode(monitor.getItem(), mapValues(nodeInputRelOffset, Math.round));
            setLinksHovered(graph.current.graph);
        },
        hover: (item: NodeType, monitor) => {
            const node = item;
            const canInjectNode = NodeUtils.hasInputs(node) || NodeUtils.hasOutputs(node);

            if (canInjectNode) {
                const clientOffset = monitor.getClientOffset();
                const point = graph.current.processGraphPaper.clientToLocalPoint(clientOffset);
                const rect = new g.Rect({ ...point, width: 0, height: 0 })
                    .inflate(RECT_WIDTH / 2, RECT_HEIGHT / 2)
                    .offset(RECT_WIDTH / 2, RECT_HEIGHT / 2)
                    .offset(RECT_WIDTH * -0.8, RECT_HEIGHT * -0.5);
                setLinksHovered(graph.current.graph, rect);
            } else {
                setLinksHovered(graph.current.graph);
            }
        },
        collect: (monitor) => ({
            isDraggingOver: monitor.isOver(),
        }),
    });

    const dispatch = useDispatch();
    const actions = useMemo(
        () =>
            bindActionCreators(
                {
                    nodesConnected,
                    nodesDisconnected,
                    layoutChanged,
                    injectNode,
                    nodeAdded,
                    resetSelection,
                    toggleSelection,
                },
                dispatch,
            ),
        [dispatch],
    );

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
            {...actions}
        />
    );
});
