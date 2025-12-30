/* eslint-disable i18next/no-literal-string */
import { produce } from "immer";
import type { Dictionary } from "lodash";
import { concat, defaultsDeep, isEqual, partition, sortBy } from "lodash";
import type { StateWithHistory } from "redux-undo";
import undoable, { ActionTypes as UndoActionTypes, combineFilters, excludeAction } from "redux-undo";

import type { Action, ActionOfType, Reducer } from "../../actions/reduxTypes";
import ProcessUtils from "../../common/ProcessUtils";
import NodeUtils from "../../components/graph/NodeUtils";
import { addStickyNotesToNodes, StickyNoteType } from "../../components/graph/utils/stickyNotesUtils";
import type { Scenario } from "../../components/Process/types";
import type { Edge } from "../../types/edge";
import type { NodeType } from "../../types/node";
import type { ProcessDefinitionData } from "../../types/scenarioGraph";
import type { ValidationResult } from "../../types/validation";
import { fromMeta, nodes } from "../layoutUtils";
import { mergeReducers } from "../mergeReducers";
import { batchGroupBy } from "./batchGroupBy";
import { correctFetchedDetails } from "./correctFetchedDetails";
import { appendHistorySquashLogic } from "./historySquash";
import type { NestedKeyOf } from "./lodashWrappers";
import { omit, pick } from "./lodashWrappers";
import { selectionState } from "./selectionState";
import { testCaseReducer } from "./testCases";
import { testingReducer } from "./testing";
import type { GraphState } from "./types";
import { VisibleDataType } from "./types";
import {
    addNodesWithLayout,
    adjustBranchParametersAfterDisconnect,
    createEdge,
    enrichNodeWithProcessDependentData,
    getUpdateStateAfterNodeIdChange,
    updateAfterNodeDelete,
} from "./utils";

//TODO: We should change namespace from graphReducer to currentlyDisplayedProcess

const emptyGraphState: GraphState = {
    scenarioLoading: false,
    scenario: {
        scenarioGraph: {
            nodes: [],
            edges: [],
            properties: null,
            stickyNotes: [],
            testCases: {},
        },
    } as Scenario,
    layout: [],
    testCapabilities: null,
    testFormParameters: null,
    selectionState: [],
    processCounts: {},
    testing: {},
};

export function updateValidationResult(
    state: GraphState,
    action: ActionOfType<"EDIT_NODE" | "EDIT_PROPERTIES" | "VALIDATION_RESULT">,
): ValidationResult {
    if (!action.validationResult) {
        return state.scenario.validationResult;
    }
    return {
        ...action.validationResult,
        // nodeResults is sometimes empty although it shouldn't e.g. when SaveNotAllowed errors happen
        nodeResults: {
            ...ProcessUtils.getValidationResult(state.scenario).nodeResults,
            ...action.validationResult.nodeResults,
        },
    };
}

// fix added/pasted edges port names after fixing duplicated node names
function adjustEdges(
    addedNodes: NodeType[],
    addedEdges: Edge[],
    currentNodes: NodeType[],
    currentEdges: Edge[],
    processDefinitionData: ProcessDefinitionData,
    idMapping: Dictionary<string>,
): Edge[] {
    if (addedEdges.length < 1) return currentEdges;
    const nodes = [...currentNodes, ...addedNodes];

    const getId = (rawId: string) => {
        // one "to be connected" node
        if (addedNodes.length === 1 && addedEdges.length === 1) {
            return rawId || addedNodes[0].id;
        }
        return idMapping[rawId] || rawId;
    };

    return addedEdges.reduce((adjustedEdges, { edgeType, from, to }) => {
        const fromNode = nodes.find(({ id }) => id === getId(from));
        const toNode = nodes.find(({ id }) => id === getId(to));
        const currentNodeEdges = NodeUtils.getOutputEdges(fromNode.id, adjustedEdges);
        const newEdge = createEdge(fromNode, toNode, edgeType, currentNodeEdges, processDefinitionData);
        return adjustedEdges.concat(newEdge);
    }, currentEdges);
}

// scenario name is used as fake property in form so we should init it on load
const appendScenarioNameToProperties = produce((scenario: Scenario) => {
    scenario.scenarioGraph = {
        ...scenario.scenarioGraph,
        properties: {
            ...scenario.scenarioGraph?.properties,
            name: scenario.name,
        },
    };
});

const updateStateAfterEdit = (state: GraphState, action: ActionOfType<"EDIT_NODE" | "EDIT_PROPERTIES">) => {
    return produce(state, (draft) => {
        draft.scenario.scenarioGraph = action.scenarioGraphAfterChange;
        draft.scenario.validationResult = updateValidationResult(draft, action);
    });
};

const graphReducer: Reducer<GraphState> = (state = emptyGraphState, action): GraphState => {
    const currentNodes = state.scenario.scenarioGraph.nodes;
    const currentEdges = state.scenario.scenarioGraph.edges;
    switch (action.type) {
        case "PROCESS_FETCH":
        case "PROCESS_LOADING": {
            return {
                ...state,
                scenarioLoading: true,
            };
        }
        case "TEST_RESULTS_LOADING": {
            return {
                ...state,
                testing: {
                    ...state.testing,
                    [state.scenario.name]: {
                        ...state.testing[state.scenario.name],
                        testResultsLoading: true,
                    },
                },
            };
        }
        case "UPDATE_IMPORTED_PROCESS": {
            const oldNodeIds = sortBy(currentNodes.map((n) => n.id));
            const adjustedScenario = appendScenarioNameToProperties(addStickyNotesToNodes(action.scenario));
            const newNodeids = sortBy(adjustedScenario.scenarioGraph.nodes.map((n) => n.id));
            const newLayout = isEqual(oldNodeIds, newNodeids) ? state.layout : null;

            return {
                ...state,
                scenarioLoading: false,
                layout: newLayout,
                scenario: {
                    ...state.scenario,
                    ...adjustedScenario,
                },
            };
        }
        case "UPDATE_TEST_CAPABILITIES": {
            return {
                ...state,
                testCapabilities: action.capabilities,
                testFormParameters: action.capabilities?.testWithParameters?.sourceParameters,
            };
        }
        case "DISPLAY_PROCESS": {
            const adjustedScenario = appendScenarioNameToProperties(addStickyNotesToNodes(action.scenario));
            return {
                ...state,
                scenario: adjustedScenario,
                scenarioLoading: false,
                layout: fromMeta(adjustedScenario.scenarioGraph),
            };
        }
        case "CORRECT_INVALID_SCENARIO": {
            const scenario = correctFetchedDetails(state.scenario, action.processDefinitionData);
            return {
                ...state,
                scenario,
            };
        }
        case "ARCHIVED": {
            return {
                ...state,
                scenario: {
                    ...state.scenario,
                    isArchived: true,
                },
            };
        }
        case "TEST_RESULTS_FAILED": {
            return {
                ...state,
                testing: {
                    ...state.testing,
                    [state.scenario.name]: {
                        ...state.testing[state.scenario.name],
                        testResultsLoading: false,
                    },
                },
            };
        }
        case "LOADING_FAILED": {
            return {
                ...state,
                scenarioLoading: false,
            };
        }
        case "CLEAR_PROCESS": {
            return { ...emptyGraphState, testing: state.testing };
        }
        case "EDIT_NODE": {
            const updateStateAfterNodeIdChange = getUpdateStateAfterNodeIdChange(action.before.id, action.after.id);
            return updateStateAfterNodeIdChange(updateStateAfterEdit(state, action));
        }
        case "EDIT_PROPERTIES": {
            return updateStateAfterEdit(state, action);
        }
        case "EDIT_LABELS": {
            return {
                ...state,
                scenario: {
                    ...state.scenario,
                    labels: action.labels,
                },
            };
        }
        case "DELETE_NODES": {
            return action.ids.reduce((state, idToDelete) => {
                return updateAfterNodeDelete(state, idToDelete);
            }, state);
        }
        case "NODES_CONNECTED": {
            const currentEdges = NodeUtils.edgesFromScenarioGraph(state.scenario.scenarioGraph);
            const newEdge = NodeUtils.getEdgeForConnection({
                fromNode: action.fromNode,
                toNode: action.toNode,
                edgeType: action.edgeType,
                processDefinition: action.processDefinitionData,
                scenarioGraph: state.scenario.scenarioGraph,
            });

            const newEdges = currentEdges.includes(newEdge)
                ? currentEdges.map((edge) =>
                      edge === newEdge
                          ? {
                                ...newEdge,
                                to: action.toNode.id,
                            }
                          : edge,
                  )
                : concat(currentEdges, newEdge);

            return {
                ...state,
                scenario: {
                    ...state.scenario,
                    scenarioGraph: {
                        ...state.scenario.scenarioGraph,
                        nodes: currentNodes.map((n) =>
                            action.toNode.id !== n.id ? n : enrichNodeWithProcessDependentData(n, action.processDefinitionData, newEdges),
                        ),
                        edges: newEdges,
                    },
                },
            };
        }
        case "NODES_DISCONNECTED": {
            const nodesToSet = adjustBranchParametersAfterDisconnect(currentNodes, [action]);
            return {
                ...state,
                scenario: {
                    ...state.scenario,
                    scenarioGraph: {
                        ...state.scenario.scenarioGraph,
                        edges: currentEdges
                            .map((e) => (e.from === action.from && e.to === action.to ? { ...e, to: "" } : e))
                            .filter(Boolean),
                        nodes: nodesToSet,
                    },
                },
            };
        }
        case "NODE_ADDED": {
            return addNodesWithLayout(state, {
                nodes: action.nodes,
                layout: action.layout,
            });
        }
        case "STICKY_NOTE_UPDATED": {
            const { nodes = [], ...scenarioGraph } = state.scenario.scenarioGraph;
            const updatedNodes = nodes.map((node) =>
                node.id === action.id
                    ? {
                          ...node,
                          dimensions: action.dimensions,
                          content: action.content ? action.content : node.content,
                      }
                    : node,
            );
            return {
                ...state,
                scenario: {
                    ...state.scenario,
                    scenarioGraph: {
                        ...scenarioGraph,
                        nodes: updatedNodes,
                    },
                },
            };
        }
        case "STICKY_NOTE_SET_ERRORS": {
            const { nodes = [], ...scenarioGraph } = state.scenario.scenarioGraph;
            const [stickyNotes, graphNodes] = partition(nodes, (node) => node.type === StickyNoteType);
            const stickyNotesUpdated = stickyNotes.map((stickyNote) => {
                return action.stickyNoteErrors[stickyNote.id]
                    ? {
                          ...stickyNote,
                          errors: action.stickyNoteErrors[stickyNote.id],
                      }
                    : stickyNote;
            });
            return {
                ...state,
                scenario: {
                    ...state.scenario,
                    scenarioGraph: {
                        ...scenarioGraph,
                        nodes: [...graphNodes, ...stickyNotesUpdated],
                    },
                },
            };
        }
        case "NODES_WITH_EDGES_ADDED": {
            const { nodes, layout, idMapping, processDefinitionData, edges } = action;
            return addNodesWithLayout(state, {
                nodes,
                layout,
                edges: adjustEdges(nodes, edges, currentNodes, currentEdges, processDefinitionData, idMapping),
            });
        }
        case "VALIDATION_RESULT": {
            return {
                ...state,
                scenario: {
                    ...state.scenario,
                    validationResult: updateValidationResult(state, action),
                },
            };
        }
        //TODO: handle it differently?
        case "LAYOUT_CHANGED": {
            return {
                ...state,
                layout: action.layout,
            };
        }
        case "DISPLAY_PROCESS_COUNTS": {
            return {
                ...state,
                visibleDataType: VisibleDataType.counts,
                processCounts: action.processCounts,
                processCountsRefresh: action.refresh,
                testing: {
                    ...state.testing,
                    [state.scenario.name]: {
                        ...state.testing[state.scenario.name],
                        testResults: null,
                    },
                },
            };
        }
        case "FETCH_LIVE_DATA": {
            return {
                ...state,
                visibleDataType: VisibleDataType.live,
            };
        }
        case "DISPLAY_LIVE_DATA": {
            return {
                ...state,
                visibleDataType: VisibleDataType.live,
                processCounts: action.results?.counts || {},
                processCountsRefresh: null,
                testing: {
                    ...state.testing,
                    [state.scenario.name]: {
                        ...state.testing[state.scenario.name],
                        testResults: action.results?.results || null,
                    },
                },
            };
        }
        case "DISPLAY_TEST_RESULTS_DETAILS": {
            return {
                ...state,
                visibleDataType: VisibleDataType.test,
                scenarioLoading: false,
            };
        }
        case "HIDE_RUN_PROCESS_DETAILS": {
            return {
                ...state,
                processCounts: null,
                processCountsRefresh: null,
            };
        }
        default:
            return state;
    }
};

const reducer = mergeReducers(graphReducer, {
    scenario: {
        scenarioGraph: {
            nodes,
            testCases: testCaseReducer,
        },
    },
    testing: testingReducer,
    selectionState,
});

export type GraphStateWithHistory = StateWithHistory<GraphState> & {
    snapshots: number[];
};

const pickKeys: NestedKeyOf<GraphState>[] = ["scenario", "layout"];
const omitKeys: NestedKeyOf<GraphState>[] = ["scenario.validationResult", "scenario.history"];

export const getUndoableState = (state: GraphState) => omit(pick(state, pickKeys), omitKeys.concat(["scenario.validationResult"]));
export const getNonUndoableState = (state: GraphState) => defaultsDeep(omit(state, pickKeys), pick(state, omitKeys));

const undoableReducer = undoable<GraphState, Action>(reducer, {
    ignoreInitialState: true,
    clearHistoryType: [UndoActionTypes.CLEAR_HISTORY, "PROCESS_FETCH"],
    groupBy: batchGroupBy.init,
    filter: combineFilters((action, nextState, prevState) => {
        return !isEqual(getUndoableState(nextState), getUndoableState(prevState._latestUnfiltered));
    }, excludeAction(["LIVE_DATA_START", "LIVE_DATA_STARTED", "LIVE_DATA_STOP", "DISPLAY_LIVE_DATA", "FETCH_LIVE_DATA", "VALIDATION_RESULT", "STICKY_NOTE_SET_ERRORS", "UPDATE_IMPORTED_PROCESS", "PROCESS_STATE_LOADED", "UPDATE_TEST_CAPABILITIES", "UPDATE_BACKEND_NOTIFICATIONS", "PROCESS_DEFINITION_DATA", "PROCESS_TOOLBARS_CONFIGURATION_LOADED", "CORRECT_INVALID_SCENARIO", "GET_SCENARIO_ACTIVITIES", "LOGGED_USER", "REGISTER_TOOLBARS", "UI_SETTINGS", "MARK_BACKEND_NOTIFICATION_READ", "GET_SCENARIOS", "SCENARIOS_FETCHED", "OPEN_NODE_SELECTOR", "CLOSE_NODE_SELECTOR"])),
}) as Reducer<StateWithHistory<GraphState>>;

// apply only undoable changes for undo actions
const fixUndoableHistory: Reducer<StateWithHistory<GraphState>> = (state, action) => {
    const nextState = undoableReducer(state, action);

    if (Object.values(UndoActionTypes).includes(action.type)) {
        const present = defaultsDeep(getUndoableState(nextState.present), getNonUndoableState(state?.present));
        return { ...nextState, present };
    }

    return nextState;
};

export const reducerWithUndo: Reducer<GraphStateWithHistory> = (state, action) => {
    const history = fixUndoableHistory(state, action);
    return appendHistorySquashLogic({ ...history, snapshots: state?.snapshots }, action);
};
