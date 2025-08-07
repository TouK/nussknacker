import type { dia } from "jointjs";
import { g } from "jointjs";
import { mapValues } from "lodash";
import React, { forwardRef, useCallback, useImperativeHandle, useMemo, useRef } from "react";
import { useDrop } from "react-dnd";
import { bindActionCreators } from "redux";

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
import HttpService from "../../http/HttpService";
import { createUniqueName } from "../../reducers/graph/utils";
import { fetchScenarios, getScenariosNames } from "../../reducers/scenarios";
import { getLayout, getProcessCounts, getScenario } from "../../reducers/selectors/graph";
import type { Capabilities } from "../../reducers/selectors/other";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";
import type { NodeType } from "../../types";
import { DndTypes } from "../DndTypes";
import type { Scenario } from "../Process/types";
import { jsonToFileInFormData } from "./createFragment";
import { RECT_HEIGHT, RECT_WIDTH } from "./EspNode/esp";
import { STICKY_NOTE_CONSTRAINTS } from "./EspNode/stickyNote";
import type { Graph } from "./Graph";
import GraphWrapped from "./GraphWrapped";
import NodeUtils from "./NodeUtils";
import { setDraggedOver } from "./utils/dragHelpers";
import { StickyNoteType } from "./utils/stickyNotesUtils";

export type ElementDropResult = {
    item: NodeType;
    clientOffset: g.PlainPoint;
    paper: dia.Paper;
};

export const ProcessGraph = forwardRef<
    Graph,
    {
        capabilities: Capabilities;
    }
>(function ProcessGraph({ capabilities }, forwardedRef): JSX.Element {
    const scenario = useAppSelector(getScenario);
    const processCounts = useAppSelector(getProcessCounts);
    const layout = useAppSelector(getLayout);

    const graph = useRef<Graph>();
    useImperativeHandle(forwardedRef, () => graph.current);

    const [{ isDraggingOver }, connectDropTarget] = useDrop({
        accept: DndTypes.ELEMENT,
        drop: (item: NodeType, monitor): ElementDropResult => {
            const clientOffset = monitor.getClientOffset();
            const paper = graph.current.processGraphPaper;
            const relOffset = paper.clientToLocalPoint(clientOffset);
            // to make node horizontally aligned
            const nodeInputRelOffset =
                item.type === StickyNoteType
                    ? relOffset.offset(STICKY_NOTE_CONSTRAINTS.DEFAULT_WIDTH * -0.5, STICKY_NOTE_CONSTRAINTS.DEFAULT_HEIGHT * -0.5)
                    : relOffset.offset(RECT_WIDTH * -0.8, RECT_HEIGHT * -0.5);
            graph.current.addNode(item, mapValues(nodeInputRelOffset, Math.round));
            setDraggedOver(graph.current.graph);
            return {
                paper,
                item,
                clientOffset,
            };
        },
        hover: (item: NodeType, monitor) => {
            const node = item;
            const canInjectNode = NodeUtils.hasInputs(node) || NodeUtils.hasOutputs(node);

            if (canInjectNode) {
                const clientOffset = monitor.getClientOffset();
                const point = graph.current.processGraphPaper.clientToLocalPoint(clientOffset);
                const rect = new g.Rect({
                    ...point,
                    width: 0,
                    height: 0,
                })
                    .inflate(RECT_WIDTH / 2, RECT_HEIGHT / 2)
                    .offset(RECT_WIDTH / 2, RECT_HEIGHT / 2)
                    .offset(RECT_WIDTH * -0.8, RECT_HEIGHT * -0.5);
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
    const actions = useMemo(
        () =>
            bindActionCreators(
                {
                    nodesConnected,
                    nodesDisconnected,
                    layoutChanged,
                    injectNode,
                    editNode,
                    replaceNode,
                    nodeAdded,
                    nodesWithEdgesAdded,
                    stickyNoteUpdated,
                    stickyNoteSetErrors,
                    resetSelection,
                    toggleSelection,
                },
                dispatch,
            ),
        [dispatch],
    );

    const createFragment = useCallback((callback) => dispatch(createFragmentAction(scenario, callback)), [dispatch, scenario]);

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
            createFragment={createFragment}
        />
    );
});

const FRAGMENT_TEMPLATE = {
    metaData: {
        id: "test-frament",
        additionalFields: {
            description: null,
            properties: {
                docsUrl: "",
                componentGroup: "fragments",
                icon: "/assets/components/FragmentInput.svg",
            },
            metaDataType: "FragmentSpecificData",
            showDescription: false,
        },
    },
    nodes: [
        {
            id: "input",
            parameters: [],
            additionalFields: {
                description: null,
                layoutData: {
                    x: 0,
                    y: 0,
                },
            },
            type: "FragmentInputDefinition",
        },
        {
            id: "output",
            outputName: "output",
            fields: [],
            additionalFields: {
                description: null,
                layoutData: {
                    x: 0,
                    y: 180,
                },
            },
            type: "FragmentOutputDefinition",
        },
    ],
    additionalBranches: [],
    stickyNotes: [],
};

function createFragmentAction(scenario: Scenario, callback: (node: NodeType | null) => void): ThunkAction {
    return async (dispatch, getState) => {
        await dispatch(fetchScenarios());
        const scenarios = getScenariosNames(getState());
        const uniqueName = createUniqueName("empty fragment", scenarios);

        const { processingType, engineSetupName, processCategory, processingMode } = scenario;
        await HttpService.createProcess({
            name: uniqueName,
            isFragment: true,
            category: processCategory,
            processingMode: processingMode,
            engineSetupName: engineSetupName,
        });
        const { data } = await HttpService.importProcess(uniqueName, jsonToFileInFormData(FRAGMENT_TEMPLATE));
        await HttpService.saveProcess(uniqueName, data.scenarioGraph, "import placeholder data", []);
        const { componentGroups } = await dispatch(fetchProcessDefinition(processingType, false));
        const component = componentGroups
            .find((g) => g.name.toLowerCase() === "fragments")
            ?.components.find((c) => c.componentId === `fragment-${uniqueName}`);
        callback(
            component
                ? {
                      ...component.node,
                      id: component.label,
                  }
                : null,
        );
    };
}
